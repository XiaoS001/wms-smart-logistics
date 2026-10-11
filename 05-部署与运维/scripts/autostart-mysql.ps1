# MySQL sign-in autostart helper.
# Invoked by the shortcut placed in the user's Startup folder.
#
# Design notes:
#   1) Idempotent - exits immediately when the server is already listening.
#   2) Silent     - runs hidden at sign-in; all output goes to a log file.
#   3) No environment variables - every path is absolute, because at sign-in
#      time the user environment may not be fully applied yet.

$mysqld = 'D:\Dev\mysql8\bin\mysqld.exe'
$ini    = 'D:\Dev\mysql8\my.ini'
$log    = 'D:\Dev\mysql8\autostart.log'

function Write-Log([string]$msg) {
    $line = "{0}  {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $msg
    Add-Content -Path $log -Value $line -Encoding UTF8
}

function Test-Port([string]$Computer, [int]$Port, [int]$TimeoutMs = 1500) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($Computer, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
        if ($ok) { $client.EndConnect($iar) }
        $client.Close()
        return $ok
    } catch { return $false }
}

Write-Log "=== autostart check started ==="

if (Test-Port '127.0.0.1' 3306) {
    Write-Log "MySQL already running - skipped"
    exit 0
}

if (-not (Test-Path $mysqld)) {
    Write-Log "ERROR: mysqld.exe not found at $mysqld"
    exit 1
}

Write-Log "Starting mysqld ..."
Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden

for ($i = 1; $i -le 30; $i++) {
    Start-Sleep -Milliseconds 1500
    if (Test-Port '127.0.0.1' 3306) {
        Write-Log ("Started successfully in about {0} s" -f [math]::Round($i * 1.5, 1))
        exit 0
    }
}

Write-Log "TIMEOUT: port 3306 not ready within 45 s; check D:\Dev\mysql8\data\mysql-error.log"
exit 1
