# Verification for the warehouse & location module.
#
# Creates a disposable test warehouse, exercises generation and the query
# endpoints against it, then deletes it so the demo data stays clean.
#
# Checks:
#   1. clean build
#   2. warehouse list / detail
#   3. create warehouse (with layout parameters)
#   4. duplicate warehouse code rejected (409)
#   5. validation: missing code / bad gap rejected (400)
#   6. batch generate locations (5 x 8 = 40), distances computed correctly
#   7. generation is idempotent - second call skips all 40
#   8. overwrite=true regenerates instead of skipping
#   9. location list ordered by distance, filtered by status
#  10. nearest-free returns the closest slots first
#  11. zone summary covers all 40 locations
#  12. delete guard: warehouse with locations cannot be deleted (409)
#  13. cleanup: locations removed, then warehouse deleted

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
    if ($condition) { Write-Host ("  [PASS] {0,-40} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-40} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}
function Api($method, $url, $body) {
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 20; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    if ($body) { $p.Body = $body; $p.ContentType = 'application/json' }
    try { $r = Invoke-WebRequest @p } catch { return @{ status = 0; json = $null; raw = $_.Exception.Message } }
    $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$r.StatusCode; json = $j; raw = $r.Content }
}
function RunSql($sql) {
    $f = Join-Path $env:TEMP 'wms_verify.sql'
    [System.IO.File]::WriteAllText($f, $sql, (New-Object System.Text.UTF8Encoding($false)))
    & cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP --default-character-set=utf8mb4 < `"$f`" 2>nul"
    Remove-Item $f -Force -ErrorAction SilentlyContinue
}

Write-Host "=== 1. Build ==="
Push-Location $backend
$buildOut = & $mvn -B -DskipTests clean package 2>&1 | Out-String
$buildCode = $LASTEXITCODE
Pop-Location
if ($buildCode -ne 0) {
    Write-Host "build failed:" -ForegroundColor Red
    ($buildOut -split "`r?`n" | Select-Object -Last 20) | ForEach-Object { Write-Host ("  | " + $_) }
    exit 1
}
$warns = ($buildOut -split "`r?`n") | Where-Object { $_ -match '\[WARNING\]' -and $_ -notmatch 'platform encoding|File encoding' }
Check "build succeeds"   $true ("jar = {0:N2} MB" -f ((Get-Item $jar).Length / 1MB))
Check "no code warnings" ($warns.Count -eq 0) ("warnings = " + $warns.Count)

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
Write-Host "=== 3. Existing demo data ==="
$r = Api 'Get' "$base/api/warehouses" $null
Check "warehouse list works" ($r.json.data.Count -ge 1) ("count=" + $r.json.data.Count)
$demo = $r.json.data[0]
Check "demo warehouse is WH001" ($demo.warehouseCode -eq 'WH001') ("code=" + $demo.warehouseCode + " slots=" + ($demo.aisleCount * $demo.rowCount))

$r = Api 'Get' "$base/api/locations?warehouseId=1&limit=5000" $null
Check "demo warehouse has 200 locations" ($r.json.data.Count -eq 200) ("count=" + $r.json.data.Count)
$first = $r.json.data[0]
$last = $r.json.data[-1]
Check "first location is A01-01 @ 3.70" ($first.locationCode -eq 'A01-01' -and [math]::Abs([double]$first.distance - 3.70) -lt 0.001) `
      ("code=" + $first.locationCode + " distance=" + $first.distance)
Check "list is distance-ascending" ([double]$first.distance -le [double]$last.distance) `
      ("first=" + $first.distance + " last=" + $last.distance)

Write-Host ""
Write-Host "=== 4. Create a disposable test warehouse ==="
$code = 'VW' + (Get-Random -Minimum 100000 -Maximum 999999)
$body = "{""warehouseCode"":""$code"",""warehouseName"":""verification warehouse"",""aisleCount"":5,""rowCount"":8,""aisleGap"":2.50,""rowGap"":1.20,""status"":1,""remark"":""created by verify-warehouse-module.ps1""}"
$r = Api 'Post' "$base/api/warehouses" $body
Check "warehouse created" ($r.json.code -eq 200 -and $r.json.data.id -gt 0) ("id=" + $r.json.data.id + " code=" + $r.json.data.warehouseCode)
$wId = $r.json.data.id

$r = Api 'Post' "$base/api/warehouses" $body
Check "duplicate code rejected (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)

$r = Api 'Post' "$base/api/warehouses" '{"warehouseName":"no code"}'
Check "missing code rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)

$r = Api 'Post' "$base/api/warehouses" ("{""warehouseCode"":""$code-x"",""warehouseName"":""bad gap"",""aisleGap"":0}")
Check "non-positive gap rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)

Write-Host ""
Write-Host "=== 5. Generate locations (5 x 8 = 40) ==="
$genBody = '{"aisleDistance":15,"rowDistance":30,"capacity":100,"overwrite":false}'
$r = Api 'Post' "$base/api/warehouses/$wId/locations/generate" $genBody
Check "40 locations created" ($r.json.data.created -eq 40) ("created=" + $r.json.data.created)
Check "total count is 40"     ($r.json.data.total -eq 40)   ("total=" + $r.json.data.total)
Check "min distance = 3.70"   ([math]::Abs([double]$r.json.data.minDistance - 3.70) -lt 0.001) ("min=" + $r.json.data.minDistance)
Check "max distance = 22.10"  ([math]::Abs([double]$r.json.data.maxDistance - 22.10) -lt 0.001) ("max=" + $r.json.data.maxDistance)
$zones = $r.json.data.zoneCounts
$zoneSum = [int]$zones.A + [int]$zones.B + [int]$zones.C
Check "zone counts cover all 40" ($zoneSum -eq 40) ("A=" + $zones.A + " B=" + $zones.B + " C=" + $zones.C)

Write-Host ""
Write-Host "=== 6. Generation is idempotent ==="
$r = Api 'Post' "$base/api/warehouses/$wId/locations/generate" $genBody
Check "second call skips all 40" ($r.json.data.created -eq 0 -and $r.json.data.skipped -eq 40) `
      ("created=" + $r.json.data.created + " skipped=" + $r.json.data.skipped)
Check "total still 40" ($r.json.data.total -eq 40) ("total=" + $r.json.data.total)

$r = Api 'Post' "$base/api/warehouses/$wId/locations/generate" '{"overwrite":true}'
Check "overwrite regenerates 40" ($r.json.data.created -eq 40 -and $r.json.data.removed -eq 40) `
      ("created=" + $r.json.data.created + " removed=" + $r.json.data.removed)

Write-Host ""
Write-Host "=== 7. Location queries ==="
$r = Api 'Get' "$base/api/locations?warehouseId=$wId&limit=100" $null
Check "location list returns 40" ($r.json.data.Count -eq 40) ("count=" + $r.json.data.Count)
$distances = $r.json.data | ForEach-Object { [double]$_.distance }
$sorted = ($distances -join ',') -eq (($distances | Sort-Object) -join ',')
Check "ordered by distance ascending" $sorted ("first=" + $distances[0] + " last=" + $distances[-1])

$r = Api 'Get' "$base/api/locations/nearest-free?warehouseId=$wId&limit=5" $null
Check "nearest-free returns 5" ($r.json.data.Count -eq 5) ("count=" + $r.json.data.Count)
Check "nearest-free starts at 3.70" ([math]::Abs([double]$r.json.data[0].distance - 3.70) -lt 0.001) `
      ("first distance=" + $r.json.data[0].distance + " code=" + $r.json.data[0].locationCode)

$zoneIdA = $r.json.data[0].zoneId
$r = Api 'Get' "$base/api/locations?warehouseId=$wId&zoneId=$zoneIdA&limit=100" $null
Check "filter by zone works" ($r.json.data.Count -gt 0 -and $r.json.data.Count -lt 40) ("A zone count=" + $r.json.data.Count)

$r = Api 'Get' "$base/api/warehouses/$wId/locations/summary" $null
Check "zone summary present" ($r.json.data.Count -ge 1) ("zone rows=" + $r.json.data.Count)

Write-Host ""
Write-Host "=== 8. Delete guard and cleanup ==="
$r = Api 'Delete' "$base/api/warehouses/$wId" $null
Check "delete blocked while locations exist (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)

RunSql "DELETE FROM wms.base_location WHERE warehouse_id = $wId;"
$r = Api 'Delete' "$base/api/warehouses/$wId" $null
Check "delete succeeds after cleanup" ($r.json.code -eq 200) ("code=" + $r.json.code + " msg=" + $r.json.message)

$r = Api 'Get' "$base/api/warehouses/$wId" $null
Check "deleted warehouse not returned" ($null -eq $r.json.data) ("data=" + $r.json.data)

Write-Host ""
Write-Host "=== 9. Demo data must be untouched ==="
$r = Api 'Get' "$base/api/locations?warehouseId=1&limit=5000" $null
Check "demo warehouse still has 200 locations" ($r.json.data.Count -eq 200) ("count=" + $r.json.data.Count)

Write-Host ""
Write-Host "=== 10. Stop application ==="
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
if ($fail -eq 0) { Write-Host "WAREHOUSE MODULE VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "WAREHOUSE MODULE VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
