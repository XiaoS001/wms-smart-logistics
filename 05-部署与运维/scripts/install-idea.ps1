# Reinstall IntelliJ IDEA Community Edition to the intended path.
#
# What went wrong the first time:
#   Start-Process -ArgumentList "/S /D=... /NCRC" passes the whole string as ONE
#   argument and quotes it, so the installer received a quoted /D value and
#   treated " NCRC" as part of the directory name. Result:
#     D:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.3 NCRC
#
# Correct approach:
#   * pass the switches as a STRING ARRAY so each switch is a separate argument
#   * /D must be the LAST argument and must not be quoted
#   * drop /NCRC (it adds nothing here) to keep the command line minimal

$ErrorActionPreference = 'Continue'

$installer = 'D:\XIAZAI\idea-2025.3.exe'
$wrongDir  = 'D:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.3 NCRC'
$target    = 'D:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.3'

Write-Host "=== 1. Uninstall the wrongly located copy ==="
$wrongUninstaller = Join-Path $wrongDir 'bin\Uninstall.exe'
if (Test-Path $wrongUninstaller) {
    Write-Host ("uninstalling: {0}" -f $wrongUninstaller)
    try {
        $u = Start-Process -FilePath $wrongUninstaller -ArgumentList '/S' -Verb RunAs -Wait -PassThru -ErrorAction Stop
        Write-Host ("uninstaller exit code: {0}" -f $u.ExitCode)
    } catch {
        Write-Host ("uninstall failed: {0}" -f $_.Exception.Message) -ForegroundColor Yellow
    }
    for ($i = 1; $i -le 20; $i++) {
        Start-Sleep -Seconds 3
        if (-not (Test-Path $wrongUninstaller)) { break }
    }
    Write-Host ("wrong dir still present: {0}" -f (Test-Path $wrongDir))
    if (Test-Path $wrongDir) {
        Write-Host "removing leftover directory"
        Remove-Item $wrongDir -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host ("removed: {0}" -f (-not (Test-Path $wrongDir)))
    }
} else {
    Write-Host "no uninstaller found - cleaning the directory directly"
    if (Test-Path $wrongDir) {
        Remove-Item $wrongDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    Write-Host ("wrong dir present after cleanup: {0}" -f (Test-Path $wrongDir))
}

Write-Host ""
Write-Host "=== 2. Silent install to the intended path (approve the UAC prompt) ==="
Write-Host ("target: {0}" -f $target)
# Argument array: each switch is its own element; /D last and unquoted.
$argArray = @('/S', "/D=$target")
try {
    $p = Start-Process -FilePath $installer -ArgumentList $argArray -Verb RunAs -Wait -PassThru -ErrorAction Stop
    Write-Host ("installer exit code: {0}" -f $p.ExitCode)
} catch {
    Write-Host ("launch failed: {0}" -f $_.Exception.Message) -ForegroundColor Red
    exit 2
}

Write-Host ""
Write-Host "=== 3. Wait for installation to finish ==="
$exe = Join-Path $target 'bin\idea64.exe'
$ok = $false
for ($i = 1; $i -le 80; $i++) {
    Start-Sleep -Seconds 3
    if (Test-Path $exe) { $ok = $true; break }
    if ($i % 10 -eq 0) { Write-Host ("  ... still installing ({0}s)" -f ($i * 3)) }
}

Write-Host ""
Write-Host "=== 4. Result ==="
Write-Host ("target dir exists : {0}" -f (Test-Path $target))
Write-Host ("idea64.exe exists : {0}" -f $ok)
if (Test-Path $target) {
    $sum = (Get-ChildItem $target -Recurse -File -ErrorAction SilentlyContinue |
            Measure-Object -Property Length -Sum).Sum
    Write-Host ("installed size    : {0:N2} GB" -f ($sum / 1GB))
}
if ($ok) {
    $vi = (Get-Item $exe).VersionInfo
    Write-Host ("file version      : {0}" -f $vi.FileVersion)
    Write-Host ("product           : {0}" -f $vi.ProductName)
    Write-Host ""
    Write-Host "INSTALL OK" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "INSTALL NOT CONFIRMED" -ForegroundColor Yellow
    exit 3
}

Write-Host ""
Write-Host "=== 5. Registry entry ==="
Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*' -ErrorAction SilentlyContinue |
    Where-Object { $_.DisplayName -match 'IntelliJ' } |
    Select-Object DisplayName, DisplayVersion, InstallLocation | Format-List

Write-Host "=== 6. Start menu shortcut ==="
Get-ChildItem 'C:\ProgramData\Microsoft\Windows\Start Menu\Programs' -Recurse -Filter '*IntelliJ*' -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty FullName
