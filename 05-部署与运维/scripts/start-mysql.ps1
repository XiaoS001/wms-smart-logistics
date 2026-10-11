# 启动 MySQL 实例（免安装版）
# 设计说明：不使用 -RedirectStandardOutput/-RedirectStandardError（那会让 Start-Process 挂在管道上），
#          改为让 mysqld 以独立进程运行，再用 TCP 端口探测判断是否就绪。

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
    Write-Host "MySQL 已在运行（端口 3306 可连接），无需启动。" -ForegroundColor Yellow
} else {
    Write-Host "正在启动 MySQL ..." -ForegroundColor Cyan
    Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden

    $ok = $false
    for ($i = 1; $i -le 20; $i++) {
        Start-Sleep -Milliseconds 1500
        if (Test-Port '127.0.0.1' 3306) { $ok = $true; break }
    }
    if ($ok) {
        Write-Host ("MySQL 已就绪（端口 3306，耗时约 {0} 秒）。" -f [math]::Round($i * 1.5, 1)) -ForegroundColor Green
    } else {
        Write-Host "启动失败或超时，请查看日志：" -ForegroundColor Red
        Write-Host "  D:\Dev\mysql8\data\mysql-error.log"
    }
}

Write-Host "`n连接自检：" -ForegroundColor Cyan
& 'D:\Dev\mysql8\bin\mysql.exe' -u wms -pWms@2026App --port=3306 --protocol=TCP `
    -e "SELECT VERSION() AS 版本, @@port AS 端口, @@character_set_server AS 字符集;" wms 2>$null
