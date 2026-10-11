# 准备验收演示数据。
#
# 目的：让网站在打开时就有可看、可操作的内容，而不是空页面。
# 演示数据的安排是刻意的：
#   - 入库单使用算法推荐货位 → 可直接看"推荐理由"
#   - 出库单建好但**不预先规划路径** → 留给你在页面上亲手点"规划路径"，看算法效果
#   - 出库单也不预先确认 → 留给你亲手"确认出库"，观察库存与频次变化
#   - 保留一张待入库单据 → 留给你亲手确认入库
# 这样演示时每个关键动作都由你本人触发，效果最直观。

$ErrorActionPreference = 'Continue'
$base     = 'http://127.0.0.1:8080'
$mysqlExe = 'D:\Dev\mysql8\bin\mysql.exe'

function Api($method, $url, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 60; UseBasicParsing = $true
            SkipHttpErrorCheck = $true; Headers = $headers }
    if ($body) { $p.Body = $body; $p.ContentType = 'application/json' }
    try { $r = Invoke-WebRequest @p } catch { return @{ status = 0; json = $null; raw = $_.Exception.Message } }
    $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$r.StatusCode; json = $j; raw = $r.Content }
}
function Sql($s) {
    $f = Join-Path $env:TEMP 'wms_demo_seed.sql'
    [System.IO.File]::WriteAllText($f, $s, (New-Object System.Text.UTF8Encoding($false)))
    & cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP --default-character-set=utf8mb4 -N -B < `"$f`" 2>nul" | Out-Null
    Remove-Item $f -Force -ErrorAction SilentlyContinue
}

Write-Host "=== 1. 清空并恢复基线 ==="
Sql "DELETE FROM wms.biz_slotting_plan; DELETE FROM wms.biz_stocktake; DELETE FROM wms.inv_stock; DELETE FROM wms.biz_outbound_item; DELETE FROM wms.biz_outbound_order; DELETE FROM wms.biz_inbound_item; DELETE FROM wms.biz_inbound_order; UPDATE wms.base_location SET status=1; DELETE FROM wms.base_product WHERE product_code LIKE 'AC%' OR product_code='PERMDENY';"
Write-Host "  完成"

Write-Host ""
Write-Host "=== 2. 登录 ==="
$r = Api 'Post' "$base/api/auth/login" '{"username":"admin","password":"123456"}' $null
if ($r.status -ne 200) { Write-Host "  登录失败，请确认后端已启动" -ForegroundColor Red; exit 1 }
$token = $r.json.data.token
Write-Host ("  管理员登录成功，权限 " + @($r.json.data.permissions).Count + " 项")

Write-Host ""
Write-Host "=== 3. 演示数据 A：已完成的入库单（展示算法推荐理由）==="
$body = '{"warehouseId":1,"supplier":"华东日用百货供应商","useSlottingSuggest":true,"strategy":"frequency-greedy","remark":"演示数据：算法按出库频次推荐货位","items":[' +
        '{"productId":1,"planQuantity":240,"unitPrice":12.5},' +
        '{"productId":2,"planQuantity":160,"unitPrice":28},' +
        '{"productId":3,"planQuantity":120,"unitPrice":15},' +
        '{"productId":4,"planQuantity":80,"unitPrice":36},' +
        '{"productId":6,"planQuantity":60,"unitPrice":9.8},' +
        '{"productId":8,"planQuantity":40,"unitPrice":89}]}'
$r = Api 'Post' "$base/api/inbound/orders" $body $token
if ($r.status -ne 200) { Write-Host ("  创建失败: " + $r.json.message) -ForegroundColor Red; exit 1 }
$inId1 = $r.json.data.id
Write-Host ("  单据 " + $r.json.data.orderNo + " 已创建")
$r = Api 'Post' "$base/api/inbound/orders/$inId1/confirm" $null $token
Write-Host ("  已确认入库，占用 " + $r.json.data.occupiedLocations + " 个货位")

Write-Host ""
Write-Host "=== 4. 演示数据 B：待确认的入库单（留给你亲手确认）==="
$body2 = '{"warehouseId":1,"supplier":"华南电子配件供应商","useSlottingSuggest":true,"strategy":"abc-greedy","remark":"演示数据：待你确认入库","items":[' +
         '{"productId":5,"planQuantity":50,"unitPrice":199},' +
         '{"productId":7,"planQuantity":90,"unitPrice":16.8},' +
         '{"productId":9,"planQuantity":70,"unitPrice":22}]}'
$r = Api 'Post' "$base/api/inbound/orders" $body2 $token
Write-Host ("  单据 " + $r.json.data.orderNo + " 已创建（状态：待入库，可在页面上确认）")

Write-Host ""
Write-Host "=== 5. 演示数据 C：待规划路径的出库单（留给你亲手规划）==="
$body3 = '{"warehouseId":1,"customer":"杭州城西连锁超市","remark":"演示数据：待你规划拣货路径","items":[' +
         '{"productId":1,"quantity":60},' +
         '{"productId":2,"quantity":40},' +
         '{"productId":3,"quantity":30},' +
         '{"productId":4,"quantity":20}]}'
$r = Api 'Post' "$base/api/outbound/orders" $body3 $token
if ($r.status -ne 200) { Write-Host ("  创建失败: " + $r.json.message) -ForegroundColor Red; exit 1 }
$outId = $r.json.data.id
Write-Host ("  单据 " + $r.json.data.orderNo + " 已创建（未规划路径，未出库）")

Write-Host ""
Write-Host "=== 6. 演示数据 D：第二位客户的出库单 ==="
$body4 = '{"warehouseId":1,"customer":"南京江宁社区便利店","remark":"演示数据","items":[' +
         '{"productId":6,"quantity":25},' +
         '{"productId":8,"quantity":10}]}'
$r = Api 'Post' "$base/api/outbound/orders" $body4 $token
Write-Host ("  单据 " + $r.json.data.orderNo + " 已创建")

Write-Host ""
Write-Host "=== 7. 演示数据就绪情况 ==="
$r = Api 'Get' "$base/api/report/dashboard" $null $token
$d = $r.json.data
Write-Host ("  库存总量      : " + $d.stockQuantity)
Write-Host ("  货位占用率    : " + $d.occupancyRate + "%  (" + $d.occupiedLocationCount + " / " + $d.locationCount + ")")
Write-Host ("  商品种类      : " + $d.productCount)
Write-Host ("  缺货预警      : " + $d.lowStockCount + " 种")
Write-Host ("  已完成入库单  : " + $d.doneInboundCount)
Write-Host ""
Write-Host "演示数据准备完成。" -ForegroundColor Green
Write-Host "提示：出库单 " + $outId + " 尚未规划路径，请到「拣货路径规划」页亲手点『规划路径』。"
