# Verification for the outbound module.
#
# Full business flow (stock is created first through the inbound module, then
# consumed through outbound, so the two modules are verified together):
#   1. build + unit tests (both cross-check suites)
#   2. inbound: create and confirm a stock of 100 units on 4 products
#   3. outbound create: nearest-first location allocation, insufficient stock rejected
#   4. plan-route: pick sequence written back, distance recorded, all strategies compared
#   5. confirm: stock decremented, emptied location freed, pick frequency increased
#   6. guards: re-confirm 409, cancel completed 409, unknown strategy 400
#   7. validation: missing warehouse, empty items, non-positive quantity, unknown product
#   8. cleanup: orders, items, stock removed; locations and frequencies restored

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
    if ($condition) { Write-Host ("  [PASS] {0,-46} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-46} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}
# 后端已启用认证与权限校验，统一在此引入共享的登录辅助。
. "$PSScriptRoot\auth-helper.ps1"

function Api($method, $url, $body) {
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 60; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    # 携带访问令牌：无令牌或权限不足时接口会返回 401 / 403
    $__h = Get-AuthHeaders
    if ($__h) { $p.Headers = $__h }
    if ($body) { $p.Body = $body; $p.ContentType = 'application/json' }
    try { $r = Invoke-WebRequest @p } catch { return @{ status = 0; json = $null; raw = $_.Exception.Message } }
    $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$r.StatusCode; json = $j; raw = $r.Content }
}
function Sql($statement) {
    $f = Join-Path $env:TEMP 'wms_outbound_verify.sql'
    [System.IO.File]::WriteAllText($f, $statement, (New-Object System.Text.UTF8Encoding($false)))
    $out = & cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP --default-character-set=utf8mb4 -N -B < `"$f`" 2>nul"
    Remove-Item $f -Force -ErrorAction SilentlyContinue
    return $out
}

$TAG = 'verify-outbound'

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
$warns = ($out -split "`r?`n") | Where-Object { $_ -match '\[WARNING\]' -and $_ -notmatch 'platform encoding|File encoding' }
Check "no compiler warnings" ($warns.Count -eq 0) ("warnings = " + $warns.Count)

Write-Host ""
Write-Host "=== 2. Baseline ==="
$freqBefore = @{}
foreach ($row in (Sql "SELECT id, pick_freq FROM wms.base_product WHERE id <= 4;")) {
    $parts = ($row -split "`t")
    if ($parts.Count -ge 2) { $freqBefore[[int]$parts[0]] = [int]$parts[1] }
}
$usedBefore = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
Check "baseline recorded" $true ("freqs=" + (($freqBefore.GetEnumerator() | ForEach-Object { "$($_.Key):$($_.Value)" }) -join ',') + " occupied=" + $usedBefore)

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
# 登录以取得访问令牌（后端所有 /api/** 接口均要求认证）
if ($ready) {
    $null = Initialize-Auth
    if (-not $global:WMS_TOKEN) {
        Write-Host '  登录失败，后续请求将因缺少令牌返回 401' -ForegroundColor Red
    }
}

if (-not $ready) {
    Write-Host "  application not ready" -ForegroundColor Red
    if (-not $proc.HasExited) { $proc.Kill() }
    try { Write-Host ((($outTask.Result) -split "`r?`n" | Select-Object -Last 20) -join "`n") } catch { }
    exit 2
}
Write-Host ("  ready after {0}s (pid {1})" -f ($i * 3), $proc.Id)

Write-Host ""
Write-Host "=== 4. Prepare stock via the inbound module ==="
# Four products, 100 units each. The slotting algorithm places the highest
# frequency products on the nearest locations.
$inBody = '{"warehouseId":1,"supplier":"' + $TAG + '","useSlottingSuggest":true,"strategy":"frequency-greedy","remark":"' + $TAG + '","items":[{"productId":1,"planQuantity":100,"unitPrice":10},{"productId":2,"planQuantity":100,"unitPrice":10},{"productId":3,"planQuantity":100,"unitPrice":10},{"productId":4,"planQuantity":100,"unitPrice":10}]}'
$r = Api 'Post' "$base/api/inbound/orders" $inBody
Check "inbound order created" ($r.json.code -eq 200) ("id=" + $r.json.data.id)
$inId = $r.json.data.id
$r = Api 'Post' "$base/api/inbound/orders/$inId/confirm" $null
Check "inbound confirmed (4 locations)" ($r.json.data.occupiedLocations -eq 4) ("occupied=" + $r.json.data.occupiedLocations)

$r = Api 'Get' "$base/api/inbound/orders/$inId" $null
$allocated = @{}
foreach ($it in $r.json.data.items) { $allocated[[int]$it.productId] = [double]$it.distance }
Check "stock placed on 4 distinct locations" ($allocated.Count -eq 4) (($allocated.GetEnumerator() | ForEach-Object { "P$($_.Key)@$($_.Value)m" }) -join ' ')

Write-Host ""
Write-Host "=== 5. Outbound creation: nearest-first allocation ==="
$outBody = '{"warehouseId":1,"customer":"' + $TAG + '","remark":"' + $TAG + '","items":[{"productId":1,"quantity":30},{"productId":2,"quantity":100}]}'
$r = Api 'Post' "$base/api/outbound/orders" $outBody
Check "outbound order created" ($r.json.code -eq 200) ("id=" + $r.json.data.id + " no=" + $r.json.data.orderNo)
$outId = $r.json.data.id
Check "order number format OUT+date+seq" ($r.json.data.orderNo -match '^OUT\d{8}\d{4}$') ("no=" + $r.json.data.orderNo)
Check "status is pending (0)" ($r.json.data.status -eq 0) ("status=" + $r.json.data.status)
Check "total quantity = 130" ([double]$r.json.data.totalQuantity -eq 130) ("total=" + $r.json.data.totalQuantity)

$r = Api 'Get' "$base/api/outbound/orders/$outId" $null
$items = $r.json.data.items
Check "two lines allocated" ($items.Count -eq 2) ("lines=" + $items.Count)
Check "allocation uses the nearest stock location" `
      (($items | Where-Object { $_.productCode -eq 'P0001' }).locationCode -eq 'A01-01') `
      ("P0001 from " + (($items | Where-Object { $_.productCode -eq 'P0001' }).locationCode))
Check "pickSeq initialised to 0" (($items | Where-Object { $_.pickSeq -eq 0 }).Count -eq 2) ("zero seqs=" + ($items | Where-Object { $_.pickSeq -eq 0 }).Count)

$r = Api 'Post' "$base/api/outbound/orders" '{"warehouseId":1,"customer":"too much","items":[{"productId":1,"quantity":9999}]}'
Check "insufficient stock rejected (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)

Write-Host ""
Write-Host "=== 6. Plan the picking route ==="
$r = Api 'Post' "$base/api/outbound/orders/$outId/plan-route?strategy=ant-colony" $null
Check "route planned" ($r.json.code -eq 200) ("distance=" + $r.json.data.distance + " m")
Check "strategy label returned" ($r.json.data.strategyLabel -eq '蚁群算法') ("label=" + $r.json.data.strategyLabel)
Check "pick point count = 2" ($r.json.data.pickPointCount -eq 2) ("points=" + $r.json.data.pickPointCount)
Check "two items updated with sequence" ($r.json.data.updatedItems -eq 2) ("updated=" + $r.json.data.updatedItems)
$cmp = $r.json.data.comparison
# NOTE: use @(...) here. $cmp.PSObject.Properties.Count would resolve to the
# Count property of EACH element (member enumeration), yielding "1 1 1 1".
$cmpCodes = @($cmp.PSObject.Properties | ForEach-Object { $_.Name })
Check "all four strategies compared" ($cmpCodes.Count -eq 4) ("strategies=" + ($cmpCodes -join ','))
# P1 sits on A01-01 (x=1,y=1 -> 3.70 m) and P2 on A01-02 (x=1,y=2 -> 4.90 m).
# Optimal route [0,0]->[1,1]->[1,2]->[0,0] = 3.70 + 1.20 + 4.90 = 9.80 m.
Check "route distance for A01-01 + A01-02 is 9.80 m" ([math]::Abs([double]$r.json.data.distance - 9.80) -lt 0.01) ("distance=" + $r.json.data.distance)
Write-Host ("      distances: " + (($cmp.PSObject.Properties | ForEach-Object { $_.Name + "=" + $_.Value }) -join '  '))

$r = Api 'Get' "$base/api/outbound/orders/$outId" $null
$seqs = $r.json.data.items | ForEach-Object { [int]$_.pickSeq } | Sort-Object
Check "pick sequences are 1 and 2" (($seqs -join ',') -eq '1,2') ("seqs=" + ($seqs -join ','))
Check "order status moved to picking (1)" ($r.json.data.order.status -eq 1) ("status=" + $r.json.data.order.status)
Check "route distance persisted" ([double]$r.json.data.order.routeDistance -gt 0) ("routeDistance=" + $r.json.data.order.routeDistance)
Check "route detail persisted as JSON" ($r.json.data.order.routeDetail.StartsWith('[')) ("detail=" + $r.json.data.order.routeDetail)

Write-Host ""
Write-Host "=== 7. Confirm outbound ==="
$stockBeforeShip = [double](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock WHERE product_id IN (1,2);")
$r = Api 'Post' "$base/api/outbound/orders/$outId/confirm" $null
Check "confirm succeeded" ($r.json.code -eq 200) ("code=" + $r.json.code + " msg=" + $r.json.message)
Check "two lines shipped" ($r.json.data.shippedItems -eq 2) ("shippedItems=" + $r.json.data.shippedItems)
Check "shipped quantity = 130" ([double]$r.json.data.shippedQuantity -eq 130) ("shipped=" + $r.json.data.shippedQuantity)
Check "P0002 emptied location freed" ($r.json.data.freedLocations -ge 1) ("freed=" + $r.json.data.freedLocations)
$stockAfterShip = [double](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock WHERE product_id IN (1,2);")
Check "stock decremented by 130" ([math]::Abs(($stockBeforeShip - $stockAfterShip) - 130) -lt 0.01) `
      ("before=" + $stockBeforeShip + " after=" + $stockAfterShip)

$r = Api 'Get' "$base/api/outbound/orders/$outId" $null
Check "order status is completed (2)" ($r.json.data.order.status -eq 2) ("status=" + $r.json.data.order.status)
Check "all lines marked picked" (($r.json.data.items | Where-Object { $_.picked -eq 1 }).Count -eq 2) ("picked=" + ($r.json.data.items | Where-Object { $_.picked -eq 1 }).Count)

$freqAfter = @{}
foreach ($row in (Sql "SELECT id, pick_freq FROM wms.base_product WHERE id <= 4;")) {
    $parts = ($row -split "`t")
    if ($parts.Count -ge 2) { $freqAfter[[int]$parts[0]] = [int]$parts[1] }
}
Check "P0001 pick frequency +30" (($freqAfter[1] - $freqBefore[1]) -eq 30) ("before=" + $freqBefore[1] + " after=" + $freqAfter[1])
Check "P0002 pick frequency +100" (($freqAfter[2] - $freqBefore[2]) -eq 100) ("before=" + $freqBefore[2] + " after=" + $freqAfter[2])
Check "P0003 frequency untouched" (($freqAfter[3] - $freqBefore[3]) -eq 0) ("before=" + $freqBefore[3] + " after=" + $freqAfter[3])

Write-Host ""
Write-Host "=== 8. Guards and validation ==="
$r = Api 'Post' "$base/api/outbound/orders/$outId/confirm" $null
Check "re-confirm rejected (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/outbound/orders/$outId/cancel" $null
Check "cancel completed rejected (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)

$r = Api 'Post' "$base/api/outbound/orders" '{"customer":"no warehouse","items":[{"productId":1,"quantity":1}]}'
Check "missing warehouse rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/outbound/orders" '{"warehouseId":1,"items":[]}'
Check "empty items rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/outbound/orders" '{"warehouseId":1,"items":[{"productId":1,"quantity":0}]}'
Check "zero quantity rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/outbound/orders" '{"warehouseId":1,"items":[{"productId":999999,"quantity":1}]}'
Check "unknown product rejected (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Get' "$base/api/outbound/orders/999999" $null
Check "unknown order rejected (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)

$r = Api 'Post' "$base/api/outbound/orders" '{"warehouseId":1,"customer":"bad strategy","items":[{"productId":3,"quantity":1}]}'
$tmpId = $r.json.data.id
$r = Api 'Post' "$base/api/outbound/orders/$tmpId/plan-route?strategy=no-such" $null
Check "unknown strategy rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/outbound/orders/$tmpId/cancel" $null
Check "pending order can be cancelled" ($r.json.code -eq 200) ("code=" + $r.json.code)

$r = Api 'Get' "$base/api/outbound/strategies" $null
$exposedCodes = @($r.json.data.PSObject.Properties | ForEach-Object { $_.Name })
Check "four strategies exposed" ($exposedCodes.Count -eq 4) ("codes=" + ($exposedCodes -join ','))

Write-Host ""
Write-Host "=== 9. Statistics ==="
$r = Api 'Get' "$base/api/outbound/statistics" $null
Check "statistics returned" ($r.json.data.Count -ge 1) ("rows=" + $r.json.data.Count)

Write-Host ""
Write-Host "=== 10. Cleanup ==="
Sql "DELETE FROM wms.inv_stock WHERE location_id IN (SELECT location_id FROM wms.biz_inbound_item WHERE order_id IN (SELECT id FROM wms.biz_inbound_order WHERE supplier = '$TAG' OR remark = '$TAG'));"
Sql "UPDATE wms.base_location SET status = 1 WHERE id IN (SELECT location_id FROM wms.biz_inbound_item WHERE order_id IN (SELECT id FROM wms.biz_inbound_order WHERE supplier = '$TAG' OR remark = '$TAG'));"
Sql "DELETE FROM wms.biz_outbound_item WHERE order_id IN (SELECT id FROM wms.biz_outbound_order WHERE customer = '$TAG' OR remark = '$TAG' OR customer = 'too much' OR customer = 'bad strategy' OR customer = 'no warehouse');"
Sql "DELETE FROM wms.biz_outbound_order WHERE customer = '$TAG' OR remark = '$TAG' OR customer = 'too much' OR customer = 'bad strategy' OR customer = 'no warehouse';"
Sql "DELETE FROM wms.biz_inbound_item WHERE order_id IN (SELECT id FROM wms.biz_inbound_order WHERE supplier = '$TAG' OR remark = '$TAG');"
Sql "DELETE FROM wms.biz_inbound_order WHERE supplier = '$TAG' OR remark = '$TAG';"
foreach ($kv in $freqBefore.GetEnumerator()) {
    Sql "UPDATE wms.base_product SET pick_freq = $($kv.Value) WHERE id = $($kv.Key);"
}

$stockFinal = [double](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock;")
$usedFinal = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
$inLeft = [int](Sql "SELECT COUNT(*) FROM wms.biz_inbound_order;")
$outLeft = [int](Sql "SELECT COUNT(*) FROM wms.biz_outbound_order;")
Check "stock cleaned" ($stockFinal -eq 0) ("stock=" + $stockFinal)
Check "locations restored to baseline" ($usedFinal -eq $usedBefore) ("occupied=" + $usedFinal + " baseline=" + $usedBefore)
Check "inbound orders removed" ($inLeft -eq 0) ("remaining=" + $inLeft)
Check "outbound orders removed" ($outLeft -eq 0) ("remaining=" + $outLeft)
$freqRestored = $true
foreach ($row in (Sql "SELECT id, pick_freq FROM wms.base_product WHERE id <= 4;")) {
    $parts = ($row -split "`t")
    if ($parts.Count -ge 2 -and $freqBefore[[int]$parts[0]] -ne [int]$parts[1]) { $freqRestored = $false }
}
Check "pick frequencies restored" $freqRestored ("expected=" + (($freqBefore.GetEnumerator() | ForEach-Object { "$($_.Key):$($_.Value)" }) -join ','))

Write-Host ""
Write-Host "=== 11. Stop application ==="
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
if ($fail -eq 0) { Write-Host "OUTBOUND MODULE VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "OUTBOUND MODULE VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
