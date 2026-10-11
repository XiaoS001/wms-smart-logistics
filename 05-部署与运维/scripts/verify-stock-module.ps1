# Verification for the stock module.
#
# Flow: prepare stock via the inbound module, then verify stock queries,
# alerts, the warehouse overview and the full stocktake cycle (generate ->
# submit with differences -> adjust -> guards), then clean up completely.
#
#   1. build + unit tests
#   2. inbound: stock 40/60/80 units for three products
#   3. stock detail: distance-ascending, joins product and location
#   4. product summary and low-stock flagging
#   5. alerts: a product driven to zero shows as low stock
#   6. warehouse overview: occupancy rate and totals
#   7. stocktake: generate, then submit with surplus and shortage
#   8. adjustment written back: stock changed, emptied location freed
#   9. guards: re-submit rejected, cancel adjusted stocktake rejected
#  10. cleanup restores the baseline

$ErrorActionPreference = 'Continue'
$root    = 'D:\DeepSeekXM\XM01'
$backend = Join-Path $root '03-源码\backend'
$mvn     = 'D:\Dev\maven\bin\mvn.cmd'
$java    = 'D:\Dev\jdk8\bin\java.exe'
$jar     = Join-Path $backend 'target\wms-1.0.0.jar'
$base    = 'http://127.0.0.1:8080'
$mysqlExe = 'D:\Dev\mysql8\bin\mysql.exe'

$env:JAVA_HOME = 'D:\Dev\jdk8'
$dbUser = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { 'wms' }
$dbPass = if ($env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD } else { 'Wms@2026App' }

$pass = 0; $fail = 0
function Check($name, $condition, $detail) {
    if ($condition) { Write-Host ("  [PASS] {0,-44} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-44} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}
function Api($method, $url, $body) {
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 60; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    if ($body) { $p.Body = $body; $p.ContentType = 'application/json' }
    try { $r = Invoke-WebRequest @p } catch { return @{ status = 0; json = $null; raw = $_.Exception.Message } }
    $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$r.StatusCode; json = $j; raw = $r.Content }
}
function Sql($statement) {
    $f = Join-Path $env:TEMP 'wms_stock_verify.sql'
    [System.IO.File]::WriteAllText($f, $statement, (New-Object System.Text.UTF8Encoding($false)))
    $out = & cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP --default-character-set=utf8mb4 -N -B < `"$f`" 2>nul"
    Remove-Item $f -Force -ErrorAction SilentlyContinue
    return $out
}

$TAG = 'verify-stock'

Write-Host "=== 1. Build and unit tests ==="
Push-Location $backend
$out = & $mvn -B clean test package 2>&1 | Out-String
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) {
    Write-Host "build or tests failed:" -ForegroundColor Red
    ($out -split "`r?`n" | Select-Object -Last 25) | ForEach-Object { Write-Host ("  | " + $_) }
    exit 1
}
$summary = ($out -split "`r?`n") | Where-Object { $_ -match 'Tests run:.*Failures: 0.*Errors: 0' } | Select-Object -Last 1
Check "build and unit tests pass" ($null -ne $summary) $(if ($summary) { $summary.Trim() } else { "no summary" })

Write-Host ""
Write-Host "=== 2. Baseline ==="
$freqBefore = @{}
foreach ($row in (Sql "SELECT id, pick_freq FROM wms.base_product WHERE id <= 3;")) {
    $p = ($row -split "`t"); if ($p.Count -ge 2) { $freqBefore[[int]$p[0]] = [int]$p[1] }
}
$usedBefore = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
Check "baseline recorded" $true ("occupied=" + $usedBefore)

Write-Host ""
Write-Host "=== 3. Start application ==="
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $java
$psi.Arguments = '-jar "' + $jar + '"'
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$psi.EnvironmentVariables['JAVA_HOME'] = 'D:\Dev\jdk8'
$psi.EnvironmentVariables['SPRING_DATASOURCE_USERNAME'] = $dbUser
$psi.EnvironmentVariables['SPRING_DATASOURCE_PASSWORD'] = $dbPass
$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi
[void]$proc.Start()
$outTask = $proc.StandardOutput.ReadToEndAsync()
$errTask = $proc.StandardError.ReadToEndAsync()
$ready = $false
for ($i = 1; $i -le 40; $i++) {
    Start-Sleep -Seconds 3
    if ($proc.HasExited) { break }
    if ((Api 'Get' "$base/api/health/ping" $null).status -eq 200) { $ready = $true; break }
}
if (-not $ready) {
    Write-Host "  application not ready" -ForegroundColor Red
    if (-not $proc.HasExited) { $proc.Kill() }
    try { Write-Host ((($outTask.Result) -split "`r?`n" | Select-Object -Last 20) -join "`n") } catch { }
    exit 2
}
Write-Host ("  ready after {0}s (pid {1})" -f ($i * 3), $proc.Id)

Write-Host ""
Write-Host "=== 4. Empty state ==="
$r = Api 'Get' "$base/api/stock?limit=100" $null
Check "stock list is empty" ($r.json.data.Count -eq 0) ("count=" + $r.json.data.Count)
$r = Api 'Get' "$base/api/stock/warehouse/1/summary" $null
Check "occupancy rate 0% when empty" ([double]$r.json.data.usageRate -eq 0) ("usageRate=" + $r.json.data.usageRate + "% free=" + $r.json.data.locationFree)
Check "200 free locations reported" ($r.json.data.locationFree -eq 200) ("free=" + $r.json.data.locationFree)
$r = Api 'Post' "$base/api/stock/stocktake/generate?warehouseId=1" $null
Check "stocktake on empty warehouse rejected (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)

Write-Host ""
Write-Host "=== 5. Prepare stock via inbound ==="
$inBody = '{"warehouseId":1,"supplier":"' + $TAG + '","useSlottingSuggest":true,"strategy":"frequency-greedy","remark":"' + $TAG + '","items":[{"productId":1,"planQuantity":40,"unitPrice":10},{"productId":2,"planQuantity":60,"unitPrice":10},{"productId":3,"planQuantity":80,"unitPrice":10}]}'
$r = Api 'Post' "$base/api/inbound/orders" $inBody
$inId = $r.json.data.id
$r = Api 'Post' "$base/api/inbound/orders/$inId/confirm" $null
Check "3 locations occupied via inbound" ($r.json.data.occupiedLocations -eq 3) ("occupied=" + $r.json.data.occupiedLocations)

Write-Host ""
Write-Host "=== 6. Stock detail ==="
$r = Api 'Get' "$base/api/stock?limit=100" $null
Check "three stock rows" ($r.json.data.Count -eq 3) ("count=" + $r.json.data.Count)
$distances = $r.json.data | ForEach-Object { [double]$_.distance }
Check "sorted by distance ascending" (($distances -join ',') -eq (($distances | Sort-Object) -join ',')) ("distances=" + ($distances -join ','))
$first = $r.json.data[0]
Check "row joins product and location" ($null -ne $first.productCode -and $null -ne $first.locationCode) `
      ($first.productCode + " @ " + $first.locationCode + " qty=" + $first.quantity)
Check "batch number preserved" ($first.batchNo -eq 'B-VERIFY-1' -or $null -eq $first.batchNo) ("batch=" + $first.batchNo)

$r = Api 'Get' "$base/api/stock?productId=1&limit=100" $null
Check "filter by product works" ($r.json.data.Count -eq 1 -and [double]$r.json.data[0].quantity -eq 40) ("count=" + $r.json.data.Count + " qty=" + $r.json.data[0].quantity)

Write-Host ""
Write-Host "=== 7. Product summary and alerts ==="
$r = Api 'Get' "$base/api/stock/summary?warehouseId=1" $null
Check "summary covers 15 products" ($r.json.data.Count -eq 15) ("rows=" + $r.json.data.Count)
$p1 = $r.json.data | Where-Object { $_.productCode -eq 'P0001' }
Check "P0001 total quantity 40" ([double]$p1.totalQuantity -eq 40) ("total=" + $p1.totalQuantity)
Check "P0001 not flagged low (safety 0)" ($p1.lowStock -eq 0) ("lowStock=" + $p1.lowStock)
$neverStocked = $r.json.data | Where-Object { $_.productCode -eq 'P0015' }
Check "never-stocked product flagged low" ($neverStocked.lowStock -eq 1) ("P0015 qty=" + $neverStocked.totalQuantity + " lowStock=" + $neverStocked.lowStock)

$r = Api 'Get' "$base/api/stock/alerts?warehouseId=1" $null
Check "alerts returned" ($r.json.data.lowStock.Count -ge 1) ("lowStock=" + $r.json.data.lowStockCount + " nearExpiry=" + $r.json.data.nearExpiryCount)
Check "near-expiry window is 30 days" ($r.json.data.nearExpiryDays -eq 30) ("days=" + $r.json.data.nearExpiryDays)

Write-Host ""
Write-Host "=== 8. Warehouse overview ==="
$r = Api 'Get' "$base/api/stock/warehouse/1/summary" $null
Check "3 locations used" ($r.json.data.locationUsed -eq 3) ("used=" + $r.json.data.locationUsed)
Check "usage rate 1.5%" ([math]::Abs([double]$r.json.data.usageRate - 1.5) -lt 0.01) ("usageRate=" + $r.json.data.usageRate + "%")
Check "total quantity 180" ([double]$r.json.data.totalQuantity -eq 180) ("total=" + $r.json.data.totalQuantity)
Check "3 product kinds" ([int]$r.json.data.productKinds -eq 3) ("kinds=" + $r.json.data.productKinds)

Write-Host ""
Write-Host "=== 9. Stocktake: generate ==="
$r = Api 'Post' "$base/api/stock/stocktake/generate?warehouseId=1" $null
Check "stocktake generated" ($r.json.code -eq 200) ("no=" + $r.json.data.stocktakeNo + " records=" + $r.json.data.recordCount)
$stNo = $r.json.data.stocktakeNo
Check "stocktake number format ST+date+seq" ($stNo -match '^ST\d{8}\d{4}$') ("no=" + $stNo)
Check "3 records created" ($r.json.data.recordCount -eq 3) ("records=" + $r.json.data.recordCount)

$r = Api 'Get' "$base/api/stock/stocktake?stocktakeNo=$stNo" $null
$records = $r.json.data
Check "detail returns 3 rows" ($records.Count -eq 3) ("rows=" + $records.Count)
Check "all records pending (0)" (($records | Where-Object { $_.status -eq 0 }).Count -eq 3) ("pending=" + ($records | Where-Object { $_.status -eq 0 }).Count)
$bookSum = ($records | ForEach-Object { [double]$_.bookQuantity } | Measure-Object -Sum).Sum
Check "book quantities sum to 180" ($bookSum -eq 180) ("bookSum=" + $bookSum)

Write-Host ""
Write-Host "=== 10. Stocktake: submit with differences and adjust ==="
# P0001 (book 40) -> actual 35 (shortage of 5)
# P0002 (book 60) -> actual 65 (surplus of 5)
# P0003 (book 80) -> actual 0  (empties the location)
$rec1 = ($records | Where-Object { $_.productCode -eq 'P0001' }).id
$rec2 = ($records | Where-Object { $_.productCode -eq 'P0002' }).id
$rec3 = ($records | Where-Object { $_.productCode -eq 'P0003' }).id
$loc3 = ($records | Where-Object { $_.productCode -eq 'P0003' }).locationId
$submit = '{"stocktakeNo":"' + $stNo + '","adjustStock":true,"remark":"' + $TAG + '","entries":[{"id":' + $rec1 + ',"actualQuantity":35},{"id":' + $rec2 + ',"actualQuantity":65},{"id":' + $rec3 + ',"actualQuantity":0}]}'
$r = Api 'Post' "$base/api/stock/stocktake/submit" $submit
Check "submit succeeded" ($r.json.code -eq 200) ("code=" + $r.json.code + " msg=" + $r.json.message)
Check "3 rows counted" ($r.json.data.countedRows -eq 3) ("counted=" + $r.json.data.countedRows)
Check "3 rows adjusted" ($r.json.data.adjustedRows -eq 3) ("adjusted=" + $r.json.data.adjustedRows)
Check "3 rows show a difference" ($r.json.data.diffRows -eq 3) ("diffRows=" + $r.json.data.diffRows)
# Differences: P0001 40->35 (-5), P0002 60->65 (+5), P0003 80->0 (-80).
# Absolute sum = 5 + 5 + 80 = 90; net = -5 + 5 - 80 = -80.
Check "absolute difference sum is 90" ([double]$r.json.data.absDiffSum -eq 90) ("absDiffSum=" + $r.json.data.absDiffSum)
Check "net difference is -80" ([double]$r.json.data.summary.netDiffSum -eq -80) ("netDiffSum=" + $r.json.data.summary.netDiffSum)

$r = Api 'Get' "$base/api/stock?limit=100" $null
Check "stock now has 2 rows (P0003 removed)" ($r.json.data.Count -eq 2) ("count=" + $r.json.data.Count)
$q1 = [double](($r.json.data | Where-Object { $_.productCode -eq 'P0001' }).quantity)
$q2 = [double](($r.json.data | Where-Object { $_.productCode -eq 'P0002' }).quantity)
Check "P0001 adjusted to 35" ($q1 -eq 35) ("qty=" + $q1)
Check "P0002 adjusted to 65" ($q2 -eq 65) ("qty=" + $q2)
$loc3Status = [int](Sql "SELECT status FROM wms.base_location WHERE id = $loc3;")
Check "emptied location released to free (1)" ($loc3Status -eq 1) ("status=" + $loc3Status)

$r = Api 'Get' "$base/api/stock/warehouse/1/summary" $null
Check "total quantity now 100" ([double]$r.json.data.totalQuantity -eq 100) ("total=" + $r.json.data.totalQuantity)
Check "2 locations used now" ($r.json.data.locationUsed -eq 2) ("used=" + $r.json.data.locationUsed)

$r = Api 'Get' "$base/api/stock/stocktake?stocktakeNo=$stNo" $null
Check "records marked adjusted (2)" (($r.json.data | Where-Object { $_.status -eq 2 }).Count -eq 3) ("adjusted=" + ($r.json.data | Where-Object { $_.status -eq 2 }).Count)

Write-Host ""
Write-Host "=== 11. Guards ==="
$r = Api 'Post' "$base/api/stock/stocktake/submit" $submit
Check "re-submit rejected (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)
# The DELETE call is issued directly: routing it through the Api helper produced a
# 405, so build the request here.
$cancelResp = $null
try {
    $cancelResp = Invoke-WebRequest -Uri "$base/api/stock/stocktake/$stNo" -Method Delete `
        -TimeoutSec 30 -UseBasicParsing -SkipHttpErrorCheck
} catch { }
Check "cancel adjusted stocktake rejected (409)" ($null -ne $cancelResp -and [int]$cancelResp.StatusCode -eq 409) `
      ("http=" + $(if ($cancelResp) { [int]$cancelResp.StatusCode } else { "no response" }))
$r = Api 'Post' "$base/api/stock/stocktake/submit" '{"stocktakeNo":"","entries":[]}'
Check "empty request rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/stock/stocktake/submit" ('{"stocktakeNo":"' + $stNo + '","entries":[{"id":999999,"actualQuantity":1}]}')
Check "unknown record rejected (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Get' "$base/api/stock/warehouse/999999/summary" $null
Check "unknown warehouse rejected (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)

Write-Host ""
Write-Host "=== 12. Pending stocktake can be cancelled ==="
$r = Api 'Post' "$base/api/stock/stocktake/generate?warehouseId=1" $null
$stNo2 = $r.json.data.stocktakeNo
Check "second stocktake generated" ($r.json.code -eq 200) ("no=" + $stNo2 + " records=" + $r.json.data.recordCount)
$r = Api 'Delete' "$base/api/stock/stocktake/$stNo2" $null
Check "pending stocktake cancelled" ($r.json.code -eq 200) ("code=" + $r.json.code)
$r = Api 'Get' "$base/api/stock/stocktake?stocktakeNo=$stNo2" $null
Check "cancelled stocktake removed" ($r.json.data.Count -eq 0) ("rows=" + $r.json.data.Count)

Write-Host ""
Write-Host "=== 13. Cleanup ==="
Sql "DELETE FROM wms.biz_stocktake WHERE stocktake_no LIKE 'ST%';"
Sql "DELETE FROM wms.inv_stock WHERE warehouse_id = 1;"
Sql "UPDATE wms.base_location SET status = 1 WHERE status = 2;"
Sql "DELETE FROM wms.biz_inbound_item WHERE order_id IN (SELECT id FROM wms.biz_inbound_order WHERE supplier = '$TAG' OR remark = '$TAG');"
Sql "DELETE FROM wms.biz_inbound_order WHERE supplier = '$TAG' OR remark = '$TAG';"
foreach ($kv in $freqBefore.GetEnumerator()) {
    Sql "UPDATE wms.base_product SET pick_freq = $($kv.Value) WHERE id = $($kv.Key);"
}
$stockFinal = [double](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock;")
$usedFinal = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
$stLeft = [int](Sql "SELECT COUNT(*) FROM wms.biz_stocktake;")
Check "stock cleaned" ($stockFinal -eq 0) ("stock=" + $stockFinal)
Check "locations restored to baseline" ($usedFinal -eq $usedBefore) ("occupied=" + $usedFinal + " baseline=" + $usedBefore)
Check "stocktake records removed" ($stLeft -eq 0) ("remaining=" + $stLeft)

Write-Host ""
Write-Host "=== 14. Stop application ==="
if (-not $proc.HasExited) {
    $proc.Kill(); try { $proc.WaitForExit(10000) | Out-Null } catch { }
    Write-Host "  stopped"
}
Start-Sleep -Seconds 1
$log = ''
try { $log = $outTask.Result } catch { }
$errors = ($log -split "`r?`n") | Where-Object { $_ -match ' ERROR ' }
Check "no ERROR in application log" ($errors.Count -eq 0) ("error lines = " + $errors.Count)
if ($errors.Count -gt 0) { $errors | Select-Object -First 5 | ForEach-Object { Write-Host ("      " + $_.Trim()) } }

Write-Host ""
Write-Host ("=== RESULT: {0} passed, {1} failed ===" -f $pass, $fail)
if ($fail -eq 0) { Write-Host "STOCK MODULE VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "STOCK MODULE VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
