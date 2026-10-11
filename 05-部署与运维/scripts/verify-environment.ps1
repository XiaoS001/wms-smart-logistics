# Environment self-check for the WMS graduation project.
# Usage: powershell -ExecutionPolicy Bypass -File scripts\verify-environment.ps1
#        pwsh -File scripts\verify-environment.ps1
#
# This script is intentionally pure ASCII. The project uses Chinese folder
# names; they are resolved through build/paths.psd1 so that no non-ASCII byte
# ever appears in this file.

$ErrorActionPreference = 'Continue'
$fail = 0

# Project root = parent of this script's folder. Using $PSScriptRoot keeps the
# path correct no matter which directory the caller runs from.
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pathsFile = Join-Path $root 'build\paths.psd1'

if (-not (Test-Path $pathsFile)) {
    Write-Host "FATAL: build\paths.psd1 not found under $root" -ForegroundColor Red
    exit 2
}
# The path table is loaded by dot-sourcing, NOT by Import-PowerShellDataFile.
# Reason: that cmdlet rejects computed values (error "Cannot generate ... for a
# ScriptBlock evaluating dynamic expressions") in PowerShell 5.1 and 7 alike,
# and build/paths.psd1 uses ASCII escapes such as [char]0x6E90 for the Chinese
# folder names.
$PathMap = & {
    $raw = Get-Content -LiteralPath $pathsFile -Raw
    Invoke-Expression $raw
}

# Guard: this table is used in several sections. It must stay a hashtable.
# (An earlier version reused the variable name for a loop and silently
#  overwrote it, because PowerShell variable names are case-insensitive.)
if ($PathMap -isnot [hashtable]) {
    Write-Host "FATAL: path table was overwritten - expected a hashtable." -ForegroundColor Red
    exit 2
}

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
        if ($out.Trim() -eq "") { $out = "(no output)" }
        Write-Host ("  [OK]   {0,-26} {1}" -f $name, $out.Trim()) -ForegroundColor Green
    } catch {
        Write-Host ("  [FAIL] {0,-26} {1}" -f $name, $_.Exception.Message) -ForegroundColor Red
        if ($expectHint) { Write-Host ("         expected: " + $expectHint) -ForegroundColor Yellow }
        $script:fail++
    }
}

Section "1. Languages and build tools"
Check "JDK 8" { & 'D:\Dev\jdk8\bin\java.exe' -version } "openjdk version 1.8.0_xxx"
Check "Maven" { & 'D:\Dev\maven\bin\mvn.cmd' -v } "Apache Maven 3.9.x"
Check "Node.js" { node -v } "v24.x"
Check "npm" { npm.cmd -v } "10.x / 11.x"
Check "Windows PowerShell" { $PSVersionTable.PSVersion.ToString() } "5.1.x"
if (Test-Path 'C:\Program Files\PowerShell\7\pwsh.exe') {
    Check "PowerShell 7" { & 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -Command '$PSVersionTable.PSVersion.ToString()' } "7.6.x"
} else {
    Write-Host "  [SKIP] PowerShell 7               not installed" -ForegroundColor Yellow
}

Section "2. User-level environment variables"
foreach ($v in 'JAVA_HOME', 'MAVEN_HOME', 'WORKBUDDY_ELECTRON_BIN') {
    $val = [Environment]::GetEnvironmentVariable($v, 'User')
    if ($val) {
        Write-Host ("  [OK]   {0,-26} {1}" -f $v, $val) -ForegroundColor Green
    } else {
        Write-Host ("  [FAIL] {0,-26} not set" -f $v) -ForegroundColor Red
        $fail++
    }
}

Section "3. MySQL database"
$running = (tasklist /FI "IMAGENAME eq mysqld.exe" 2>$null) -match 'mysqld.exe'
if ($running) {
    Write-Host "  [OK]   mysqld process            running" -ForegroundColor Green
    Check "version and charset" {
        & 'D:\Dev\mysql8\bin\mysql.exe' -u wms -pWms@2026App --port=3306 --protocol=TCP `
            -e "SELECT VERSION(), @@character_set_server;" wms 2>$null
    } "8.0.27 / utf8mb4"
    Check "table count in wms" {
        & 'D:\Dev\mysql8\bin\mysql.exe' -u wms -pWms@2026App --port=3306 --protocol=TCP `
            -e "SELECT COUNT(*) AS tables_in_wms FROM information_schema.tables WHERE table_schema='wms';" 2>$null
    } "17 after schema import"
} else {
    Write-Host "  [FAIL] mysqld is not running - start it first." -ForegroundColor Red
    Write-Host "         run: scripts\start-mysql.ps1" -ForegroundColor Yellow
    $fail++
}

Section "4. Project file integrity"
$must = @(
    'Readme.md',
    $PathMap.ProjectPlan,
    $PathMap.Board,
    $PathMap.SpecSummary,
    $PathMap.References,
    $PathMap.ExperimentPy,
    $PathMap.ModelPy,
    $PathMap.SlottingPy,
    $PathMap.PickingPy,
    $PathMap.BackendPom,
    $PathMap.ExpSummary,
    $PathMap.SchemaSql
)
foreach ($rel in $must) {
    $fullPath = Join-Path $root $rel
    if (Test-Path $fullPath) {
        Write-Host ("  [OK]   {0}" -f $rel) -ForegroundColor Green
    } else {
        Write-Host ("  [FAIL] {0}  MISSING" -f $rel) -ForegroundColor Red
        $fail++
    }
}

Section "5. Algorithm kernel self-check"
Push-Location (Join-Path $root $PathMap.AlgorithmDir)
try {
    $py = 'C:\Users\Administrator\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
    $out = & $py 'tests_selfcheck.py' 2>&1 | Out-String
    if ($out -match 'all passed|PASS' -and $out -notmatch 'FAIL') {
        Write-Host "  [OK]   algorithm self-check      all passed" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] algorithm self-check failed, tail of output:" -ForegroundColor Red
        ($out -split "`r?`n" | Select-Object -Last 8) | ForEach-Object { Write-Host "         $_" }
        $fail++
    }
} finally {
    Pop-Location
}

Section "Result"
if ($fail -eq 0) {
    Write-Host "  All checks passed. Environment is ready." -ForegroundColor Green
} else {
    Write-Host ("  {0} check(s) failed - see the hints above." -f $fail) -ForegroundColor Yellow
    Write-Host "  Most common cause: MySQL was not started after a reboot" -ForegroundColor Yellow
    Write-Host "  -> run scripts\start-mysql.ps1"
}
Write-Host ""
