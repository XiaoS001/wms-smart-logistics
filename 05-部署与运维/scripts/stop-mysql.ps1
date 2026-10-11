# 停止 MySQL 实例（免安装版）
$mysqladmin = 'D:\Dev\mysql8\bin\mysqladmin.exe'

if (-not ((tasklist /FI "IMAGENAME eq mysqld.exe" 2>$null) -match 'mysqld.exe')) {
    Write-Host "mysqld 未在运行。" -ForegroundColor Yellow
    exit 0
}

Write-Host "正在停止 MySQL ..." -ForegroundColor Cyan
& $mysqladmin -u root -pWms@2026Root --port=3306 --protocol=TCP shutdown 2>$null
Start-Sleep -Seconds 3

if ((tasklist /FI "IMAGENAME eq mysqld.exe" 2>$null) -match 'mysqld.exe') {
    Write-Host "优雅关闭未成功，强制结束进程。" -ForegroundColor Yellow
    Stop-Process -Name mysqld -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

if ((tasklist /FI "IMAGENAME eq mysqld.exe" 2>$null) -match 'mysqld.exe') {
    Write-Host "停止失败，请手工检查进程。" -ForegroundColor Red
} else {
    Write-Host "MySQL 已停止。" -ForegroundColor Green
}
