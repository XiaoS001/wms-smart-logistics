# End-to-end smoke test for the WMS backend.
#
# Steps: build -> start -> health check -> login (correct credentials)
#        -> login (wrong credentials must be rejected) -> current user via token
#        -> user list (password must not leak) -> stop
#
# Requires: MySQL running, and the data source credentials available either via
#           the SPRING_DATASOURCE_* environment variables or application-local.yml.

$ErrorActionPreference = 'Continue'

$root    = 'D:\DeepSeekXM\XM01'
$backend = Join-Path $root '03-源码\backend'
$mvn     = 'D:\Dev\maven\bin\mvn.cmd'
$java    = 'D:\Dev\jdk8\bin\java.exe'
$jar     = Join-Path $backend 'target\wms-1.0.0.jar'
$logFile = Join-Path $env:TEMP 'wms-smoke.log'

$env:JAVA_HOME = 'D:\Dev\jdk8'
$base = 'http://127.0.0.1:8080'

$pass = 0
$fail = 0
function Check($name, $condition, $detail) {
    if ($condition) {
        Write-Host ("  [PASS] {0,-32} {1}" -f $name, $detail) -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host ("  [FAIL] {0,-32} {1}" -f $name, $detail) -ForegroundColor Red
        $script:fail++
    }
}

Write-Host "=== 1. Build ==="
Push-Location $backend
$out = & $mvn -B -DskipTests package 2>&1 | Out-String
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) {
    Write-Host "build failed:" -ForegroundColor Red
    ($out -split "`r?`n" | Select-Object -Last 20) | ForEach-Object { Write-Host ("  | " + $_) }
    exit 1
}
Write-Host ("  BUILD SUCCESS, jar = {0:N2} MB" -f ((Get-Item $jar).Length / 1MB))

Write-Host ""
Write-Host "=== 2. Start application ==="
# Child processes launched with Start-Process do not reliably inherit temporary
# process-level environment variables, so the data source credentials are
# injected explicitly here. Values fall back to the project defaults.
$dbUser = $env:SPRING_DATASOURCE_USERNAME
$dbPass = $env:SPRING_DATASOURCE_PASSWORD
if (-not $dbUser) { $dbUser = 'wms' }
if (-not $dbPass) { $dbPass = 'Wms@2026App' }

if (Test-Path $logFile) { Remove-Item $logFile -Force }

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
# Drain the pipes on background tasks so full buffers can never block the JVM.
$outTask = $proc.StandardOutput.ReadToEndAsync()
$errTask = $proc.StandardError.ReadToEndAsync()
Write-Host ("  pid = {0}  (injected SPRING_DATASOURCE_USERNAME={1})" -f $proc.Id, $dbUser)

Write-Host ""
Write-Host "=== 3. Wait for startup ==="
$ready = $false
$waited = 0
for ($i = 1; $i -le 40; $i++) {
    Start-Sleep -Seconds 3
    $waited = $i * 3
    if ($proc.HasExited) { break }
    try {
        $r = Invoke-WebRequest "$base/api/health/ping" -TimeoutSec 5 -UseBasicParsing
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch { }
}
if (-not $ready) {
    Write-Host "  application did not become ready" -ForegroundColor Red
    if (-not $proc.HasExited) { $proc.Kill() }
    try { $proc.WaitForExit(10000) | Out-Null } catch { }
    $stdout = ''
    $stderr = ''
    try { $stdout = $outTask.Result } catch { }
    try { $stderr = $errTask.Result } catch { }
    Write-Host "  --- stdout tail ---"
    ($stdout -split "`r?`n" | Select-Object -Last 20) | ForEach-Object { Write-Host ("  | " + $_) }
    if ($stderr) {
        Write-Host "  --- stderr tail ---"
        ($stderr -split "`r?`n" | Select-Object -Last 10) | ForEach-Object { Write-Host ("  | " + $_) }
    }
    exit 2
}
Write-Host ("  ready after {0}s" -f $waited)

Write-Host ""
Write-Host "=== 4. Health check ==="
$health = (Invoke-WebRequest "$base/api/health" -TimeoutSec 10 -UseBasicParsing).Content | ConvertFrom-Json
Check "database connected"  ($health.data.databaseConnected -eq $true) "databaseConnected=$($health.data.databaseConnected)"
Check "business tables = 17" ($health.data.businessTableCount -eq 17) "count=$($health.data.businessTableCount)"
Check "locations = 200"      ($health.data.locationCount -eq 200)     "count=$($health.data.locationCount)"
Check "products = 15"        ($health.data.productCount -eq 15)       "count=$($health.data.productCount)"

Write-Host ""
Write-Host "=== 5. Login with correct credentials (admin / 123456) ==="
$loginBody = '{"username":"admin","password":"123456"}'
$login = (Invoke-WebRequest "$base/api/auth/login" -Method Post -Body $loginBody `
        -ContentType 'application/json' -TimeoutSec 15 -UseBasicParsing).Content | ConvertFrom-Json
Check "response code = 200" ($login.code -eq 200) "code=$($login.code)"
Check "token issued" ($null -ne $login.data.token -and $login.data.token.Length -gt 20) `
      ("token length = " + $(if ($login.data.token) { $login.data.token.Length } else { 0 }))
Check "realName = Lijunjie" ($login.data.realName -eq '李俊杰') "realName=$($login.data.realName)"
Check "role ADMIN assigned" ($login.data.roles -contains 'ADMIN') ("roles=" + ($login.data.roles -join ','))
Check "permissions loaded" ($login.data.permissions.Count -ge 17) ("count=" + $login.data.permissions.Count)

Write-Host ""
Write-Host "=== 6. Login with wrong password (must be rejected) ==="
try {
    $bad = Invoke-WebRequest "$base/api/auth/login" -Method Post `
        -Body '{"username":"admin","password":"wrong-password"}' `
        -ContentType 'application/json' -TimeoutSec 15 -UseBasicParsing
    $badJson = $bad.Content | ConvertFrom-Json
    Check "rejected with code 401" ($badJson.code -eq 401) "code=$($badJson.code) message=$($badJson.message)"
} catch {
    Check "rejected with code 401" $false ("unexpected exception: " + $_.Exception.Message)
}

Write-Host ""
Write-Host "=== 7. Current user via token ==="
$headers = @{ Authorization = "Bearer " + $login.data.token }
$me = (Invoke-WebRequest "$base/api/auth/me" -Headers $headers -TimeoutSec 10 -UseBasicParsing).Content | ConvertFrom-Json
Check "me returns admin" ($me.data.username -eq 'admin') "username=$($me.data.username)"

Write-Host ""
Write-Host "=== 8. User list ==="
$users = (Invoke-WebRequest "$base/api/users" -TimeoutSec 10 -UseBasicParsing).Content | ConvertFrom-Json
Check "user count >= 3" ($users.data.Count -ge 3) ("count=" + $users.data.Count)
Check "password not leaked" ($null -eq $users.data[0].password) "password field = $($users.data[0].password)"

Write-Host ""
Write-Host "=== 9. Stop application ==="
if (-not $proc.HasExited) {
    $proc.Kill()
    Start-Sleep -Seconds 2
    Write-Host "  stopped"
}

Write-Host ""
Write-Host ("=== RESULT: {0} passed, {1} failed ===" -f $pass, $fail)
if ($fail -eq 0) {
    Write-Host "SMOKE TEST PASSED" -ForegroundColor Green
    exit 0
} else {
    Write-Host "SMOKE TEST FAILED - application log:" -ForegroundColor Red
    Write-Host ("  " + $logFile)
    exit 3
}
