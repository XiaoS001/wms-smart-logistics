# Show the status of the local MySQL instance.

$proc = tasklist /FI "IMAGENAME eq mysqld.exe" 2>$null

if ($proc -match 'mysqld.exe') {
    Write-Host "mysqld is running:" -ForegroundColor Green
    $proc | Select-String 'mysqld.exe'

    Write-Host ""
    Write-Host "Version and connection info:" -ForegroundColor Cyan
    & 'D:\Dev\mysql8\bin\mysql.exe' -u wms -pWms@2026App --port=3306 --protocol=TCP `
        -e "SELECT VERSION() AS version, @@port AS port, @@character_set_server AS charset, @@max_connections AS max_connections;" wms 2>$null

    Write-Host ""
    Write-Host "Tables in database wms:" -ForegroundColor Cyan
    & 'D:\Dev\mysql8\bin\mysql.exe' -u wms -pWms@2026App --port=3306 --protocol=TCP `
        -e "SHOW TABLES;" wms 2>$null
} else {
    Write-Host "mysqld is not running. Start it with: scripts\start-mysql.ps1" -ForegroundColor Yellow
    Write-Host "Error log: D:\Dev\mysql8\data\mysql-error.log"
}
