# Verification for the report module.
#
# Business data is created first (inbound -> stock -> outbound with route
# planning), then every reporting endpoint is checked against it, and finally
# the data is removed and the baseline restored.
#
#   1. build + unit tests + mapper XML check
#   2. empty-state dashboard and trends
#   3. create stock and ship part of it, so trends and statistics have data
#   4. dashboard reflects the created data
#   5. operation trend contains today's inbound and outbound
#   6. stock distribution by zone and by ABC class
#   7. slotting effect: greedy clearly beats random
#   8. picking effect: all strategies compared against the S-shape baseline
#   9. route algorithm statistics from the real outbound order
#  10. frequency contribution and the ABC 70% crossing point
#  11. validation: non-existent warehouse, and effects on an empty warehouse
#  12. cleanup

$ErrorActionPreference = 'Continue'
$root    = 'D:\DeepSeekXM\XM01'
$backend = Join-Path $root '03-源码\backend'
$mvn     = 'D:\Dev\maven\bin\mvn.cmd'
$java    = 'D:\Dev\jdk8\bin\java.exe'
$jar     = Join-Path $backend 'target\wms-1.0.0.jar'
$base    = 'http://127.0.0.1:8080'
$mysqlExe = 'D:\Dev\mysql8\bin\mysql.exe'
$diagDir = Join-Path $root 'build\diag'

$env:JAVA_HOME = 'D:\Dev\jdk8'
$dbUser = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { 'wms' }
$dbPass = if ($env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD } else { 'Wms@2026App' }

$pass = 0; $fail = 0
function Check($name, $condition, $detail) {
    if ($condition) { Write-Host ("  [PASS] {0,-46} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-46} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}
function Api($method, $url, $body) {
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 60; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    if ($body) { $p.Body = $body; $p.ContentType = 'application/json' }
    try { $r = Invoke-WebRequest @p } catch { return @{ status = 0; json = $null; raw = $_.Exception.Message } }
    $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$r.StatusCode; json = $j; raw = $r.Content }
}
function Sql($statement) {
    $f = Join-Path $env:TEMP 'wms_report_verify.sql'
    [System.IO.File]::WriteAllText($f, $statement, (New-Object System.Text.UTF8Encoding($false)))
    $out = & cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP --default-character-set=utf8mb4 -N -B < `"$f`" 2>nul"
    Remove-Item $f -Force -ErrorAction SilentlyContinue
    return $out
}

$TAG = 'verify-report'

Write-Host "=== 1. Build, unit tests and mapper XML check ==="
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

# The mapper XML checker guards against the "<=" in a <script> annotation class of
# bug, which makes the application fail to start.
if (-not (Test-Path (Join-Path $diagDir 'CheckMapperXml.class'))) {
    & 'D:\Dev\jdk8\bin\javac.exe' -encoding UTF-8 -d $diagDir (Join-Path $diagDir 'CheckMapperXml.java') 2>&1 | Out-Null
}
$xmlOut = & 'D:\Dev\jdk8\bin\java.exe' @('-Dfile.encoding=UTF-8','-cp',$diagDir,'CheckMapperXml',(Join-Path $backend 'target\classes')) 2>&1 | Out-String
$xmlTail = (($xmlOut -split "`r?`n") | Where-Object { $_ -match 'failed:' }) -join ''
Check "mapper XML valid" ($LASTEXITCODE -eq 0) $xmlTail

Write-Host ""
Write-Host "=== 2. Baseline ==="
$freqBefore = @{}
foreach ($row in (Sql "SELECT id, pick_freq FROM wms.base_product WHERE id <= 3;")) {
    $p = ($row -split "`t"); if ($p.Count -ge 2) { $freqBefore[[int]$p[0]] = [int]$p[1] }
}
Check "baseline recorded" $true ("freqs=" + (($freqBefore.GetEnumerator() | ForEach-Object { "$($_.Key):$($_.Value)" }) -join ','))

Write-Host ""
Write-Host "=== 3. Start application ==="
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $java
$psi.Arguments = '-Dfile.encoding=UTF-8 -jar "' + $jar + '"'
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
Write-Host "=== 4. Empty-state dashboard ==="
$r = Api 'Get' "$base/api/report/dashboard" $null
Check "dashboard returns" ($r.json.code -eq 200) ("code=" + $r.json.code)
Check "15 products counted" ([int]$r.json.data.productCount -eq 15) ("products=" + $r.json.data.productCount)
Check "200 locations counted" ([int]$r.json.data.locationCount -eq 200) ("locations=" + $r.json.data.locationCount)
Check "0 occupied locations" ([int]$r.json.data.occupiedLocationCount -eq 0) ("occupied=" + $r.json.data.occupiedLocationCount)
Check "occupancy rate 0%" ([double]$r.json.data.occupancyRate -eq 0) ("rate=" + $r.json.data.occupancyRate)
Check "0 stock quantity" ([double]$r.json.data.stockQuantity -eq 0) ("stock=" + $r.json.data.stockQuantity)
Check "15 products flagged low stock" ([int]$r.json.data.lowStockCount -eq 15) ("lowStock=" + $r.json.data.lowStockCount)

$r = Api 'Get' "$base/api/report/operation-trend?days=7" $null
Check "trend returns for 7 days" ($r.json.code -eq 200 -and [int]$r.json.data.summary.days -eq 7) ("days=" + $r.json.data.summary.days)
Check "no activity on empty database" ([int]$r.json.data.summary.inboundOrders -eq 0) ("inboundOrders=" + $r.json.data.summary.inboundOrders)

$r = Api 'Get' "$base/api/report/frequency-contribution?limit=20" $null
Check "frequency contribution returns" ($r.json.code -eq 200) ("topCount=" + $r.json.data.topCount)
Check "70% crossing point reported" ($null -ne $r.json.data.productsFor70Percent) ("productsFor70%=" + $r.json.data.productsFor70Percent)
Check "frequency sum is positive" ([double]$r.json.data.frequencySum -gt 0) ("sum=" + $r.json.data.frequencySum)

Write-Host ""
Write-Host "=== 5. Create business data ==="
$inBody = '{"warehouseId":1,"supplier":"' + $TAG + '","useSlottingSuggest":true,"strategy":"frequency-greedy","remark":"' + $TAG + '","items":[{"productId":1,"planQuantity":50,"unitPrice":10},{"productId":2,"planQuantity":30,"unitPrice":10},{"productId":3,"planQuantity":20,"unitPrice":10}]}'
$r = Api 'Post' "$base/api/inbound/orders" $inBody
$inId = $r.json.data.id
$r = Api 'Post' "$base/api/inbound/orders/$inId/confirm" $null
Check "inbound confirmed" ($r.json.data.occupiedLocations -eq 3) ("occupied=" + $r.json.data.occupiedLocations)

$r = Api 'Post' "$base/api/outbound/orders" ('{"warehouseId":1,"customer":"' + $TAG + '","remark":"' + $TAG + '","items":[{"productId":1,"quantity":10}]}')
$outId = $r.json.data.id
Check "outbound order created" ($r.json.code -eq 200) ("id=" + $outId)
$r = Api 'Post' "$base/api/outbound/orders/$outId/plan-route?strategy=ant-colony" $null
Check "route planned with ant colony" ($r.json.code -eq 200) ("distance=" + $r.json.data.distance)
$r = Api 'Post' "$base/api/outbound/orders/$outId/confirm" $null
Check "outbound confirmed" ($r.json.data.shippedItems -eq 1) ("shipped=" + $r.json.data.shippedItems)

Write-Host ""
Write-Host "=== 6. Dashboard reflects the created data ==="
$r = Api 'Get' "$base/api/report/dashboard" $null
Check "3 locations occupied" ([int]$r.json.data.occupiedLocationCount -eq 3) ("occupied=" + $r.json.data.occupiedLocationCount)
Check "197 free locations" ([int]$r.json.data.freeLocationCount -eq 197) ("free=" + $r.json.data.freeLocationCount)
Check "occupancy rate 1.5%" ([math]::Abs([double]$r.json.data.occupancyRate - 1.5) -lt 0.01) ("rate=" + $r.json.data.occupancyRate + "%")
Check "stock quantity 90" ([double]$r.json.data.stockQuantity -eq 90) ("stock=" + $r.json.data.stockQuantity)
Check "1 completed inbound order" ([int]$r.json.data.doneInboundCount -eq 1) ("done=" + $r.json.data.doneInboundCount)
Check "1 completed outbound order" ([int]$r.json.data.doneOutboundCount -eq 1) ("done=" + $r.json.data.doneOutboundCount)
Check "12 products still low stock" ([int]$r.json.data.lowStockCount -eq 12) ("lowStock=" + $r.json.data.lowStockCount)

Write-Host ""
Write-Host "=== 7. Operation trend contains today's activity ==="
$r = Api 'Get' "$base/api/report/operation-trend?days=7" $null
Check "one active day" ([int]$r.json.data.summary.activeDays -eq 1) ("activeDays=" + $r.json.data.summary.activeDays)
Check "1 inbound order counted" ([int]$r.json.data.summary.inboundOrders -eq 1) ("inboundOrders=" + $r.json.data.summary.inboundOrders)
Check "100 inbound quantity" ([double]$r.json.data.summary.inboundQuantity -eq 100) ("inboundQty=" + $r.json.data.summary.inboundQuantity)
Check "1 outbound order counted" ([int]$r.json.data.summary.outboundOrders -eq 1) ("outboundOrders=" + $r.json.data.summary.outboundOrders)
Check "10 outbound quantity" ([double]$r.json.data.summary.outboundQuantity -eq 10) ("outboundQty=" + $r.json.data.summary.outboundQuantity)
Check "trend rows are date-sorted" `
      ((($r.json.data.trend | ForEach-Object { $_.statDate }) -join ',') -eq ((($r.json.data.trend | ForEach-Object { $_.statDate }) | Sort-Object) -join ',')) `
      ("dates=" + (($r.json.data.trend | ForEach-Object { $_.statDate }) -join ','))

Write-Host ""
Write-Host "=== 8. Stock distribution ==="
$r = Api 'Get' "$base/api/report/stock-distribution?warehouseId=1" $null
Check "zone distribution returned" ($r.json.data.byZone.Count -ge 1) ("zones=" + $r.json.data.byZone.Count)
$zoneWithStock = $r.json.data.byZone | Where-Object { [double]$_.stockQuantity -gt 0 }
Check "exactly one zone holds stock" ($zoneWithStock.Count -eq 1) ("zones with stock=" + $zoneWithStock.Count)
Check "that zone holds 90 units" ([double]$zoneWithStock[0].stockQuantity -eq 90) ("qty=" + $zoneWithStock[0].stockQuantity)
Check "zone usage rate reported" ($null -ne $zoneWithStock[0].usageRate) ("usageRate=" + $zoneWithStock[0].usageRate + "%")
Check "ABC distribution returned" ($r.json.data.byAbcClass.Count -ge 1) ("classes=" + $r.json.data.byAbcClass.Count)

Write-Host ""
Write-Host "=== 9. Slotting optimisation effect ==="
$r = Api 'Get' "$base/api/report/slotting-effect/1" $null
Check "slotting effect returns" ($r.json.code -eq 200) ("code=" + $r.json.code)
Check "four strategies evaluated" (@($r.json.data.objectives.PSObject.Properties).Count -eq 4) `
      ("strategies=" + (@($r.json.data.objectives.PSObject.Properties | ForEach-Object { $_.Name }) -join ','))
$greedy = [double]$r.json.data.objectives.'frequency-greedy'
$random = [double]$r.json.data.objectives.random
Check "greedy beats random" ($greedy -lt $random) ("greedy=" + $greedy + " random=" + $random)
Check "improvement reported as a percentage" ([double]$r.json.data.greedyVsRandomPercent -gt 0) ("improvement=" + $r.json.data.greedyVsRandomPercent + "%")
Check "genetic matches greedy" ([math]::Abs([double]$r.json.data.objectives.genetic - $greedy) -lt 0.01) ("genetic=" + $r.json.data.objectives.genetic)
Write-Host ("      objectives: " + (@($r.json.data.objectives.PSObject.Properties | ForEach-Object { $_.Name + "=" + $_.Value }) -join '  '))
Write-Host ("      greedy vs random improvement: " + $r.json.data.greedyVsRandomPercent + "%")

Write-Host ""
Write-Host "=== 10. Picking path effect ==="
$r = Api 'Get' "$base/api/report/picking-effect/1" $null
Check "picking effect returns" ($r.json.code -eq 200) ("code=" + $r.json.code)
Check "pick point count is 3" ([int]$r.json.data.pickPointCount -eq 3) ("points=" + $r.json.data.pickPointCount)
Check "four strategies evaluated" (@($r.json.data.distances.PSObject.Properties).Count -eq 4) `
      ("strategies=" + (@($r.json.data.distances.PSObject.Properties | ForEach-Object { $_.Name }) -join ','))
Check "S-shape baseline present" ($null -ne $r.json.data.distances.'s-shape') ("sShape=" + $r.json.data.distances.'s-shape')
Check "improvement vs S-shape reported" ($null -ne $r.json.data.improvementVsSShapePercent.'nearest-neighbor') `
      ("nn improvement=" + $r.json.data.improvementVsSShapePercent.'nearest-neighbor' + "%")
Write-Host ("      distances: " + (@($r.json.data.distances.PSObject.Properties | ForEach-Object { $_.Name + "=" + $_.Value }) -join '  '))

Write-Host ""
Write-Host "=== 11. Real-order algorithm statistics ==="
$r = Api 'Get' "$base/api/report/route-algorithm-statistics" $null
Check "statistics returned" ($r.json.data.Count -ge 1) ("rows=" + $r.json.data.Count)
$grouped = $r.json.data | Where-Object { $_.routeAlgo -eq 'ant-colony' }
Check "ant-colony order counted" ($null -ne $grouped -and [int]$grouped.orderCount -eq 1) `
      ("algo=" + $grouped.routeAlgo + " orders=" + $grouped.orderCount + " avgDistance=" + $grouped.avgDistance)

Write-Host ""
Write-Host "=== 12. Validation ==="
$r = Api 'Get' "$base/api/report/slotting-effect/999999" $null
Check "slotting effect on missing warehouse (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Get' "$base/api/report/picking-effect/999999" $null
Check "picking effect on missing warehouse (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Get' "$base/api/report/operation-trend?days=0" $null
Check "zero days clamped to 1" ([int]$r.json.data.summary.days -eq 1) ("days=" + $r.json.data.summary.days)
$r = Api 'Get' "$base/api/report/frequency-contribution?limit=5" $null
Check "limit honoured" ($r.json.data.items.Count -le 5) ("items=" + $r.json.data.items.Count)

Write-Host ""
Write-Host "=== 13. Effects on an empty warehouse require stock ==="
Sql "DELETE FROM wms.inv_stock;"
Sql "DELETE FROM wms.biz_outbound_item;"
Sql "DELETE FROM wms.biz_outbound_order;"
Sql "DELETE FROM wms.biz_inbound_item;"
Sql "DELETE FROM wms.biz_inbound_order;"
Sql "UPDATE wms.base_location SET status = 1;"
$r = Api 'Get' "$base/api/report/picking-effect/1" $null
Check "picking effect without stock (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)

Write-Host ""
Write-Host "=== 14. Cleanup ==="
foreach ($kv in $freqBefore.GetEnumerator()) {
    Sql "UPDATE wms.base_product SET pick_freq = $($kv.Value) WHERE id = $($kv.Key);"
}
Sql "DELETE FROM wms.biz_stocktake;"
Sql "DELETE FROM wms.inv_stock;"
$stockFinal = [double](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock;")
$usedFinal = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
$inLeft = [int](Sql "SELECT COUNT(*) FROM wms.biz_inbound_order;")
$outLeft = [int](Sql "SELECT COUNT(*) FROM wms.biz_outbound_order;")
Check "stock cleaned" ($stockFinal -eq 0) ("stock=" + $stockFinal)
Check "locations restored" ($usedFinal -eq 0) ("occupied=" + $usedFinal)
Check "orders removed" (($inLeft + $outLeft) -eq 0) ("inbound=" + $inLeft + " outbound=" + $outLeft)

Write-Host ""
Write-Host "=== 15. Stop application ==="
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
if ($fail -eq 0) { Write-Host "REPORT MODULE VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "REPORT MODULE VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
