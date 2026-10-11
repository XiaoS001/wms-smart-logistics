#!/usr/bin/env pwsh
# 前端构建脚本。
#
# 为什么需要它：
# 直接把 TEMP/TMP 指向工作区内目录，避免 esbuild 在系统临时目录创建中间文件
# 后因权限策略无法清理而中断构建。这样即使在受限环境下也能稳定打包。
#
# 用法：
#   pwsh -File build/tools/build-frontend.ps1            # 类型检查 + 构建
#   pwsh -File build/tools/build-frontend.ps1 -SkipTypeCheck

param(
    [switch]$SkipTypeCheck
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fe = Join-Path $root '03-源码\frontend'
$tmp = Join-Path $root 'build\esbuild-tmp'

if (-not (Test-Path $fe)) {
    Write-Error "前端目录不存在：$fe"
}

New-Item -ItemType Directory -Force -Path $tmp | Out-Null

# 关键：把 Node 的临时目录指向工作区内，esbuild 的中间文件随之落在可写位置
$env:TMP = $tmp
$env:TEMP = $tmp
$env:TMPDIR = $tmp

Push-Location $fe
try {
    if (-not $SkipTypeCheck) {
        Write-Host '=== 类型检查（vue-tsc）===' -ForegroundColor Cyan
        & npm run typecheck
        if ($LASTEXITCODE -ne 0) {
            Write-Host '类型检查未通过，已中止构建。' -ForegroundColor Red
            exit 1
        }
    }

    Write-Host '=== 生产构建（vite build）===' -ForegroundColor Cyan
    & npm run build:only
    if ($LASTEXITCODE -ne 0) {
        Write-Host '构建失败。' -ForegroundColor Red
        exit 1
    }

    $dist = Join-Path $fe 'dist'
    if (Test-Path $dist) {
        $files = Get-ChildItem $dist -Recurse -File
        $size = ($files | Measure-Object -Property Length -Sum).Sum
        Write-Host ''
        Write-Host ('构建成功：{0} 个文件，共 {1:N2} MB' -f $files.Count, ($size / 1MB)) -ForegroundColor Green
        Write-Host "产物目录：$dist"
    }
} finally {
    Pop-Location
}
