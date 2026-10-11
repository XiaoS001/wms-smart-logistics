# 依次运行全部既有验证脚本，汇总结果。
#
# 各脚本自行负责启动与停止后端、清理数据，因此这里按顺序串行执行，
# 避免多个后端实例争抢 8080 端口。
#
# 用法：pwsh -File run-all-verifications.ps1
# 可选：只跑名称匹配的脚本  pwsh -File run-all-verifications.ps1 -Filter auth

param(
    [string]$Filter = ''
)

$ErrorActionPreference = 'Continue'
$root = 'D:\DeepSeekXM\XM01'
$scriptsDir = Join-Path $root '05-部署与运维\scripts'

# 排除不是验证类的脚本
$skip = @('autostart-mysql.ps1', 'start-mysql.ps1', 'stop-mysql.ps1', 'status-mysql.ps1',
          'install-idea.ps1', 'env.cmd', 'mvn.cmd', 'verify-environment.ps1')

$scripts = Get-ChildItem $scriptsDir -Filter 'verify-*.ps1' -File |
    Where-Object { $skip -notcontains $_.Name } |
    Sort-Object Name
if ($Filter) {
    $scripts = $scripts | Where-Object { $_.Name -like "*$Filter*" }
}

Write-Host ("=" * 74)
Write-Host (" 批量验证：共 {0} 个脚本" -f $scripts.Count)
Write-Host ("=" * 74)

$results = @()
$totalPass = 0
$totalFail = 0
$startAll = Get-Date

foreach ($s in $scripts) {
    Write-Host ""
    Write-Host ("-" * 74)
    Write-Host ("▶ " + $s.Name)
    Write-Host ("-" * 74)

    $t0 = Get-Date
    $out = & 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -ExecutionPolicy Bypass `
        -File $s.FullName 2>&1 | Out-String
    $code = $LASTEXITCODE
    $secs = [math]::Round(((Get-Date) - $t0).TotalSeconds, 1)

    # 从输出中提取通过/失败计数
    $p = 0; $f = 0
    $m = [regex]::Match($out, 'RESULT:\s*(\d+)\s*passed,\s*(\d+)\s*failed')
    if ($m.Success) { $p = [int]$m.Groups[1].Value; $f = [int]$m.Groups[2].Value }

    $totalPass += $p
    $totalFail += $f

    # 打印失败的检查行与最后的结论行
    $failLines = ($out -split "`r?`n") | Where-Object { $_ -match '\[FAIL\]' }
    if ($failLines) {
        foreach ($l in $failLines) { Write-Host ("  " + $l.Trim()) -ForegroundColor Red }
    }
    $verdict = ($out -split "`r?`n") | Where-Object { $_ -match 'PASSED|FAILED' } | Select-Object -Last 1
    if ($verdict) {
        $color = if ($verdict -match 'PASSED') { 'Green' } else { 'Red' }
        Write-Host ("  " + $verdict.Trim()) -ForegroundColor $color
    } else {
        Write-Host ("  (无结论行，退出码 {0})" -f $code) -ForegroundColor Yellow
    }

    $results += [pscustomobject]@{
        Script = $s.Name
        Passed = $p
        Failed = $f
        ExitCode = $code
        Seconds = $secs
    }
}

$totalSecs = [math]::Round(((Get-Date) - $startAll).TotalSeconds, 1)

Write-Host ""
Write-Host ("=" * 74)
Write-Host " 汇总"
Write-Host ("=" * 74)
$results | Format-Table -AutoSize | Out-String -Width 100 | Write-Host

Write-Host ("脚本数        : {0}" -f $results.Count)
Write-Host ("检查通过      : {0}" -f $totalPass) -ForegroundColor Green
if ($totalFail -gt 0) {
    Write-Host ("检查失败      : {0}" -f $totalFail) -ForegroundColor Red
} else {
    Write-Host "检查失败      : 0"
}
$failedScripts = $results | Where-Object { $_.Failed -gt 0 -or $_.ExitCode -ne 0 }
if ($failedScripts) {
    Write-Host "异常脚本      :" -ForegroundColor Red
    $failedScripts | ForEach-Object { Write-Host ("   " + $_.Script + " (failed=" + $_.Failed + ", exit=" + $_.ExitCode + ")") -ForegroundColor Red }
}
Write-Host ("总耗时        : {0} 秒" -f $totalSecs)

if ($totalFail -eq 0 -and -not $failedScripts) {
    Write-Host ""
    Write-Host "全部既有验证脚本通过" -ForegroundColor Green
    exit 0
} else {
    Write-Host ""
    Write-Host "存在失败项，见上方明细" -ForegroundColor Red
    exit 3
}
