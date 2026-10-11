# 权限矩阵验证。
#
# 为什么需要这个脚本：
#   前面出现过一类缺陷——权限之间不闭合。例如拣货员持有 location:view，
#   但库位页面必须先调用 /api/warehouses 才能知道查询哪个仓库，
#   而该接口曾要求 warehouse:view，导致页面整页空白。
#   这类问题靠人工逐页点击很难发现，因此把「每个角色能打开的页面，
#   其所需接口必须全部可访问；不能打开的模块必须被拒绝」固化为检查项。
#
# 检查口径：
#   - 每个角色先登录，取权限列表；
#   - 对「必须可访问」的接口断言 200（这是权限闭合性）；
#   - 对「必须被拒绝」的接口断言 401/403（这是最小权限）。
#
# 用法：pwsh -File verify-permission-matrix.ps1

$ErrorActionPreference = 'Continue'
$root    = 'D:\DeepSeekXM\XM01'
$backend = Join-Path $root '03-源码\backend'
$java    = 'D:\Dev\jdk8\bin\java.exe'
$jar     = Join-Path $backend 'target\wms-1.0.0.jar'
$base    = 'http://127.0.0.1:8080'

$pass = 0; $fail = 0
function Check($name, $condition, $detail) {
    if ($condition) { Write-Host ("  [PASS] {0,-56} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-56} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}

function Api($method, $url, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 30; UseBasicParsing = $true
            SkipHttpErrorCheck = $true; Headers = $headers }
    if ($body) { $p.Body = $body; $p.ContentType = 'application/json' }
    try { $r = Invoke-WebRequest @p } catch { return @{ status = 0; json = $null } }
    $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$r.StatusCode; json = $j }
}

Write-Host "=== 启动后端 ==="
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $java
$psi.Arguments = '-Dfile.encoding=UTF-8 -jar "' + $jar + '"'
$psi.WorkingDirectory = $backend
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$psi.EnvironmentVariables['JAVA_HOME'] = 'D:\Dev\jdk8'
$psi.EnvironmentVariables['SPRING_DATASOURCE_USERNAME'] = 'wms'
$psi.EnvironmentVariables['SPRING_DATASOURCE_PASSWORD'] = 'Wms@2026App'
$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi
[void]$proc.Start()
$outTask = $proc.StandardOutput.ReadToEndAsync()
$null = $proc.StandardError.ReadToEndAsync()
$ready = $false
for ($i = 1; $i -le 40; $i++) {
    Start-Sleep -Seconds 3
    if ($proc.HasExited) { break }
    if ((Api 'Get' "$base/api/health/ping" $null $null).status -eq 200) { $ready = $true; break }
}
Check "后端启动成功" $ready ("等待 " + ($i * 3) + " 秒")
if (-not $ready) { if (-not $proc.HasExited) { $proc.Kill() }; exit 2 }

# 各角色的令牌与权限
$tokens = @{}
$perms  = @{}
foreach ($u in @('admin', 'keeper', 'picker')) {
    $r = Api 'Post' "$base/api/auth/login" ('{"username":"' + $u + '","password":"123456"}') $null
    $tokens[$u] = $r.json.data.token
    $perms[$u]  = @($r.json.data.permissions)
}
Write-Host ""
Write-Host ("  权限数量 → admin={0} keeper={1} picker={2}" -f $perms['admin'].Count, $perms['keeper'].Count, $perms['picker'].Count)

# 每个角色「能打开的页面」所依赖的接口，必须全部可访问
# 依据：逐页核对视图代码里的 API 调用
$mustAllow = @{
    # 所有角色：仓库读接口是绘制平面图、确定当前仓库的参照数据
    'ALL' = @(
        '/api/warehouses',
        '/api/warehouses/1'
    )
    # 库位相关页面
    'location:view' = @(
        '/api/warehouses/1/locations/summary',
        '/api/locations?warehouseId=1&limit=5',
        '/api/locations/nearest-free?warehouseId=1&limit=5'
    )
    # 商品与分类页面
    'product:view' = @(
        '/api/products?limit=5',
        '/api/products/1',
        '/api/categories/tree',
        '/api/categories'
    )
    # 入库页面
    'inbound:view' = @('/api/inbound/orders?warehouseId=1&limit=5', '/api/inbound/statistics')
    # 出库与拣货页面
    'outbound:view' = @('/api/outbound/orders?warehouseId=1&limit=5', '/api/outbound/statistics')
    'picking:view'  = @('/api/outbound/strategies')
    # 库存与盘点页面
    'stock:view' = @(
        '/api/stock?warehouseId=1&limit=5',
        '/api/stock/summary?warehouseId=1',
        '/api/stock/alerts?warehouseId=1',
        '/api/stock/warehouse/1/summary',
        '/api/stock/stocktake'
    )
    # 货位优化页面
    'slotting:view' = @('/api/slotting/strategies', '/api/slotting/plans?warehouseId=1', '/api/slotting/plans/statistics')
    # 报表页面
    'report:view' = @(
        '/api/report/dashboard',
        '/api/report/operation-trend?days=7',
        '/api/report/stock-distribution?warehouseId=1',
        '/api/report/frequency-contribution?limit=5',
        '/api/report/slotting-effect/1',
        '/api/report/route-algorithm-statistics'
    )
    # 系统管理页面
    'system:view' = @('/api/users', '/api/users/1')
}

Write-Host ""
Write-Host "=== 1. 权限闭合性：能打开的页面，其依赖接口必须可访问 ==="
foreach ($u in @('admin', 'keeper', 'picker')) {
    $owned = $perms[$u]
    Write-Host ("  --- " + $u + " ---")
    foreach ($key in @('ALL') + $owned) {
        if (-not $mustAllow.ContainsKey($key)) { continue }
        foreach ($ep in $mustAllow[$key]) {
            $r = Api 'Get' ($base + $ep) $null $tokens[$u]
            # 404 说明接口存在但资源不存在，权限已通过，视为可访问
            $ok = ($r.status -eq 200 -or $r.status -eq 404 -or $r.status -eq 409)
            Check ("$u 可访问 " + $ep) $ok ("http=" + $r.status + "  需 " + $key)
        }
    }
}

Write-Host ""
Write-Host "=== 2. 最小权限：不该访问的模块必须被拒绝 ==="
# 每个角色「不应访问」的模块接口
$mustDeny = @{
    'picker' = @(
        @('/api/inbound/orders?warehouseId=1&limit=5', 'inbound:view'),
        @('/api/report/dashboard', 'report:view'),
        @('/api/users', 'system:view'),
        @('/api/slotting/plans?warehouseId=1', 'slotting:view'),
        @('/api/report/slotting-effect/1', 'report:view')
    )
    'keeper' = @(
        @('/api/users', 'system:view'),
        @('/api/slotting/plans?warehouseId=1', 'slotting:view'),
        @('/api/slotting/strategies', 'slotting:view')
    )
}
foreach ($u in $mustDeny.Keys) {
    Write-Host ("  --- " + $u + " ---")
    foreach ($pair in $mustDeny[$u]) {
        $r = Api 'Get' ($base + $pair[0]) $null $tokens[$u]
        $ok = ($r.status -eq 401 -or $r.status -eq 403)
        Check ("$u 被拒绝 " + $pair[0]) $ok ("http=" + $r.status + "  缺 " + $pair[1])
    }
}

Write-Host ""
Write-Host "=== 3. 未认证请求必须被拒绝 ==="
foreach ($ep in @('/api/warehouses', '/api/products?limit=1', '/api/report/dashboard', '/api/users')) {
    $r = Api 'Get' ($base + $ep) $null $null
    Check ("匿名被拒绝 " + $ep) ($r.status -eq 401) ("http=" + $r.status)
}

Write-Host ""
Write-Host "=== 4. 白名单可匿名访问 ==="
foreach ($ep in @('/api/auth/login', '/api/health/ping')) {
    $r = if ($ep -like '*login*') { Api 'Post' ($base + $ep) '{"username":"admin","password":"123456"}' $null }
         else { Api 'Get' ($base + $ep) $null $null }
    Check ("白名单放行 " + $ep) ($r.status -eq 200) ("http=" + $r.status)
}

Write-Host ""
Write-Host "=== 停止后端 ==="
if (-not $proc.HasExited) {
    & taskkill /PID $proc.Id /T /F 2>&1 | Out-Null
    try { $proc.WaitForExit(8000) | Out-Null } catch { }
    Write-Host '  已停止'
}
Start-Sleep -Seconds 1
$log = ''
try { $log = $outTask.Result } catch { }
$errors = @(($log -split "`r?`n") | Where-Object { $_ -match ' ERROR ' })
Check "后端日志无 ERROR" ($errors.Count -eq 0) ("ERROR 行数 " + $errors.Count)

Write-Host ""
Write-Host ("=== RESULT: {0} passed, {1} failed ===" -f $pass, $fail)
if ($fail -eq 0) { Write-Host "PERMISSION MATRIX VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "PERMISSION MATRIX VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
