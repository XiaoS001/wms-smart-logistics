# Extended verification for the user & permission module.
#
# Covers what the basic smoke test does not:
#   - clean build with zero compiler warnings
#   - login edge cases: unknown user, empty fields, malformed JSON, disabled account
#   - role/permission joins for all three accounts
#   - token handling: missing / forged / valid / without Bearer prefix
#   - user endpoints: list (no password), detail, non-existent id, unsupported method
#   - application log must contain no ERROR lines
#
# Note on the HTTP helper below: PowerShell 7 offers -SkipHttpErrorCheck, which
# returns 4xx/5xx responses instead of throwing. That is the reliable way to read
# an error body; catching the exception and digging into its response stream does
# not work in this environment.

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
    if ($condition) { Write-Host ("  [PASS] {0,-36} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-36} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}

function Invoke-Api($method, $url, $body, $headers) {
    $params = @{
        Uri = $url
        Method = $method
        TimeoutSec = 15
        UseBasicParsing = $true
        SkipHttpErrorCheck = $true
    }
    if ($body) { $params.Body = $body; $params.ContentType = 'application/json' }
    if ($headers) { $params.Headers = $headers }
    try {
        $resp = Invoke-WebRequest @params
    } catch {
        return @{ status = 0; json = $null; raw = ("request failed: " + $_.Exception.Message) }
    }
    $json = $null
    try { $json = $resp.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$resp.StatusCode; json = $json; raw = $resp.Content }
}

Write-Host "=== 1. Clean build (compiler warnings matter) ==="
Push-Location $backend
$buildOut = & $mvn -B -DskipTests clean package 2>&1 | Out-String
$buildCode = $LASTEXITCODE
Pop-Location
if ($buildCode -ne 0) {
    Write-Host "build failed:" -ForegroundColor Red
    ($buildOut -split "`r?`n" | Select-Object -Last 20) | ForEach-Object { Write-Host ("  | " + $_) }
    exit 1
}
$warnLines = ($buildOut -split "`r?`n") | Where-Object {
    $_ -match '\[WARNING\]' -and $_ -notmatch 'platform encoding|File encoding has not been set'
}
Check "build succeeds"   $true ("jar = {0:N2} MB" -f ((Get-Item $jar).Length / 1MB))
Check "no code warnings" ($warnLines.Count -eq 0) ("warning lines = " + $warnLines.Count)
if ($warnLines.Count -gt 0) { $warnLines | Select-Object -First 8 | ForEach-Object { Write-Host ("      " + $_.Trim()) } }

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

$ready = $false; $waited = 0
for ($i = 1; $i -le 40; $i++) {
    Start-Sleep -Seconds 3; $waited = $i * 3
    if ($proc.HasExited) { break }
    if ((Invoke-Api 'Get' "$base/api/health/ping" $null $null).status -eq 200) { $ready = $true; break }
}
if (-not $ready) {
    Write-Host "  application not ready" -ForegroundColor Red
    if (-not $proc.HasExited) { $proc.Kill() }
    try { Write-Host ((($outTask.Result) -split "`r?`n" | Select-Object -Last 20) -join "`n") } catch { }
    exit 2
}
Write-Host ("  ready after {0}s (pid {1})" -f $waited, $proc.Id)

Write-Host ""
Write-Host "=== 3. Login edge cases ==="
$r = Invoke-Api 'Post' "$base/api/auth/login" '{"username":"no-such-user","password":"123456"}' $null
Check "unknown user -> 401" ($r.status -eq 401 -and $r.json.code -eq 401) ("http=" + $r.status + " code=" + $r.json.code)

$r = Invoke-Api 'Post' "$base/api/auth/login" '{"username":"admin","password":""}' $null
Check "empty password -> 400" ($r.status -eq 400 -and $r.json.code -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)

$r = Invoke-Api 'Post' "$base/api/auth/login" '{"username":"","password":"123456"}' $null
Check "empty username -> 400" ($r.status -eq 400 -and $r.json.code -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)

$r = Invoke-Api 'Post' "$base/api/auth/login" '{not-valid-json' $null
Check "malformed json -> 400" ($r.status -eq 400 -and $r.json.code -eq 400) ("http=" + $r.status + " bodyCode=" + $r.json.code)

Write-Host "  (temporarily disabling account 'picker' to exercise the 403 path)"
$mysqlExe = 'D:\Dev\mysql8\bin\mysql.exe'
$sqlFile = Join-Path $env:TEMP 'wms_disable.sql'
[System.IO.File]::WriteAllText($sqlFile, "UPDATE wms.sys_user SET status=0 WHERE username='picker';", (New-Object System.Text.UTF8Encoding($false)))
& cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP < `"$sqlFile`" 2>nul"
Start-Sleep -Milliseconds 800
$r = Invoke-Api 'Post' "$base/api/auth/login" '{"username":"picker","password":"123456"}' $null
Check "disabled account -> 403" ($r.json.code -eq 403) ("code=" + $r.json.code + " msg=" + $r.json.message)
[System.IO.File]::WriteAllText($sqlFile, "UPDATE wms.sys_user SET status=1 WHERE username='picker';", (New-Object System.Text.UTF8Encoding($false)))
& cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP < `"$sqlFile`" 2>nul"
Remove-Item $sqlFile -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "=== 4. Role and permission joins for every account ==="
$tokens = @{}
foreach ($u in @('admin', 'keeper', 'picker')) {
    $r = Invoke-Api 'Post' "$base/api/auth/login" ("{""username"":""$u"",""password"":""123456""}") $null
    if ($r.json.code -eq 200) {
        $tokens[$u] = $r.json.data.token
        Check ("login " + $u) ($r.json.data.permissions.Count -gt 0) `
              ("roles=" + ($r.json.data.roles -join ',') + " permissions=" + $r.json.data.permissions.Count)
    } else {
        Check ("login " + $u) $false ("code=" + $r.json.code + " msg=" + $r.json.message)
    }
}
$r = Invoke-Api 'Post' "$base/api/auth/login" '{"username":"keeper","password":"123456"}' $null
Check "keeper permissions = 11" ($r.json.data.permissions.Count -eq 11) ("count=" + $r.json.data.permissions.Count)
$r = Invoke-Api 'Post' "$base/api/auth/login" '{"username":"picker","password":"123456"}' $null
Check "picker permissions = 6" ($r.json.data.permissions.Count -eq 6) ("count=" + $r.json.data.permissions.Count)

Write-Host ""
Write-Host "=== 5. Token handling ==="
$r = Invoke-Api 'Get' "$base/api/auth/me" $null $null
Check "missing token -> 401" ($r.json.code -eq 401) ("code=" + $r.json.code + " msg=" + $r.json.message)

$r = Invoke-Api 'Get' "$base/api/auth/me" $null @{ Authorization = 'Bearer forged.token.value' }
Check "forged token -> 401" ($r.json.code -eq 401) ("code=" + $r.json.code + " msg=" + $r.json.message)

$r = Invoke-Api 'Get' "$base/api/auth/me" $null @{ Authorization = ("Bearer " + $tokens['admin']) }
Check "valid token accepted" ($r.json.data.username -eq 'admin') ("username=" + $r.json.data.username)

$r = Invoke-Api 'Get' "$base/api/auth/me" $null @{ Authorization = $tokens['admin'] }
Check "token without Bearer prefix" ($r.json.data.username -eq 'admin') ("username=" + $r.json.data.username)

Write-Host ""
Write-Host "=== 6. User endpoints ==="
# 用户接口需要 system:view 权限，只有管理员拥有，因此必须带管理员令牌
$adminAuth = @{ Authorization = ("Bearer " + $tokens['admin']) }
$r = Invoke-Api 'Get' "$base/api/users" $null $adminAuth
Check "user list returned" ($r.json.data.Count -ge 3) ("count=" + $r.json.data.Count)
$leak = $false
foreach ($u in $r.json.data) { if ($null -ne $u.password) { $leak = $true } }
Check "no password in any row" (-not $leak) "checked all rows"

$r = Invoke-Api 'Get' "$base/api/users/1" $null $adminAuth
Check "user detail works" ($r.json.data.username -eq 'admin') ("username=" + $r.json.data.username)

$r = Invoke-Api 'Get' "$base/api/users/999999" $null $adminAuth
Check "missing user handled" ($null -eq $r.json.data) ("data=" + $r.json.data)

# 无令牌与越权都必须被拦下：这是本轮补齐的访问控制
$r = Invoke-Api 'Get' "$base/api/users" $null $null
Check "user list without token rejected (401)" ($r.status -eq 401) ("http=" + $r.status)
$r = Invoke-Api 'Get' "$base/api/users" $null @{ Authorization = ("Bearer " + $tokens['picker']) }
Check "user list as picker rejected (403)" ($r.status -eq 403) ("http=" + $r.status)

# 拣货员访问报表也需要 report:view，而 PICKER 没有该权限
$r = Invoke-Api 'Get' "$base/api/report/dashboard" $null @{ Authorization = ("Bearer " + $tokens['picker']) }
Check "report as picker rejected (403)" ($r.status -eq 403) ("http=" + $r.status)

# 拣货员拥有的接口应当放行
$r = Invoke-Api 'Get' "$base/api/locations?warehouseId=1&limit=5" $null @{ Authorization = ("Bearer " + $tokens['picker']) }
Check "picker keeps own permission (locations 200)" ($r.status -eq 200) ("http=" + $r.status)

Write-Host ""
Write-Host "=== 7. Stop application ==="
if (-not $proc.HasExited) {
    $proc.Kill()
    try { $proc.WaitForExit(10000) | Out-Null } catch { }
    Write-Host "  stopped"
}
Start-Sleep -Seconds 1
$log = ''
try { $log = $outTask.Result } catch { }
$errorLines = ($log -split "`r?`n") | Where-Object { $_ -match ' ERROR ' }
Check "no ERROR in application log" ($errorLines.Count -eq 0) ("error lines = " + $errorLines.Count)
if ($errorLines.Count -gt 0) { $errorLines | Select-Object -First 5 | ForEach-Object { Write-Host ("      " + $_.Trim()) } }

Write-Host ""
Write-Host ("=== RESULT: {0} passed, {1} failed ===" -f $pass, $fail)
if ($fail -eq 0) { Write-Host "EXTENDED VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "EXTENDED VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
