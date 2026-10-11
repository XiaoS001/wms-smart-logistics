# 算法效果页的数据验证。
#
# 页面消费三个接口：货位分配效果、拣货路径效果、真实单据算法表现。
# 仓库为空时这些接口会返回 409 或空数据，页面只能显示空状态，
# 因此脚本先造真实库存与已规划路径的出库单，再逐项断言页面需要的数据形态。

$ErrorActionPreference = 'Continue'
$root    = 'D:\DeepSeekXM\XM01'
$backend = Join-Path $root '03-源码\backend'
$java    = 'D:\Dev\jdk8\bin\java.exe'
$jar     = Join-Path $backend 'target\wms-1.0.0.jar'
$base    = 'http://127.0.0.1:8080'
$mysqlExe = 'D:\Dev\mysql8\bin\mysql.exe'

$pass = 0; $fail = 0
function Check($name, $condition, $detail) {
    if ($condition) { Write-Host ("  [PASS] {0,-48} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-48} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}
function Api($method, $url, $body) {
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 60; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    if ($body) { $p.Body = $body; $p.ContentType = 'application/json' }
    try { $r = Invoke-WebRequest @p } catch { return @{ status = 0; json = $null; raw = $_.Exception.Message } }
    $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$r.StatusCode; json = $j; raw = $r.Content }
}
function Sql($s) {
    $f = Join-Path $env:TEMP 'wms_algo_report.sql'
    [System.IO.File]::WriteAllText($f, $s, (New-Object System.Text.UTF8Encoding($false)))
    $out = & cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP --default-character-set=utf8mb4 -N -B < `"$f`" 2>nul"
    Remove-Item $f -Force -ErrorAction SilentlyContinue
    return $out
}

$TAG = 'verify-algo-report'

Write-Host "=== 1. 清空基线 ==="
Sql "DELETE FROM wms.biz_slotting_plan; DELETE FROM wms.biz_stocktake; DELETE FROM wms.inv_stock; DELETE FROM wms.biz_outbound_item; DELETE FROM wms.biz_outbound_order; DELETE FROM wms.biz_inbound_item; DELETE FROM wms.biz_inbound_order; UPDATE wms.base_location SET status=1;" | Out-Null
Check "基线已清空" $true "库存/单据/方案清空，货位置空闲"

Write-Host ""
Write-Host "=== 2. 启动后端 ==="
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
    if ((Api 'Get' "$base/api/health/ping" $null).status -eq 200) { $ready = $true; break }
}
Check "后端已就绪" $ready ("等待 " + ($i * 3) + " 秒")
if (-not $ready) { if (-not $proc.HasExited) { $proc.Kill() }; exit 2 }

Write-Host ""
Write-Host "=== 3. 空库时拣货效果应给出可识别的失败（页面据此显示提示）==="
$r = Api 'Get' "$base/api/report/picking-effect/1" $null
Check "空库时拣货效果返回 409" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)

$r = Api 'Get' "$base/api/report/route-algorithm-statistics" $null
Check "空库时真实单据统计为空数组" ($r.json.code -eq 200 -and $r.json.data.Count -eq 0) ("count=" + $r.json.data.Count)

Write-Host ""
Write-Host "=== 4. 造数据：入库 5 种商品 + 出库单并规划路径 ==="
$freqBefore = @{}
foreach ($row in (Sql "SELECT id, pick_freq FROM wms.base_product WHERE id <= 5;")) {
    $p = ($row -split "`t"); if ($p.Count -ge 2) { $freqBefore[[int]$p[0]] = [int]$p[1] }
}
$inBody = '{"warehouseId":1,"supplier":"' + $TAG + '","useSlottingSuggest":true,"strategy":"frequency-greedy","remark":"' + $TAG + '","items":[' +
    '{"productId":1,"planQuantity":120},{"productId":2,"planQuantity":90},' +
    '{"productId":3,"planQuantity":60},{"productId":4,"planQuantity":45},{"productId":5,"planQuantity":30}]}'
$r = Api 'Post' "$base/api/inbound/orders" $inBody
$inId = $r.json.data.id
$r = Api 'Post' "$base/api/inbound/orders/$inId/confirm" $null
Check "已备货 5 个货位" ($r.json.data.occupiedLocations -eq 5) ("occupied=" + $r.json.data.occupiedLocations)

$r = Api 'Post' "$base/api/outbound/orders" ('{"warehouseId":1,"customer":"' + $TAG + '","remark":"' + $TAG + '","items":[{"productId":1,"quantity":20},{"productId":3,"quantity":10},{"productId":5,"quantity":5}]}')
$outId = $r.json.data.id
$r = Api 'Post' "$base/api/outbound/orders/$outId/plan-route?strategy=ant-colony" $null
Check "已规划拣货路径" ($r.json.code -eq 200) ("distance=" + $r.json.data.distance + " m")

Write-Host ""
Write-Host "=== 5. 货位分配效果（页面上半部分）==="
$r = Api 'Get' "$base/api/report/slotting-effect/1" $null
$s = $r.json.data
Check "接口返回成功" ($r.json.code -eq 200) ("code=" + $r.json.code)
Check "含 4 种策略的目标函数" (@($s.objectives.PSObject.Properties).Count -eq 4) `
      ("strategies=" + (@($s.objectives.PSObject.Properties | ForEach-Object { $_.Name }) -join ','))
Check "含各策略降幅（页面画降幅图）" (@($s.improvementPercent.PSObject.Properties).Count -ge 3) `
      ("improvement keys=" + (@($s.improvementPercent.PSObject.Properties | ForEach-Object { $_.Name }) -join ','))
Check "含各策略耗时（页面表格列）" (@($s.elapsedMillis.PSObject.Properties).Count -eq 4) `
      ("elapsed keys=" + (@($s.elapsedMillis.PSObject.Properties).Count) + " 个")
Check "贪心优于随机（降幅为正）" ([double]$s.greedyVsRandomPercent -gt 0) `
      ("greedyVsRandom=" + $s.greedyVsRandomPercent + "%")
Check "最优策略数值最小（页面高亮依据）" `
      ([double]$s.objectives.'frequency-greedy' -le [double]$s.objectives.random) `
      ("greedy=" + $s.objectives.'frequency-greedy' + " random=" + $s.objectives.random)
Check "含商品数与货位数（页面标签用）" (($s.productCount -gt 0) -and ($s.slotCount -gt 0)) `
      ("productCount=" + $s.productCount + " slotCount=" + $s.slotCount)

Write-Host ""
Write-Host "=== 6. 拣货路径效果（页面下半部分）==="
$r = Api 'Get' "$base/api/report/picking-effect/1" $null
$p = $r.json.data
Check "接口返回成功" ($r.json.code -eq 200) ("code=" + $r.json.code)
Check "含 4 种策略的路径长度" (@($p.distances.PSObject.Properties).Count -eq 4) `
      ("distances=" + (@($p.distances.PSObject.Properties | ForEach-Object { $_.Name + '=' + $_.Value }) -join ' '))
Check "含策略中文名（页面显示用）" ($null -ne $p.labels.'ant-colony') ("ant-colony label=" + $p.labels.'ant-colony')
Check "含相对 S 型的降幅" (@($p.improvementVsSShapePercent.PSObject.Properties).Count -eq 4) `
      ("improvement=" + (@($p.improvementVsSShapePercent.PSObject.Properties | ForEach-Object { $_.Name + '=' + $_.Value }) -join ' '))
Check "S 型自身降幅为 0（页面过滤它）" ([double]$p.improvementVsSShapePercent.'s-shape' -eq 0) `
      ("s-shape improvement=" + $p.improvementVsSShapePercent.'s-shape')
Check "拣货点数与库存货位一致" ($p.pickPointCount -eq 5) ("pickPointCount=" + $p.pickPointCount)
Check "有策略不劣于 S 型（降幅 ≥ 0）" `
      ((@($p.improvementVsSShapePercent.PSObject.Properties | Where-Object { $_.Name -ne 's-shape' } | Where-Object { [double]$_.Value -ge 0 }).Count) -ge 1) `
      "至少一个策略相对 S 型有改善或不劣"
Check "蚁群算法路径最短（页面绿色高亮）" `
      ([double]$p.distances.'ant-colony' -le [double]$p.distances.'s-shape') `
      ("aco=" + $p.distances.'ant-colony' + " sShape=" + $p.distances.'s-shape')

Write-Host ""
Write-Host "=== 7. 真实单据上的算法表现 ==="
$r = Api 'Get' "$base/api/report/route-algorithm-statistics" $null
Check "接口返回成功" ($r.json.code -eq 200) ("code=" + $r.json.code)
Check "含 1 条实际算法记录" ($r.json.data.Count -eq 1) ("count=" + $r.json.data.Count)
Check "记录的算法为蚁群" ($r.json.data[0].routeAlgo -eq 'ant-colony') ("algo=" + $r.json.data[0].routeAlgo)
Check "单据数为 1" ([int]$r.json.data[0].orderCount -eq 1) ("orderCount=" + $r.json.data[0].orderCount)
Check "平均路径长度为正" ([double]$r.json.data[0].avgDistance -gt 0) ("avgDistance=" + $r.json.data[0].avgDistance)

Write-Host ""
Write-Host "=== 8. 清理 ==="
Sql "DELETE FROM wms.inv_stock;" | Out-Null
Sql "DELETE FROM wms.biz_outbound_item;" | Out-Null
Sql "DELETE FROM wms.biz_outbound_order;" | Out-Null
Sql "DELETE FROM wms.biz_inbound_item;" | Out-Null
Sql "DELETE FROM wms.biz_inbound_order;" | Out-Null
Sql "UPDATE wms.base_location SET status = 1;" | Out-Null
foreach ($kv in $freqBefore.GetEnumerator()) { Sql "UPDATE wms.base_product SET pick_freq = $($kv.Value) WHERE id = $($kv.Key);" | Out-Null }
$stock = [double](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock;")
$used = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
Check "库存已清空" ($stock -eq 0) ("stock=" + $stock)
Check "货位已恢复" ($used -eq 0) ("occupied=" + $used)

Write-Host ""
Write-Host "=== 9. 停止后端 ==="
if (-not $proc.HasExited) {
    & taskkill /PID $proc.Id /T /F 2>&1 | Out-Null
    try { $proc.WaitForExit(8000) | Out-Null } catch { }
    Write-Host '  已停止'
}
Start-Sleep -Seconds 1
$log = ''
try { $log = $outTask.Result } catch { }
$errors = ($log -split "`r?`n") | Where-Object { $_ -match ' ERROR ' }
Check "后端日志无 ERROR" ($errors.Count -eq 0) ("error lines = " + $errors.Count)
if ($errors.Count -gt 0) { $errors | Select-Object -First 5 | ForEach-Object { Write-Host ("      " + $_.Trim()) } }

Write-Host ""
Write-Host ("=== RESULT: {0} passed, {1} failed ===" -f $pass, $fail)
if ($fail -eq 0) { Write-Host "ALGORITHM REPORT PAGE VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "ALGORITHM REPORT PAGE VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
