# 查看 MySQL 实例状态
$proc = tasklist /FI "IMAGENAME eq mysqld.exe" 2>$null

if ($proc -match 'mysqld.exe') {
    Write-Host "mysqld 运行中：" -ForegroundColor Green
    $proc | Select-String 'mysqld.exe'
    Write-Host "`n连接与版本信息：" -ForegroundColor Cyan
    & 'D:\Dev\mysql8\bin\mysql.exe' -u wms -pWms@2026App --port=3306 --protocol=TCP `
        -e "SELECT VERSION() AS 版本, @@port AS 端口, @@character_set_server AS 字符集, @@max_connections AS 最大连接数;" wms 2>$null
    Write-Host "`nwms 库中的表：" -ForegroundColor Cyan
    & 'D:\Dev\mysql8\bin\mysql.exe' -u wms -pWms@2026App --port=3306 --protocol=TCP `
        -e "SHOW TABLES;" wms 2>$null
} else {
    Write-Host "mysqld 未运行。可用 start-mysql.ps1 启动。" -ForegroundColor Yellow
    Write-Host "错误日志：D:\Dev\mysql8\data\mysql-error.log"
}
