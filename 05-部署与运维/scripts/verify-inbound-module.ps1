# Verification for the inbound module.
#
# Full business flow:
#   1. build + unit tests
#   2. create an inbound order with useSlottingSuggest=true
#   3. order detail: items must carry a recommended location and a reason
#   4. confirm inbound: stock written, locations occupied, status -> 2
#   5. re-confirm must be rejected (409)
#   6. cancel a completed order must be rejected (409); a pending order can be cancelled
#   7. validation: missing warehouse, empty items, non-positive quantity, bad product
#   8. order number format and sequence
#   9. statistics endpoint
#  10. cleanup: order, items, stock removed and locations restored to free

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
    $f = Join-Path $env:TEMP 'wms_inbound_verify.sql'
    [System.IO.File]::WriteAllText($f, $statement, (New-Object System.Text.UTF8Encoding($false)))
    $out = & cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP --default-character-set=utf8mb4 -N -B < `"$f`" 2>nul"
    Remove-Item $f -Force -ErrorAction SilentlyContinue
    return $out
}

Write-Host "=== 1. Build and unit tests ==="
Push-Location $backend
$out = & $mvn -B clean test package 2>&1 | Out-String
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) {
    Write-Host "build or tests failed:" -ForegroundColor Red
    ($out -split "`r?`n" | Select-Object -Last 20) | ForEach-Object { Write-Host ("  | " + $_) }
    exit 1
}
Check "build and unit tests pass" $true "see surefire summary above"
$warns = ($out -split "`r?`n") | Where-Object { $_ -match '\[WARNING\]' -and $_ -notmatch 'platform encoding|File encoding' }
Check "no compiler warnings" ($warns.Count -eq 0) ("warnings = " + $warns.Count)

Write-Host ""
Write-Host "=== 2. Baseline state ==="
$stockBefore = [int](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock;")
$usedBefore  = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
Check "baseline recorded" $true ("stockSum=" + $stockBefore + " occupiedLocations=" + $usedBefore)

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
Write-Host "=== 4. Create inbound order with slotting suggestion ==="
# products 1..3 have the highest pick frequencies, so the algorithm should place
# them on the nearest free locations.
$body = '{"warehouseId":1,"supplier":"verification supplier","useSlottingSuggest":true,"strategy":"frequency-greedy","remark":"created by verify-inbound-module.ps1","items":[{"productId":1,"planQuantity":10,"unitPrice":12.50,"batchNo":"B-VERIFY-1"},{"productId":2,"planQuantity":20,"unitPrice":8.00,"batchNo":"B-VERIFY-1"},{"productId":3,"planQuantity":5,"unitPrice":30.00,"batchNo":"B-VERIFY-1"}]}'
$r = Api 'Post' "$base/api/inbound/orders" $body
Check "order created (200)" ($r.json.code -eq 200) ("code=" + $r.json.code + " msg=" + $r.json.message)
$orderId = $r.json.data.id
$orderNo = $r.json.data.orderNo
Check "order number format IN+date+seq" ($orderNo -match '^IN\d{8}\d{4}$') ("orderNo=" + $orderNo)
Check "status is pending (0)" ($r.json.data.status -eq 0) ("status=" + $r.json.data.status)
Check "useSlottingSuggest recorded" ($r.json.data.useSlottingSuggest -eq 1) ("flag=" + $r.json.data.useSlottingSuggest)
Check "total quantity = 35" ([double]$r.json.data.totalQuantity -eq 35) ("total=" + $r.json.data.totalQuantity)
Check "total amount = 435.00" ([math]::Abs([double]$r.json.data.totalAmount - 435.0) -lt 0.01) ("amount=" + $r.json.data.totalAmount)

Write-Host ""
Write-Host "=== 5. Order detail carries algorithm recommendations ==="
$r = Api 'Get' "$base/api/inbound/orders/$orderId" $null
$items = $r.json.data.items
Check "detail has 3 items" ($items.Count -eq 3) ("count=" + $items.Count)
$withLocation = $items | Where-Object { $null -ne $_.locationCode }
Check "every item got a location" ($withLocation.Count -eq 3) ("with location=" + $withLocation.Count)
$withReason = $items | Where-Object { $_.suggestReason -and $_.suggestReason.Length -gt 10 }
Check "every item has a reason" ($withReason.Count -eq 3) ("with reason=" + $withReason.Count)
$first = $items[0]
Check "first item is the top-frequency product" ($first.productCode -eq 'P0001') ("product=" + $first.productCode + " freq=" + $first.pickFreq)
Check "top product placed on nearest slot (3.70)" ([math]::Abs([double]$first.distance - 3.70) -lt 0.001) ("distance=" + $first.distance + " location=" + $first.locationCode)
Write-Host ("      sample reason: " + $first.suggestReason)

Write-Host ""
Write-Host "=== 6. Confirm inbound ==="
$r = Api 'Post' "$base/api/inbound/orders/$orderId/confirm" $null
Check "confirm succeeded" ($r.json.code -eq 200) ("code=" + $r.json.code + " msg=" + $r.json.message)
Check "3 items stocked" ($r.json.data.stockedItems -eq 3) ("stockedItems=" + $r.json.data.stockedItems)
Check "3 locations occupied" ($r.json.data.occupiedLocations -eq 3) ("occupiedLocations=" + $r.json.data.occupiedLocations)
Check "status moved to completed (2)" ($r.json.data.status -eq 2) ("status=" + $r.json.data.status)

$stockAfter = [int](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock;")
$usedAfter  = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
Check "stock increased by 35" (($stockAfter - $stockBefore) -eq 35) ("before=" + $stockBefore + " after=" + $stockAfter)
Check "occupied locations increased by 3" (($usedAfter - $usedBefore) -eq 3) ("before=" + $usedBefore + " after=" + $usedAfter)

$r = Api 'Get' "$base/api/inbound/orders/$orderId" $null
Check "order status persisted as 2" ($r.json.data.order.status -eq 2) ("status=" + $r.json.data.order.status)
$actuals = $r.json.data.items | ForEach-Object { [double]$_.actualQuantity }
Check "actual quantities recorded" (($actuals | Measure-Object -Sum).Sum -eq 35) ("sum=" + ($actuals | Measure-Object -Sum).Sum)

Write-Host ""
Write-Host "=== 7. Guards ==="
$r = Api 'Post' "$base/api/inbound/orders/$orderId/confirm" $null
Check "re-confirm rejected (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/inbound/orders/$orderId/cancel" $null
Check "cancel completed order rejected (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)

Write-Host ""
Write-Host "=== 8. Validation ==="
$r = Api 'Post' "$base/api/inbound/orders" '{"supplier":"no warehouse","items":[{"productId":1,"planQuantity":1}]}'
Check "missing warehouse rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/inbound/orders" '{"warehouseId":1,"items":[]}'
Check "empty items rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/inbound/orders" '{"warehouseId":1,"items":[{"productId":1,"planQuantity":0}]}'
Check "zero quantity rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/inbound/orders" '{"warehouseId":1,"items":[{"productId":999999,"planQuantity":1}]}'
Check "unknown product rejected (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Get' "$base/api/inbound/orders/999999" $null
Check "unknown order rejected (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)

Write-Host ""
Write-Host "=== 9. Max-frequency product stays available after occupation ==="
# After the confirmation above, P0001's inventory occupies A01-01, so the next
# recommendation must skip that location.
$r = Api 'Post' "$base/api/slotting/recommend/1?productIds=1&strategy=frequency-greedy" $null
$rec = $r.json.data.recommendations[0]
Check "recommendation skips occupied slot" ($rec.locationCode -ne 'A01-01') `
      ("next location for P0001 = " + $rec.locationCode + " distance=" + $rec.distance)

Write-Host ""
Write-Host "=== 10. Pending order can be cancelled ==="
$r = Api 'Post' "$base/api/inbound/orders" '{"warehouseId":1,"supplier":"cancel me","useSlottingSuggest":false,"items":[{"productId":4,"planQuantity":2,"locationId":null}]}'
$pendingId = $r.json.data.id
Check "pending order created" ($r.json.code -eq 200) ("id=" + $pendingId + " status=" + $r.json.data.status)
$r = Api 'Post' "$base/api/inbound/orders/$pendingId/cancel" $null
Check "pending order cancelled" ($r.json.code -eq 200) ("code=" + $r.json.code)
$r = Api 'Get' "$base/api/inbound/orders/$pendingId" $null
Check "cancelled status persisted (9)" ($r.json.data.order.status -eq 9) ("status=" + $r.json.data.order.status)

Write-Host ""
Write-Host "=== 11. Statistics ==="
$r = Api 'Get' "$base/api/inbound/statistics" $null
Check "statistics returned" ($r.json.data.Count -ge 1) ("rows=" + $r.json.data.Count)

Write-Host ""
Write-Host "=== 12. Cleanup ==="
Sql "DELETE FROM wms.inv_stock WHERE location_id IN (SELECT id FROM wms.base_location WHERE id IN (SELECT location_id FROM wms.biz_inbound_item WHERE order_id IN (SELECT id FROM wms.biz_inbound_order WHERE remark LIKE 'created by verify-inbound%' OR supplier = 'cancel me')));"
Sql "UPDATE wms.base_location SET status = 1 WHERE id IN (SELECT location_id FROM wms.biz_inbound_item WHERE order_id IN (SELECT id FROM wms.biz_inbound_order WHERE remark LIKE 'created by verify-inbound%' OR supplier = 'cancel me'));"
Sql "DELETE FROM wms.biz_inbound_item WHERE order_id IN (SELECT id FROM wms.biz_inbound_order WHERE remark LIKE 'created by verify-inbound%' OR supplier = 'cancel me');"
Sql "DELETE FROM wms.biz_inbound_order WHERE remark LIKE 'created by verify-inbound%' OR supplier = 'cancel me';"
$stockFinal = [int](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock;")
$usedFinal  = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
$ordersLeft = [int](Sql "SELECT COUNT(*) FROM wms.biz_inbound_order;")
Check "stock restored to baseline" ($stockFinal -eq $stockBefore) ("stock=" + $stockFinal + " baseline=" + $stockBefore)
Check "locations restored to baseline" ($usedFinal -eq $usedBefore) ("occupied=" + $usedFinal + " baseline=" + $usedBefore)
Check "test orders removed" ($ordersLeft -eq 0) ("orders remaining=" + $ordersLeft)

Write-Host ""
Write-Host "=== 13. Stop application ==="
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
if ($fail -eq 0) { Write-Host "INBOUND MODULE VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "INBOUND MODULE VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
