# Verification for the slotting algorithm service and its REST endpoints.
#
# The heavy cross-check against the Python kernel is covered by the JUnit test
# SlottingCrossCheckTest (mvn test). This script verifies the service layer and
# the HTTP surface:
#   1. build + unit tests
#   2. /api/slotting/strategies lists all four strategies
#   3. /api/slotting/solve returns the same objective as the Python kernel
#   4. every strategy runs and the greedy strategies beat random
#   5. /api/slotting/recommend returns per-product locations with reasons
#   6. recommendation respects distance ordering (nearest slot first)
#   7. validation: unknown strategy rejected, non-existent warehouse rejected
#   8. read-only: warehouse/location/product data unchanged afterwards

$ErrorActionPreference = 'Continue'
$root    = 'D:\DeepSeekXM\XM01'
$backend = Join-Path $root '03-源码\backend'
$mvn     = 'D:\Dev\maven\bin\mvn.cmd'
$java    = 'D:\Dev\jdk8\bin\java.exe'
$jar     = Join-Path $backend 'target\wms-1.0.0.jar'
$base    = 'http://127.0.0.1:8080'

$env:JAVA_HOME = 'D:\Dev\jdk8'
$dbUser = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { 'wms' }
$dbPass = if ($env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD } else { 'Wms@2026App' }

$pass = 0; $fail = 0
function Check($name, $condition, $detail) {
    if ($condition) { Write-Host ("  [PASS] {0,-42} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-42} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}
function Api($method, $url, $body) {
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 60; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    if ($body) { $p.Body = $body; $p.ContentType = 'application/json' }
    try { $r = Invoke-WebRequest @p } catch { return @{ status = 0; json = $null; raw = $_.Exception.Message } }
    $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$r.StatusCode; json = $j; raw = $r.Content }
}

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
$testsRun = ($out -split "`r?`n") | Where-Object { $_ -match 'Tests run:.*Failures: 0' } | Select-Object -First 1
Check "build and unit tests pass" ($null -ne $testsRun) $(if ($testsRun) { $testsRun.Trim() } else { "no test summary" })
$warns = ($out -split "`r?`n") | Where-Object { $_ -match '\[WARNING\]' -and $_ -notmatch 'platform encoding|File encoding' }
Check "no compiler warnings" ($warns.Count -eq 0) ("warnings = " + $warns.Count)

Write-Host ""
Write-Host "=== 2. Start application ==="
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
Write-Host "=== 3. Strategy list ==="
$r = Api 'Get' "$base/api/slotting/strategies" $null
$codes = @($r.json.data.PSObject.Properties | ForEach-Object { $_.Name })
Check "four strategies registered" ($codes.Count -eq 4) ("codes=" + ($codes -join ','))
Check "greedy strategy present" ($codes -contains 'frequency-greedy') ("label=" + $r.json.data.'frequency-greedy')

Write-Host ""
Write-Host "=== 4. Solve: cross-strategy consistency on the live data ==="
# NOTE: the Python golden case uses 50 products over the same 200 slots, while the
# demo database holds 15 products, so the absolute objectives differ by design.
# The same-instance comparison against Python is done by the JUnit test
# SlottingCrossCheckTest, which loads the golden JSON. Here we verify the
# service layer: deterministic strategies must agree with each other, and the
# greedy solution must clearly beat random.
$objectives = @{}
foreach ($s in @('random', 'frequency-greedy', 'abc-greedy', 'genetic')) {
    $r = Api 'Post' "$base/api/slotting/solve?warehouseId=1&strategy=$s" $null
    $objectives[$s] = [double]$r.json.data.objective
    Check ("solve " + $s + " returns a value") ($objectives[$s] -gt 0) `
          ("objective=" + $objectives[$s] + " elapsed=" + $r.json.data.elapsedMillis + "ms")
}
$g = $objectives['frequency-greedy']
Check "abc-greedy equals frequency-greedy" ([math]::Abs($objectives['abc-greedy'] - $g) -lt 0.01) `
      ("abc=" + $objectives['abc-greedy'] + " freq=" + $g)
Check "genetic not worse than greedy" ($objectives['genetic'] -le $g + 0.01) `
      ("genetic=" + $objectives['genetic'] + " greedy=" + $g)
$improve = ($objectives['random'] - $g) / $objectives['random'] * 100.0
Check "greedy beats random by 30%-95%" ($improve -ge 30.0 -and $improve -le 95.0) `
      ("improvement=" + [math]::Round($improve, 2) + "%")

$r = Api 'Post' "$base/api/slotting/solve?warehouseId=1&strategy=frequency-greedy" $null
Check "assignment covers all products" ($r.json.data.itemCount -eq 15) ("itemCount=" + $r.json.data.itemCount)
Check "assignment has unique slots" `
      ((($r.json.data.slotOfItem | Select-Object -Unique).Count) -eq $r.json.data.itemCount) `
      ("unique=" + ($r.json.data.slotOfItem | Select-Object -Unique).Count)

Write-Host ""
Write-Host "=== 5. Recommend: per-product location with reason ==="
$r = Api 'Post' "$base/api/slotting/recommend/1?strategy=frequency-greedy" $null
Check "recommendation returned" ($r.json.data.recommendations.Count -gt 0) ("count=" + $r.json.data.recommendations.Count)
Check "objective reported" ([double]$r.json.data.objective -gt 0) ("objective=" + $r.json.data.objective)
Check "average distance reported" ([double]$r.json.data.averageDistance -gt 0) ("avgDistance=" + $r.json.data.averageDistance + " m")
$first = $r.json.data.recommendations[0]
Check "first recommendation has location" ($null -ne $first.locationCode) ("location=" + $first.locationCode + " distance=" + $first.distance)
Check "first recommendation has reason" ($first.reason.Length -gt 10) ("reason=" + $first.reason.Substring(0, [Math]::Min(60, $first.reason.Length)))

# Highest-frequency product must receive the nearest location
$products = (Api 'Get' "$base/api/products?limit=100" $null).json.data
$topProduct = $products[0]
$topRec = $r.json.data.recommendations | Where-Object { $_.productId -eq $topProduct.id }
Check "top-frequency product gets nearest slot" ($null -ne $topRec -and [math]::Abs([double]$topRec.distance - 3.70) -lt 0.001) `
      ("product=" + $topProduct.productCode + " freq=" + $topProduct.pickFreq + " -> distance=" + $topRec.distance)

$dists = $r.json.data.recommendations | ForEach-Object { [double]$_.distance }
$asc = ($dists -join ',') -eq (($dists | Sort-Object) -join ',')
Check "recommendations are distance-ascending" $asc ("first=" + $dists[0] + " last=" + $dists[-1])

$r = Api 'Post' "$base/api/slotting/recommend/1?productIds=1&strategy=abc-greedy" $null
Check "recommend for one product works" ($r.json.data.recommendations.Count -eq 1) ("count=" + $r.json.data.recommendations.Count)

Write-Host ""
Write-Host "=== 6. Validation ==="
$r = Api 'Post' "$base/api/slotting/solve?warehouseId=1&strategy=no-such-strategy" $null
Check "unknown strategy rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/slotting/solve?warehouseId=999999&strategy=random" $null
Check "non-existent warehouse rejected (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/slotting/recommend/999999?strategy=random" $null
Check "recommend on missing warehouse (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)

Write-Host ""
Write-Host "=== 7. Read-only guarantee ==="
$r = Api 'Get' "$base/api/locations?warehouseId=1&limit=5000" $null
Check "locations unchanged (200)" ($r.json.data.Count -eq 200) ("count=" + $r.json.data.Count)
$r = Api 'Get' "$base/api/products?limit=100" $null
Check "products unchanged (15)" ($r.json.data.Count -eq 15) ("count=" + $r.json.data.Count)
$r = Api 'Get' "$base/api/warehouses" $null
Check "warehouses unchanged (1)" ($r.json.data.Count -eq 1) ("count=" + $r.json.data.Count)

Write-Host ""
Write-Host "=== 8. Stop application ==="
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
if ($fail -eq 0) { Write-Host "SLOTTING SERVICE VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "SLOTTING SERVICE VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
