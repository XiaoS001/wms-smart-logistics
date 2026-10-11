# Start the local MySQL instance (portable / no-installer build).
#
# Implementation notes:
#   * No -RedirectStandardOutput / -RedirectStandardError here: those make
#     Start-Process wait on a pipe and the call may never return.
#   * Readiness is detected by probing TCP port 3306 instead of parsing logs.

$mysqld = 'D:\Dev\mysql8\bin\mysqld.exe'
$ini    = 'D:\Dev\mysql8\my.ini'

function Test-Port([string]$Computer, [int]$Port, [int]$TimeoutMs = 1500) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($Computer, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
        if ($ok) { $client.EndConnect($iar) }
        $client.Close()
        return $ok
    } catch {
        return $false
    }
}

if (Test-Port '127.0.0.1' 3306) {
    Write-Host "MySQL is already running on port 3306 - nothing to do." -ForegroundColor Yellow
} else {
    Write-Host "Starting MySQL ..." -ForegroundColor Cyan
    Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden

    $ok = $false
    for ($i = 1; $i -le 20; $i++) {
        Start-Sleep -Milliseconds 1500
        if (Test-Port '127.0.0.1' 3306) { $ok = $true; break }
    }
    if ($ok) {
        Write-Host ("MySQL is ready on port 3306 (about {0} s)." -f [math]::Round($i * 1.5, 1)) -ForegroundColor Green
    } else {
        Write-Host "MySQL did not become ready within 30 s." -ForegroundColor Red
        Write-Host "Check the server error log: D:\Dev\mysql8\data\mysql-error.log"
    }
}

Write-Host ""
Write-Host "Connection check:" -ForegroundColor Cyan
& 'D:\Dev\mysql8\bin\mysql.exe' -u wms -pWms@2026App --port=3306 --protocol=TCP `
    -e "SELECT VERSION() AS version, @@port AS port, @@character_set_server AS charset;" wms 2>$null
