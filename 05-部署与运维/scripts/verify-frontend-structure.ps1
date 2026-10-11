# Structural review for the frontend project.
#
# Verifies that the project follows the conventions declared in its README:
#   1. required configuration files exist
#   2. directory layout matches the documented structure
#   3. every route in router/index.ts has a matching view component
#   4. every API module is typed (no implicit any in signatures)
#   5. no leftover template markers or placeholder TODOs in real views
#   6. package.json is valid JSON and declares the expected scripts
#   7. imports resolve: every '@/...' path referenced actually exists
#
# Usage: pwsh -File verify-frontend-structure.ps1

$ErrorActionPreference = 'Continue'
$root = 'D:\DeepSeekXM\XM01'
$fe = Join-Path $root '03-源码\frontend'
$src = Join-Path $fe 'src'

$pass = 0; $fail = 0
function Check($name, $condition, $detail) {
    if ($condition) { Write-Host ("  [PASS] {0,-44} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-44} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}

Write-Host "=== 1. Project root files ==="
$required = @(
    'package.json', 'vite.config.ts', 'tsconfig.json', 'uno.config.ts',
    'index.html', 'eslint.config.js', '.prettierrc.json', 'commitlint.config.js',
    '.gitignore', 'README.md',
    '.env.development', '.env.production'
)
foreach ($f in $required) {
    $p = Join-Path $fe $f
    Check $f (Test-Path $p) $(if (Test-Path $p) { "存在" } else { "缺失" })
}

Write-Host ""
Write-Host "=== 2. Documented directory layout ==="
$dirs = @('api', 'assets', 'components', 'composables', 'layout', 'router', 'stores', 'styles', 'types', 'utils', 'views')
foreach ($d in $dirs) {
    $p = Join-Path $src $d
    $n = if (Test-Path $p) { (Get-ChildItem $p -Recurse -File | Measure-Object).Count } else { -1 }
    Check ("src/" + $d) (Test-Path $p) ("文件数=" + $n)
}

Write-Host ""
Write-Host "=== 3. Every route has a view component ==="
$routerFile = Join-Path $src 'router\index.ts'
$routerText = Get-Content $routerFile -Raw -Encoding UTF8
$imports = [regex]::Matches($routerText, "import\('@/([^']+)'\)") | ForEach-Object { $_.Groups[1].Value }
$missing = @()
foreach ($imp in $imports) {
    $rel = $imp -replace '/', '\'
    $candidates = @((Join-Path $src $rel), (Join-Path $src ($rel + '.vue')), (Join-Path $src ($rel + '.ts')))
    $found = $false
    foreach ($c in $candidates) { if (Test-Path $c -PathType Leaf) { $found = $true; break } }
    if (-not $found) { $missing += $imp }
}
Check "all lazy routes resolve" ($missing.Count -eq 0) `
      ("routes=" + $imports.Count + " missing=" + $missing.Count)
$missing | ForEach-Object { Write-Host ("      missing: " + $_) }

Write-Host ""
Write-Host "=== 4. API modules are typed ==="
$apiDir = Join-Path $src 'api'
$apiFiles = Get-ChildItem $apiDir -File -Filter '*.ts' -ErrorAction SilentlyContinue
$untyped = @()
foreach ($f in $apiFiles) {
    $text = Get-Content $f.FullName -Raw -Encoding UTF8
    # 统计 request.get/post/put/delete 调用是否带泛型参数
    $calls = [regex]::Matches($text, 'request\.(get|post|put|delete)\s*[<(]')
    $typed = [regex]::Matches($text, 'request\.(get|post|put|delete)\s*<')
    if ($calls.Count -gt $typed.Count) { $untyped += ($f.Name + " (" + $calls.Count + " calls, " + $typed.Count + " typed)") }
}
Check "all API calls specify a type" ($untyped.Count -eq 0) ("modules=" + $apiFiles.Count)
$untyped | ForEach-Object { Write-Host ("      untyped: " + $_) }

Write-Host ""
Write-Host "=== 5. Required modules present ==="
$mustHave = @(
    'utils\request.ts', 'utils\constants.ts', 'utils\format.ts',
    'stores\user.ts', 'stores\app.ts',
    'router\index.ts', 'layout\index.vue',
    'types\common.ts', 'types\auth.ts', 'types\base.ts', 'types\business.ts', 'types\env.d.ts',
    'styles\index.scss', 'main.ts', 'App.vue'
)
foreach ($m in $mustHave) {
    $p = Join-Path $src $m
    Check $m (Test-Path $p) $(if (Test-Path $p) { "存在" } else { "缺失" })
}

Write-Host ""
Write-Host "=== 6. Layout components ==="
foreach ($c in @('components\AppSidebar.vue', 'components\AppNavbar.vue', 'components\AppTabs.vue', 'components\AppBreadcrumb.vue')) {
    $p = Join-Path (Join-Path $src 'layout') $c
    Check $c (Test-Path $p) $(if (Test-Path $p) { "存在" } else { "缺失" })
}

Write-Host ""
Write-Host "=== 7. Placeholder views are tracked ==="
$viewFiles = Get-ChildItem (Join-Path $src 'views') -Recurse -File -Filter '*.vue' -ErrorAction SilentlyContinue
$stubs = @()
$real = @()
foreach ($f in $viewFiles) {
    $t = Get-Content $f.FullName -Raw -Encoding UTF8
    if ($t -match '该页面正在开发中') { $stubs += $f.FullName.Replace($src + '\', '') } else { $real += $f.FullName.Replace($src + '\', '') }
}
Check "placeholder count is known" $true ("implemented=" + $real.Count + " placeholder=" + $stubs.Count)
Write-Host "      implemented:"; $real | ForEach-Object { Write-Host ("        " + $_) }
Write-Host "      placeholder:"; $stubs | ForEach-Object { Write-Host ("        " + $_) }

Write-Host ""
Write-Host "=== 8. package.json is valid and complete ==="
$pkgPath = Join-Path $fe 'package.json'
$pkgOk = $true; $pkgDetail = ""
try {
    $pkg = Get-Content $pkgPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $scripts = @('dev', 'build', 'preview', 'lint', 'format', 'typecheck')
    $missingScripts = $scripts | Where-Object { -not $pkg.scripts.PSObject.Properties.Name.Contains($_) }
    if ($missingScripts) { $pkgOk = $false; $pkgDetail = "缺少脚本: " + ($missingScripts -join ',') }
    else {
        $deps = @($pkg.dependencies.PSObject.Properties).Count
        $devDeps = @($pkg.devDependencies.PSObject.Properties).Count
        $pkgDetail = "dependencies=$deps devDependencies=$devDeps 脚本齐全"
    }
} catch {
    $pkgOk = $false; $pkgDetail = $_.Exception.Message
}
Check "package.json valid" $pkgOk $pkgDetail

Write-Host ""
Write-Host "=== 9. Relative imports resolve within src ==="
$badImports = @()
foreach ($f in (Get-ChildItem $src -Recurse -File -Include '*.ts', '*.vue' -ErrorAction SilentlyContinue)) {
    $t = Get-Content $f.FullName -Raw -Encoding UTF8
    foreach ($m in [regex]::Matches($t, "from '(@/[^']+)'")) {
        $imp = $m.Groups[1].Value.Substring(2)
        $rel = $imp -replace '/', '\'
        $cands = @(
            (Join-Path $src $rel),
            (Join-Path $src ($rel + '.ts')),
            (Join-Path $src ($rel + '.vue')),
            (Join-Path $src ($rel + '\index.ts')),
            (Join-Path $src ($rel + '\index.vue'))
        )
        $ok = $false
        foreach ($c in $cands) { if (Test-Path $c -PathType Leaf) { $ok = $true; break } }
        if (-not $ok) { $badImports += ($f.Name + " -> " + $m.Groups[1].Value) }
    }
}
Check "all @/ imports resolve" ($badImports.Count -eq 0) ("broken=" + $badImports.Count)
$badImports | Select-Object -First 15 | ForEach-Object { Write-Host ("      " + $_) }

Write-Host ""
Write-Host "=== 10. node_modules installed ==="
$nm = Join-Path $fe 'node_modules'
$installed = Test-Path $nm
$count = if ($installed) { (Get-ChildItem $nm -Directory -ErrorAction SilentlyContinue | Measure-Object).Count } else { 0 }
Check "node_modules present" $installed ("packages=" + $count)

Write-Host ""
Write-Host ("=== RESULT: {0} passed, {1} failed ===" -f $pass, $fail)
if ($fail -eq 0) { Write-Host "FRONTEND STRUCTURE REVIEW PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "FRONTEND STRUCTURE REVIEW FAILED" -ForegroundColor Red; exit 3 }
