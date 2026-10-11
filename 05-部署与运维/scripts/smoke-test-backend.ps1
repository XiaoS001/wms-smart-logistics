# Build the backend, start it, smoke-test the health endpoint, then stop it.
# This is the end-to-end verification that the Java toolchain + MySQL work together.

$ErrorActionPreference = 'Continue'

$root    = 'D:\DeepSeekXM\XM01'
$backend = Join-Path $root '03-源码\backend'
$mvn     = 'D:\Dev\maven\bin\mvn.cmd'
$java    = 'D:\Dev\jdk8\bin\java.exe'
$jar     = Join-Path $backend 'target\wms-1.0.0.jar'
$logFile = Join-Path $env:TEMP 'wms-app.log'

$env:JAVA_HOME = 'D:\Dev\jdk8'

Write-Host "=== 1. Build (mvn package) ==="
Push-Location $backend
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$out = & $mvn -B -DskipTests package 2>&1 | Out-String
$code = $LASTEXITCODE
$sw.Stop()
Pop-Location
Write-Host ("exit={0}  elapsed={1:N1}s" -f $code, $sw.Elapsed.TotalSeconds)
if ($code -ne 0) {
    Write-Host "--- tail of build output ---"
    ($out -split "`r?`n" | Select-Object -Last 25) | ForEach-Object { Write-Host ("  | " + $_) }
    exit 1
}
($out -split "`r?`n" | Select-String -Pattern 'BUILD SUCCESS|Building jar|Total time') | ForEach-Object { Write-Host ("  " + $_.Line.Trim()) }

if (-not (Test-Path $jar)) { Write-Host "FAIL: jar not produced at $jar"; exit 2 }
Write-Host ("jar size: {0:N2} MB" -f ((Get-Item $jar).Length / 1MB))

Write-Host ""
Write-Host "=== 2. Start the application (profile=local) ==="
if (Test-Path $logFile) { Remove-Item $logFile -Force }
$proc = Start-Process -FilePath $java `
    -ArgumentList '-jar', $jar, '--spring.profiles.active=local' `
    -RedirectStandardOutput $logFile -RedirectStandardError ($logFile + '.err') `
    -PassThru -WindowStyle Hidden
Write-Host ("started pid={0}" -f $proc.Id)

Write-Host ""
Write-Host "=== 3. Poll the health endpoint ==="
$ok = $false
$body = $null
for ($i = 1; $i -le 40; $i++) {
    Start-Sleep -Seconds 3
    if ($proc.HasExited) { Write-Host ("process exited early, code={0}" -f $proc.ExitCode); break }
    try {
        $resp = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/health' -TimeoutSec 5 -UseBasicParsing
        if ($resp.StatusCode -eq 200) { $ok = $true; $body = $resp.Content; break }
    } catch { }
}
Write-Host ("health reachable: {0}  (waited {1}s)" -f $ok, ($i * 3))

Write-Host ""
Write-Host "=== 4. Response body ==="
if ($ok) {
    Write-Host $body
} else {
    Write-Host "--- application log tail ---"
    if (Test-Path $logFile) {
        (Get-Content $logFile -Tail 30) | ForEach-Object { Write-Host ("  | " + $_) }
    }
    if (Test-Path ($logFile + '.err')) {
        Write-Host "--- stderr tail ---"
        (Get-Content ($logFile + '.err') -Tail 15) | ForEach-Object { Write-Host ("  | " + $_) }
    }
}

Write-Host ""
Write-Host "=== 5. Stop the application ==="
if (-not $proc.HasExited) {
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Host "stopped"
} else {
    Write-Host "already exited"
}

Write-Host ""
if ($ok) { Write-Host "SMOKE TEST PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "SMOKE TEST FAILED" -ForegroundColor Red; exit 3 }
