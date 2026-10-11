# 重启后环境自检脚本
# 用法：powershell -ExecutionPolicy Bypass -File scripts\verify-environment.ps1
# 或在 PowerShell 7 中：pwsh -File scripts\verify-environment.ps1

$ErrorActionPreference = 'Continue'
$fail = 0

function Section($title) {
    Write-Host ""
    Write-Host ("=" * 66) -ForegroundColor DarkGray
    Write-Host $title -ForegroundColor Cyan
    Write-Host ("=" * 66) -ForegroundColor DarkGray
}

function Check($name, $scriptBlock, $expectHint) {
    try {
        $out = & $scriptBlock 2>&1 | Out-String
        $out = ($out -split "`r?`n" | Where-Object { $_.Trim() -ne "" } | Select-Object -First 2) -join " / "
        if ($out.Trim() -eq "") { $out = "(无输出)" }
        Write-Host ("  [OK]   {0,-26} {1}" -f $name, $out.Trim()) -ForegroundColor Green
    } catch {
        Write-Host ("  [FAIL] {0,-26} {1}" -f $name, $_.Exception.Message) -ForegroundColor Red
        if ($expectHint) { Write-Host ("         期望: " + $expectHint) -ForegroundColor Yellow }
        $script:fail++
    }
}

Section "1. 语言与构建工具"
Check "JDK 8" { & 'D:\Dev\jdk8\bin\java.exe' -version } "openjdk version 1.8.0_xxx"
Check "Maven" { & 'D:\Dev\maven\bin\mvn.cmd' -v } "Apache Maven 3.9.x"
Check "Node.js" { node -v } "v24.x"
Check "npm" { npm.cmd -v } "10.x / 11.x"
Check "Windows PowerShell" { $PSVersionTable.PSVersion.ToString() } "5.1.x"
if (Test-Path 'C:\Program Files\PowerShell\7\pwsh.exe') {
    Check "PowerShell 7" { & 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -Command '$PSVersionTable.PSVersion.ToString()' } "7.6.x"
} else {
    Write-Host "  [SKIP] PowerShell 7              未安装（如需安装：见环境审计报告）" -ForegroundColor Yellow
}

Section "2. 环境变量（用户级）"
foreach ($v in 'JAVA_HOME', 'MAVEN_HOME', 'WORKBUDDY_ELECTRON_BIN') {
    $val = [Environment]::GetEnvironmentVariable($v, 'User')
    if ($val) {
        Write-Host ("  [OK]   {0,-26} {1}" -f $v, $val) -ForegroundColor Green
    } else {
        Write-Host ("  [FAIL] {0,-26} 未设置" -f $v) -ForegroundColor Red
        $fail++
    }
}

Section "3. MySQL 数据库"
$running = (tasklist /FI "IMAGENAME eq mysqld.exe" 2>$null) -match 'mysqld.exe'
if ($running) {
    Write-Host "  [OK]   mysqld 进程              运行中" -ForegroundColor Green
    Check "版本与字符集" {
        & 'D:\Dev\mysql8\bin\mysql.exe' -u wms -pWms@2026App --port=3306 --protocol=TCP `
            -e "SELECT VERSION(), @@character_set_server;" wms 2>$null
    } "8.0.27 / utf8mb4"
    Check "wms 库表数量" {
        & 'D:\Dev\mysql8\bin\mysql.exe' -u wms -pWms@2026App --port=3306 --protocol=TCP `
            -e "SELECT COUNT(*) AS tables_in_wms FROM information_schema.tables WHERE table_schema='wms';" 2>$null
    } "当前应为 0（尚未建表）"
} else {
    Write-Host "  [FAIL] mysqld 未运行 —— 请先执行 scripts\start-mysql.ps1" -ForegroundColor Red
    Write-Host "         启动命令: & '.\05-部署与运维\scripts\start-mysql.ps1'" -ForegroundColor Yellow
    $fail++
}

Section "4. 项目文件完整性"
$must = @(
    'Readme.md',
    '01-文档\00-项目管理\毕业设计总体计划与实施方案.md',
    '01-文档\03-进度检查\进度看板.md',
    '01-文档\99-格式模板\论文撰写规范-要点摘录.md',
    '01-文档\06-文献与综述\参考文献清单.md',
    '03-源码\algorithm\experiments.py',
    '03-源码\algorithm\wmsopt\model.py',
    '03-源码\algorithm\wmsopt\slotting.py',
    '03-源码\algorithm\wmsopt\picking.py',
    '03-源码\backend\pom.xml',
    '04-测试与实验\03-算法对比实验\实验结果汇总.md',
    '05-部署与运维\数据库与开发环境速查表.md'
)
# 项目根目录 = 本脚本所在目录（scripts）的上两级 = 05-部署与运维 的上二级
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
foreach ($rel in $must) {
    $p = Join-Path $root $rel
    if (Test-Path $p) {
        Write-Host ("  [OK]   {0}" -f $rel) -ForegroundColor Green
    } else {
        Write-Host ("  [FAIL] {0}  缺失" -f $rel) -ForegroundColor Red
        $fail++
    }
}

Section "5. 算法内核自检"
Push-Location (Join-Path $root '03-源码\algorithm')
try {
    $py = 'C:\Users\Administrator\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
    $out = & $py 'tests_selfcheck.py' 2>&1 | Out-String
    if ($out -match '全部自检通过') {
        Write-Host "  [OK]   算法自检                  全部通过" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] 算法自检未通过，输出尾部：" -ForegroundColor Red
        ($out -split "`r?`n" | Select-Object -Last 8) | ForEach-Object { Write-Host "         $_" }
        $fail++
    }
} finally {
    Pop-Location
}

Section "自检结果"
if ($fail -eq 0) {
    Write-Host "  全部通过 ✅ 环境可用，可以继续开发。" -ForegroundColor Green
} else {
    Write-Host ("  有 {0} 项未通过，请按上面的提示处理。" -f $fail) -ForegroundColor Yellow
    Write-Host "  最常见原因：重启后忘了启动 MySQL → 跑 scripts\start-mysql.ps1" -ForegroundColor Yellow
}
Write-Host ""
