# Verification for the slotting optimisation plan module.
#
# To give the optimiser real work to do, stock is deliberately placed in
# sub-optimal locations rather than through the algorithm:
#   * place the three products at the three locations furthest from the I/O
#     point, with the highest-frequency product (P0001) on the very farthest
#     one. A good optimiser must move P0001 towards the I/O point.
#
#   1. build + unit tests + mapper XML check
#   2. optimisation plan on an empty warehouse is rejected
#   3. place stock deliberately badly, then check the current objective
#   4. preview: optimisation space, improvement, assignment and move details
#   5. compare strategies: random vs greedy vs ABC vs genetic
#   6. save the plan, then query history and detail
#   7. apply: stock relocated, location statuses consistent, objective improved
#   8. guards: re-apply rejected, deleting an applied plan rejected
#   9. statistics by strategy
#  10. cleanup restores everything

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
    $f = Join-Path $env:TEMP 'wms_slotplan_verify.sql'
    [System.IO.File]::WriteAllText($f, $statement, (New-Object System.Text.UTF8Encoding($false)))
    $out = & cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP --default-character-set=utf8mb4 -N -B < `"$f`" 2>nul"
    Remove-Item $f -Force -ErrorAction SilentlyContinue
    return $out
}

$TAG = 'verify-slotplan'

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
if (-not (Test-Path (Join-Path $diagDir 'CheckMapperXml.class'))) {
    & 'D:\Dev\jdk8\bin\javac.exe' -encoding UTF-8 -d $diagDir (Join-Path $diagDir 'CheckMapperXml.java') 2>&1 | Out-Null
}
$xmlOut = & 'D:\Dev\jdk8\bin\java.exe' @('-Dfile.encoding=UTF-8','-cp',$diagDir,'CheckMapperXml',(Join-Path $backend 'target\classes')) 2>&1 | Out-String
$xmlTail = (($xmlOut -split "`r?`n") | Where-Object { $_ -match 'checked:' }) -join ''
Check "mapper XML valid" ($LASTEXITCODE -eq 0) $xmlTail

Write-Host ""
Write-Host "=== 2. Baseline: clean business data ==="
Sql "DELETE FROM wms.biz_slotting_plan;"
Sql "DELETE FROM wms.biz_stocktake;"
Sql "DELETE FROM wms.inv_stock;"
Sql "DELETE FROM wms.biz_outbound_item;"
Sql "DELETE FROM wms.biz_outbound_order;"
Sql "DELETE FROM wms.biz_inbound_item;"
Sql "DELETE FROM wms.biz_inbound_order;"
Sql "UPDATE wms.base_location SET status = 1;"
$planCount = [int](Sql "SELECT COUNT(*) FROM wms.biz_slotting_plan;")
Check "plan table empty" ($planCount -eq 0) ("plans=" + $planCount)

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
Write-Host "=== 4. Preview on an empty warehouse ==="
$r = Api 'Post' "$base/api/slotting/plans/preview?warehouseId=1&strategy=frequency-greedy" $null
Check "preview returns" ($r.json.code -eq 200) ("code=" + $r.json.code)
Check "15 products in the plan" ([int]$r.json.data.summary.productCount -eq 15) ("products=" + $r.json.data.summary.productCount)
Check "200 locations available" ([int]$r.json.data.summary.locationCount -eq 200) ("locations=" + $r.json.data.summary.locationCount)
Check "current objective is 0 (no stock)" ([double]$r.json.data.summary.currentObjective -eq 0) ("current=" + $r.json.data.summary.currentObjective)
Check "optimised objective is positive" ([double]$r.json.data.summary.optimizedObjective -gt 0) ("optimized=" + $r.json.data.summary.optimizedObjective)
Check "no relocation needed without stock" ([int]$r.json.data.summary.movesNeeded -eq 0) ("moves=" + $r.json.data.summary.movesNeeded)

Write-Host ""
Write-Host "=== 5. Place stock deliberately badly ==="
# A01-01 is nearest (3.70 m); the farthest locations are around 49 m and 47.8 m.
# Put the highest-frequency product P0001 (freq 480) on the FARTHEST location and
# the lowest-frequency product P0003 (freq 300) on the nearest one.
Sql "INSERT INTO wms.inv_stock (warehouse_id, location_id, product_id, quantity, locked_quantity) SELECT 1, id, 1, 100, 0 FROM wms.base_location WHERE warehouse_id=1 ORDER BY distance DESC LIMIT 1;"
Sql "INSERT INTO wms.inv_stock (warehouse_id, location_id, product_id, quantity, locked_quantity) SELECT 1, id, 2, 100, 0 FROM wms.base_location WHERE warehouse_id=1 ORDER BY distance DESC LIMIT 1 OFFSET 1;"
Sql "INSERT INTO wms.inv_stock (warehouse_id, location_id, product_id, quantity, locked_quantity) VALUES (1, (SELECT id FROM wms.base_location WHERE location_code='A01-01'), 3, 100, 0);"
Sql "UPDATE wms.base_location SET status = 2 WHERE id IN (SELECT location_id FROM wms.inv_stock WHERE warehouse_id = 1);"
$r = Api 'Get' "$base/api/stock?warehouseId=1&limit=10" $null
Check "3 stock rows placed" ($r.json.data.Count -eq 3) ("rows=" + $r.json.data.Count)
$placements = ($r.json.data | ForEach-Object { $_.productCode + '@' + $_.locationCode + '(' + $_.distance + 'm)' }) -join '  '
Write-Host ("      current placement: " + $placements)
$p1 = $r.json.data | Where-Object { $_.productCode -eq 'P0001' }
Check "P0001 sits on the farthest location" ([double]$p1.distance -gt 40) ("P0001 distance=" + $p1.distance)

Write-Host ""
Write-Host "=== 6. Preview with real optimisation space ==="
$r = Api 'Post' "$base/api/slotting/plans/preview?warehouseId=1&strategy=frequency-greedy" $null
Check "preview returns" ($r.json.code -eq 200) ("code=" + $r.json.code)
$s = $r.json.data.summary
Check "3 stocked products detected" ([int]$s.stockedProducts -eq 3) ("stocked=" + $s.stockedProducts)
Check "current objective is positive" ([double]$s.currentObjective -gt 0) ("current=" + $s.currentObjective)
Check "optimised objective is much smaller" ([double]$s.optimizedObjective -lt [double]$s.currentObjective) `
      ("current=" + $s.currentObjective + " optimized=" + $s.optimizedObjective)
Check "improvement is reported" ([double]$s.improvementPercent -gt 0) ("improvement=" + $s.improvementPercent + "%")
Check "relocation moves are detected" ([int]$s.movesNeeded -ge 1) ("moves=" + $s.movesNeeded)
$p1Move = $r.json.data.moves | Where-Object { $_.productCode -eq 'P0001' }
Check "P0001 is scheduled to move closer" ($null -ne $p1Move -and [double]$p1Move.toDistance -lt [double]$p1Move.fromDistance) `
      ("P0001 " + $p1Move.fromLocationCode + "(" + $p1Move.fromDistance + "m) -> " + $p1Move.toLocationCode + "(" + $p1Move.toDistance + "m)")
Check "assignment covers all 15 products" ($r.json.data.assignments.Count -eq 15) ("assignments=" + $r.json.data.assignments.Count)
Write-Host ("      objectives: current=" + $s.currentObjective + "  optimized=" + $s.optimizedObjective + "  improvement=" + $s.improvementPercent + "%")
Write-Host ("      P0001 move: " + $p1Move.fromLocationCode + " (" + $p1Move.fromDistance + " m) -> " + $p1Move.toLocationCode + " (" + $p1Move.toDistance + " m)")

Write-Host ""
Write-Host "=== 7. Compare strategies ==="
$results = @{}
foreach ($st in @('random', 'frequency-greedy', 'abc-greedy', 'genetic')) {
    $r = Api 'Post' "$base/api/slotting/plans/preview?warehouseId=1&strategy=$st" $null
    $results[$st] = [double]$r.json.data.summary.optimizedObjective
    Check ("preview " + $st) ($r.json.code -eq 200) ("optimized=" + $results[$st])
}
Check "greedy beats random" ($results['frequency-greedy'] -lt $results['random']) `
      ("greedy=" + $results['frequency-greedy'] + " random=" + $results['random'])
Check "genetic matches greedy" ([math]::Abs($results['genetic'] - $results['frequency-greedy']) -lt 0.01) `
      ("genetic=" + $results['genetic'])

Write-Host ""
Write-Host "=== 8. Save the plan ==="
$r = Api 'Post' ("$base/api/slotting/plans?warehouseId=1&strategy=frequency-greedy&remark=" + $TAG) $null
Check "plan saved" ($r.json.code -eq 200) ("planNo=" + $r.json.data.planNo + " id=" + $r.json.data.planId)
$planId = $r.json.data.planId
$planNo = $r.json.data.planNo
Check "plan number format SP+date+seq" ($planNo -match '^SP\d{8}\d{4}$') ("planNo=" + $planNo)
$savedImprovement = [double]$r.json.data.summary.improvementPercent

$r = Api 'Get' "$base/api/slotting/plans?warehouseId=1" $null
Check "history contains the plan" ($r.json.data.Count -eq 1) ("plans=" + $r.json.data.Count)
Check "plan is not applied yet" ($r.json.data[0].applied -eq 0) ("applied=" + $r.json.data[0].applied)

$r = Api 'Get' "$base/api/slotting/plans/$planId" $null
Check "detail returns the plan" ($r.json.data.planNo -eq $planNo) ("planNo=" + $r.json.data.planNo)
Check "detail contains the assignment array" ($r.json.data.slotOfProduct.Count -eq 15) ("assignments=" + $r.json.data.slotOfProduct.Count)

Write-Host ""
Write-Host "=== 9. Apply the plan ==="
$before = @{}
foreach ($row in (Sql "SELECT product_id, location_id FROM wms.inv_stock WHERE warehouse_id=1 ORDER BY product_id;")) {
    $p = ($row -split "`t"); if ($p.Count -ge 2) { $before[[int]$p[0]] = [int]$p[1] }
}
$r = Api 'Post' "$base/api/slotting/plans/$planId/apply" $null
Check "apply succeeded" ($r.json.code -eq 200) ("code=" + $r.json.code + " msg=" + $r.json.message)
Check "stock records moved" ([int]$r.json.data.movedRecords -ge 1) ("moved=" + $r.json.data.movedRecords)
Check "moves detail returned" ($r.json.data.moves.Count -ge 1) ("moves=" + $r.json.data.moves.Count)
Check "improvement carried on the plan" ([double]$r.json.data.improvementPercent -gt 0) ("improvement=" + $r.json.data.improvementPercent + "%")

$after = @{}
foreach ($row in (Sql "SELECT product_id, location_id FROM wms.inv_stock WHERE warehouse_id=1 ORDER BY product_id;")) {
    $p = ($row -split "`t"); if ($p.Count -ge 2) { $after[[int]$p[0]] = [int]$p[1] }
}
$changed = 0
foreach ($k in $before.Keys) { if ($after.ContainsKey($k) -and $after[$k] -ne $before[$k]) { $changed++ } }
Check "at least one product changed location" ($changed -ge 1) ("changed=" + $changed)

$r = Api 'Get' "$base/api/stock?warehouseId=1&limit=10" $null
$p1 = $r.json.data | Where-Object { $_.productCode -eq 'P0001' }
Check "P0001 now much closer to the I/O point" ([double]$p1.distance -lt 20) ("P0001 distance=" + $p1.distance + " at " + $p1.locationCode)
Check "all 3 products still stocked" ($r.json.data.Count -eq 3) ("rows=" + $r.json.data.Count)
Check "no location lost its occupied status" `
      ([int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;") -eq 3) `
      ("occupied=" + (Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;"))
Check "no orphaned occupied location" `
      ([int](Sql "SELECT COUNT(*) FROM wms.base_location l WHERE l.status = 2 AND NOT EXISTS (SELECT 1 FROM wms.inv_stock s WHERE s.location_id = l.id);") -eq 0) `
      ("orphans=" + (Sql "SELECT COUNT(*) FROM wms.base_location l WHERE l.status = 2 AND NOT EXISTS (SELECT 1 FROM wms.inv_stock s WHERE s.location_id = l.id);"))

$r = Api 'Get' "$base/api/slotting/plans/$planId" $null
Check "plan marked applied" ($r.json.data.applied -eq 1) ("applied=" + $r.json.data.applied)
Check "moved count persisted" ([int]$r.json.data.movedRecords -ge 1) ("movedRecords=" + $r.json.data.movedRecords)

Write-Host ""
Write-Host "=== 10. Guards and validation ==="
$r = Api 'Post' "$base/api/slotting/plans/$planId/apply" $null
Check "re-apply rejected (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)
$delResp = $null
try {
    # 带令牌，否则鉴权会先返回 401，掩盖应有的 409
    $delParams = @{ Uri = "$base/api/slotting/plans/$planId"; Method = 'Delete'
                    TimeoutSec = 30; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    $delAuth = Get-AuthHeaders
    if ($delAuth) { $delParams.Headers = $delAuth }
    $delResp = Invoke-WebRequest @delParams
} catch { }
Check "delete applied plan rejected (409)" ($null -ne $delResp -and [int]$delResp.StatusCode -eq 409) `
      ("http=" + $(if ($delResp) { [int]$delResp.StatusCode } else { "no response" }))
$r = Api 'Post' "$base/api/slotting/plans/preview?warehouseId=999999" $null
Check "missing warehouse rejected (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/slotting/plans/preview?warehouseId=1&strategy=no-such" $null
Check "unknown strategy rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)

Write-Host ""
Write-Host "=== 11. A pending plan can be deleted ==="
$r = Api 'Post' ("$base/api/slotting/plans?warehouseId=1&strategy=random&remark=" + $TAG + "-2") $null
$pendingId = $r.json.data.planId
Check "second plan saved" ($r.json.code -eq 200) ("planNo=" + $r.json.data.planNo)
$delResp2 = $null
try {
    $delParams2 = @{ Uri = "$base/api/slotting/plans/$pendingId"; Method = 'Delete'
                     TimeoutSec = 30; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    $delAuth2 = Get-AuthHeaders
    if ($delAuth2) { $delParams2.Headers = $delAuth2 }
    $delResp2 = Invoke-WebRequest @delParams2
} catch { }
Check "pending plan deleted" ($null -ne $delResp2 -and [int]$delResp2.StatusCode -eq 200) `
      ("http=" + $(if ($delResp2) { [int]$delResp2.StatusCode } else { "no response" }))

Write-Host ""
Write-Host "=== 12. Statistics by strategy ==="
$r = Api 'Get' "$base/api/slotting/plans/statistics" $null
Check "statistics returned" ($r.json.code -eq 200) ("code=" + $r.json.code)
Check "one strategy row" ($r.json.data.Count -eq 1) ("rows=" + $r.json.data.Count)
Check "average improvement matches the saved plan" `
      ([math]::Abs([double]$r.json.data[0].avgImprovement - $savedImprovement) -lt 0.01) `
      ("avg=" + $r.json.data[0].avgImprovement + " expected=" + $savedImprovement)

Write-Host ""
Write-Host "=== 13. Cleanup ==="
$freqFixes = Sql "SELECT id, pick_freq FROM wms.base_product;"
Sql "DELETE FROM wms.biz_slotting_plan;"
Sql "DELETE FROM wms.inv_stock;"
Sql "UPDATE wms.base_location SET status = 1;"
$planFinal = [int](Sql "SELECT COUNT(*) FROM wms.biz_slotting_plan;")
$stockFinal = [double](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock;")
$usedFinal = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
Check "plans removed" ($planFinal -eq 0) ("plans=" + $planFinal)
Check "stock cleaned" ($stockFinal -eq 0) ("stock=" + $stockFinal)
Check "locations restored" ($usedFinal -eq 0) ("occupied=" + $usedFinal)

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
if ($fail -eq 0) { Write-Host "SLOTTING PLAN MODULE VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "SLOTTING PLAN MODULE VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
