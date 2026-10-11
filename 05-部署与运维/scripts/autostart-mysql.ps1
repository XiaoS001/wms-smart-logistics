# MySQL 登录自启动脚本（由启动文件夹的快捷方式调用）
# 设计要点：
#   1) 幂等——已在运行则直接退出，不会重复启动
#   2) 静默——启动过程不弹窗，日志写入 D:\Dev\mysql8\autostart.log
#   3) 不依赖环境变量——所有路径写死，避免登录时机环境未就绪

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

Write-Log "=== 自启动检查开始 ==="

if (Test-Port '127.0.0.1' 3306) {
    Write-Log "MySQL 已在运行，跳过启动"
    exit 0
}

if (-not (Test-Path $mysqld)) {
    Write-Log "错误：未找到 mysqld.exe（$mysqld）"
    exit 1
}

Write-Log "正在启动 mysqld ..."
Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden

for ($i = 1; $i -le 30; $i++) {
    Start-Sleep -Milliseconds 1500
    if (Test-Port '127.0.0.1' 3306) {
        Write-Log ("启动成功，耗时约 {0} 秒" -f [math]::Round($i * 1.5, 1))
        exit 0
    }
}

Write-Log "启动超时（45 秒内端口 3306 未就绪），请检查 D:\Dev\mysql8\data\mysql-error.log"
exit 1
