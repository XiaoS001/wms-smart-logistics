# Stop the local MySQL instance.

$mysqladmin = 'D:\Dev\mysql8\bin\mysqladmin.exe'

if (-not ((tasklist /FI "IMAGENAME eq mysqld.exe" 2>$null) -match 'mysqld.exe')) {
    Write-Host "mysqld is not running." -ForegroundColor Yellow
    exit 0
}

Write-Host "Stopping MySQL ..." -ForegroundColor Cyan
& $mysqladmin -u root -pWms@2026Root --port=3306 --protocol=TCP shutdown 2>$null
Start-Sleep -Seconds 3

if ((tasklist /FI "IMAGENAME eq mysqld.exe" 2>$null) -match 'mysqld.exe') {
    Write-Host "Graceful shutdown did not complete; forcing the process to stop." -ForegroundColor Yellow
    Stop-Process -Name mysqld -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

if ((tasklist /FI "IMAGENAME eq mysqld.exe" 2>$null) -match 'mysqld.exe') {
    Write-Host "Failed to stop MySQL - please check the process manually." -ForegroundColor Red
} else {
    Write-Host "MySQL stopped." -ForegroundColor Green
}
