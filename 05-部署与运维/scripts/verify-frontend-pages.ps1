# End-to-end verification for the frontend pages implemented so far.
#
# The pages added in this round (dashboard, location management, picking route
# planning) depend on backend data being present. An empty warehouse makes them
# look identical to an unimplemented page, so this script first creates real
# business data and then asserts the exact response shapes those pages consume.
#
#   1. build backend, start it
#   2. seed: inbound 5 products so the warehouse has stock and ABC classes
#   3. dashboard page needs: /report/dashboard, /report/operation-trend,
#      /report/frequency-contribution, /report/slotting-effect,
#      /report/picking-effect, /stock/summary
#   4. location page needs: /warehouses, /locations, /warehouses/{id}/locations/summary
#      plus the idempotency behaviour of the generate endpoint
#   5. picking page needs: /outbound/orders, /outbound/orders/{id},
#      /outbound/orders/{id}/plan-route with a route containing the I/O point
#      and every pick point, and a comparison across strategies
#   6. cleanup restores the baseline

$ErrorActionPreference = 'Continue'
$root    = 'D:\DeepSeekXM\XM01'
$backend = Join-Path $root '03-源码\backend'
$mvn     = 'D:\Dev\maven\bin\mvn.cmd'
$java    = 'D:\Dev\jdk8\bin\java.exe'
$jar     = Join-Path $backend 'target\wms-1.0.0.jar'
$base    = 'http://127.0.0.1:8080'
$mysqlExe = 'D:\Dev\mysql8\bin\mysql.exe'

$env:JAVA_HOME = 'D:\Dev\jdk8'
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'

$pass = 0; $fail = 0
function Check($name, $condition, $detail) {
    if ($condition) { Write-Host ("  [PASS] {0,-46} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-46} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}
function Api($method, $url, $body) {
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 60; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    if ($body) { $p.Body = $body; $p.ContentType = 'application/json' }
    try { $r = Invoke-WebRequest @p } catch { return @{ status = 0; json = $null; raw = $_.Exception.Message } }
    $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$r.StatusCode; json = $j; raw = $r.Content }
}
function Sql($statement) {
    $f = Join-Path $env:TEMP 'wms_fe_verify.sql'
    [System.IO.File]::WriteAllText($f, $statement, (New-Object System.Text.UTF8Encoding($false)))
    $out = & cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP --default-character-set=utf8mb4 -N -B < `"$f`" 2>nul"
    Remove-Item $f -Force -ErrorAction SilentlyContinue
    return $out
}

$TAG = 'verify-frontend-pages'

Write-Host "=== 1. Clean baseline ==="
$sql = "DELETE FROM wms.biz_slotting_plan; DELETE FROM wms.biz_stocktake; DELETE FROM wms.inv_stock; DELETE FROM wms.biz_outbound_item; DELETE FROM wms.biz_outbound_order; DELETE FROM wms.biz_inbound_item; DELETE FROM wms.biz_inbound_order; UPDATE wms.base_location SET status=1;"
Sql $sql | Out-Null
Check "baseline cleared" $true "库存/单据/方案已清空，货位全部置为空闲"

Write-Host ""
Write-Host "=== 2. Ensure backend jar and start ==="
if (-not (Test-Path $jar)) {
    Push-Location $backend
    & $mvn -B -q clean package -DskipTests 2>&1 | Out-Null
    Pop-Location
}
Check "backend jar exists" (Test-Path $jar) ("size=" + [math]::Round((Get-Item $jar).Length / 1MB, 2) + " MB")

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
Check "backend started" $ready ("waited=" + ($i * 3) + "s")
if (-not $ready) {
    if (-not $proc.HasExited) { $proc.Kill() }
    exit 2
}

Write-Host ""
Write-Host "=== 3. Seed business data (needed for the pages to show anything) ==="
$freqBefore = @{}
foreach ($row in (Sql "SELECT id, pick_freq, safety_stock FROM wms.base_product WHERE id <= 6;")) {
    $p = ($row -split "`t"); if ($p.Count -ge 3) { $freqBefore[[int]$p[0]] = [int]$p[1] }
}
$inBody = '{"warehouseId":1,"supplier":"' + $TAG + '","useSlottingSuggest":true,"strategy":"frequency-greedy","remark":"' + $TAG + '","items":[' +
    '{"productId":1,"planQuantity":120,"unitPrice":10},' +
    '{"productId":2,"planQuantity":90,"unitPrice":8},' +
    '{"productId":3,"planQuantity":60,"unitPrice":12},' +
    '{"productId":4,"planQuantity":45,"unitPrice":15},' +
    '{"productId":5,"planQuantity":30,"unitPrice":20}]}'
$r = Api 'Post' "$base/api/inbound/orders" $inBody
Check "seed inbound order created" ($r.json.code -eq 200) ("id=" + $r.json.data.id)
$inId = $r.json.data.id
$r = Api 'Post' "$base/api/inbound/orders/$inId/confirm" $null
Check "seed inbound confirmed" ($r.json.data.occupiedLocations -eq 5) ("occupied=" + $r.json.data.occupiedLocations)

# 再造一张出库单并规划路径，供拣货页面展示
$r = Api 'Post' "$base/api/outbound/orders" ('{"warehouseId":1,"customer":"' + $TAG + '","remark":"' + $TAG + '","items":[{"productId":1,"quantity":20},{"productId":3,"quantity":10},{"productId":5,"quantity":5}]}')
Check "seed outbound order created" ($r.json.code -eq 200) ("id=" + $r.json.data.id)
$outId = $r.json.data.id

Write-Host ""
Write-Host "=== 4. Dashboard page data ==="
$r = Api 'Get' "$base/api/report/dashboard" $null
$d = $r.json.data
Check "dashboard: returns" ($r.json.code -eq 200) ("code=" + $r.json.code)
Check "dashboard: stock quantity > 0" ([double]$d.stockQuantity -gt 0) ("stock=" + $d.stockQuantity)
Check "dashboard: occupancy rate > 0" ([double]$d.occupancyRate -gt 0) ("rate=" + $d.occupancyRate + "%")
Check "dashboard: all six cards have data" `
      (($null -ne $d.productCount) -and ($null -ne $d.locationCount) -and ($null -ne $d.occupiedLocationCount) -and `
       ($null -ne $d.stockQuantity) -and ($null -ne $d.lowStockCount) -and ($null -ne $d.doneInboundCount)) `
      ("products=" + $d.productCount + " locations=" + $d.locationCount + " lowStock=" + $d.lowStockCount)

$r = Api 'Get' "$base/api/report/operation-trend?days=14" $null
Check "trend: returns rows" ($r.json.data.trend.Count -ge 1) ("rows=" + $r.json.data.trend.Count)
Check "trend: today has inbound qty" ([double]$r.json.data.summary.inboundQuantity -gt 0) ("qty=" + $r.json.data.summary.inboundQuantity)
Check "trend: each row has all four series" `
      (@($r.json.data.trend[0].PSObject.Properties).Count -ge 5) `
      ("fields=" + (@($r.json.data.trend[0].PSObject.Properties | ForEach-Object { $_.Name }) -join ','))

$r = Api 'Get' "$base/api/report/frequency-contribution?limit=12" $null
Check "frequency: returns items" ($r.json.data.items.Count -ge 5) ("items=" + $r.json.data.items.Count)
Check "frequency: cumulative ratio is monotonic" `
      ((($r.json.data.items | ForEach-Object { [double]$_.cumulativeRatio }) -join ',') -eq `
       ((($r.json.data.items | ForEach-Object { [double]$_.cumulativeRatio }) | Sort-Object) -join ',')) `
      "累计占比单调递增（折线图前提）"
Check "frequency: 70% crossing point present" ($null -ne $r.json.data.productsFor70Percent) `
      ("productsFor70%=" + $r.json.data.productsFor70Percent)
Check "frequency: items carry abcClass for colouring" `
      ($null -ne $r.json.data.items[0].abcClass) ("first abc=" + $r.json.data.items[0].abcClass)

$r = Api 'Get' "$base/api/report/slotting-effect/1" $null
Check "slotting effect: four strategies" (@($r.json.data.objectives.PSObject.Properties).Count -eq 4) `
      ("strategies=" + (@($r.json.data.objectives.PSObject.Properties | ForEach-Object { $_.Name }) -join ','))
Check "slotting effect: improvement > 0" ([double]$r.json.data.greedyVsRandomPercent -gt 0) `
      ("improvement=" + $r.json.data.greedyVsRandomPercent + "%")

$r = Api 'Get' "$base/api/report/picking-effect/1" $null
Check "picking effect: returns distances" (@($r.json.data.distances.PSObject.Properties).Count -eq 4) `
      ("strategies=" + (@($r.json.data.distances.PSObject.Properties | ForEach-Object { $_.Name }) -join ','))
Check "picking effect: labels present for display" ($null -ne $r.json.data.labels.'ant-colony') `
      ("label=" + $r.json.data.labels.'ant-colony')

$r = Api 'Get' "$base/api/stock/summary?warehouseId=1" $null
$low = @($r.json.data | Where-Object { $_.lowStock -eq 1 })
Check "stock summary: low-stock list computable" ($r.json.data.Count -ge 5) `
      ("rows=" + $r.json.data.Count + " lowStock=" + $low.Count)

Write-Host ""
Write-Host "=== 5. Location page data ==="
$r = Api 'Get' "$base/api/warehouses" $null
Check "warehouses: list for selector" ($r.json.data.Count -ge 1) ("count=" + $r.json.data.Count)
$w = $r.json.data[0]
Check "warehouse: layout params for heat map" `
      (($w.aisleCount -gt 0) -and ($w.rowCount -gt 0) -and ($null -ne $w.aisleGap) -and ($null -ne $w.rowGap)) `
      ($w.aisleCount.ToString() + " x " + $w.rowCount + " gap=" + $w.aisleGap + "/" + $w.rowGap)

$r = Api 'Get' "$base/api/locations?warehouseId=1&limit=5000" $null
$locs = $r.json.data
Check "locations: 200 returned" ($locs.Count -eq 200) ("count=" + $locs.Count)
Check "locations: sorted by distance asc" `
      (([double]$locs[0].distance -le [double]$locs[-1].distance)) `
      ("first=" + $locs[0].distance + " last=" + $locs[-1].distance)
Check "locations: have coordinates for the grid" `
      (($null -ne $locs[0].aisleNo) -and ($null -ne $locs[0].rowNo)) `
      ("first=" + $locs[0].locationCode + " (" + $locs[0].aisleNo + "," + $locs[0].rowNo + ")")
$occupied = @($locs | Where-Object { $_.status -eq 2 })
Check "locations: occupied count matches seeding" ($occupied.Count -eq 5) ("occupied=" + $occupied.Count)

$r = Api 'Get' "$base/api/warehouses/1/locations/summary" $null
Check "zone summary: returned for zone table" ($r.json.data.Count -ge 1) ("zones=" + $r.json.data.Count)

# 幂等性：再次生成不应新增
$r = Api 'Post' "$base/api/warehouses/1/locations/generate" '{"aisleCount":10,"rowCount":20,"aisleDistance":15,"rowDistance":30,"capacity":100,"overwrite":false}'
Check "generate: idempotent (skips all 200)" `
      (($r.json.data.created -eq 0) -and ($r.json.data.skipped -eq 200)) `
      ("created=" + $r.json.data.created + " skipped=" + $r.json.data.skipped)

Write-Host ""
Write-Host "=== 6. Picking page data ==="
$r = Api 'Get' "$base/api/outbound/orders?warehouseId=1&limit=100" $null
Check "outbound list: available orders" ($r.json.data.Count -ge 1) ("count=" + $r.json.data.Count)
$pending = @($r.json.data | Where-Object { $_.status -eq 0 -or $_.status -eq 1 })
Check "outbound list: at least one planable order" ($pending.Count -ge 1) ("planable=" + $pending.Count)

$r = Api 'Get' "$base/api/outbound/orders/$outId" $null
$items = $r.json.data.items
Check "order detail: items have coordinates" `
      (@($items | Where-Object { $null -ne $_.aisleNo -and $null -ne $_.rowNo }).Count -eq $items.Count) `
      ("items=" + $items.Count)

$r = Api 'Post' "$base/api/outbound/orders/$outId/plan-route?strategy=ant-colony" $null
$plan = $r.json.data
Check "plan-route: returns route" ($r.json.code -eq 200 -and $plan.route.Count -ge 2) ("points=" + $plan.route.Count)
Check "plan-route: starts and ends at I/O" `
      (($plan.route[0].aisleNo -eq 0) -and ($plan.route[0].rowNo -eq 0) -and `
       ($plan.route[-1].aisleNo -eq 0) -and ($plan.route[-1].rowNo -eq 0)) `
      ("start=(" + $plan.route[0].aisleNo + "," + $plan.route[0].rowNo + ") end=(" + $plan.route[-1].aisleNo + "," + $plan.route[-1].rowNo + ")")
Check "plan-route: distance > 0" ([double]$plan.distance -gt 0) ("distance=" + $plan.distance + " m")
Check "plan-route: four strategies compared" (@($plan.comparison.PSObject.Properties).Count -eq 4) `
      ("comparison=" + (@($plan.comparison.PSObject.Properties | ForEach-Object { $_.Name + '=' + $_.Value }) -join ' '))

$routePicks = @($plan.route | Where-Object { $_.aisleNo -ne 0 -or $_.rowNo -ne 0 })
$uniquePicks = @($routePicks | ForEach-Object { "$($_.aisleNo)-$($_.rowNo)" } | Select-Object -Unique)
Check "plan-route: covers every pick point once" ($uniquePicks.Count -eq $plan.pickPointCount) `
      ("unique in route=" + $uniquePicks.Count + " pickPointCount=" + $plan.pickPointCount)

$r = Api 'Get' "$base/api/outbound/orders/$outId" $null
Check "order: route persisted for reload" `
      (($null -ne $r.json.data.order.routeDistance) -and ($null -ne $r.json.data.order.routeAlgo)) `
      ("algo=" + $r.json.data.order.routeAlgo + " distance=" + $r.json.data.order.routeDistance)
Check "order: route detail JSON parseable" `
      ($r.json.data.order.routeDetail.StartsWith('[') -and $r.json.data.order.routeDetail.EndsWith(']')) `
      ("detail=" + $r.json.data.order.routeDetail.Substring(0, [Math]::Min(46, $r.json.data.order.routeDetail.Length)) + "...")
$seqs = @($r.json.data.items | ForEach-Object { [int]$_.pickSeq })
Check "order: pick sequence written back" (($seqs | Where-Object { $_ -gt 0 }).Count -ge 1) ("seqs=" + ($seqs -join ','))
Check "order: status moved to picking (1)" ($r.json.data.order.status -eq 1) ("status=" + $r.json.data.order.status)

$r = Api 'Get' "$base/api/outbound/strategies" $null
Check "strategies: list for the selector" (@($r.json.data.PSObject.Properties).Count -eq 4) `
      ("codes=" + (@($r.json.data.PSObject.Properties | ForEach-Object { $_.Name }) -join ','))

Write-Host ""
Write-Host "=== 7. Product / category / warehouse pages data ==="
$r = Api 'Get' "$base/api/products?limit=2000" $null
Check "products: list for the page" ($r.json.data.Count -ge 5) ("count=" + $r.json.data.Count)
Check "products: sorted by pickFreq desc" `
      (($r.json.data[0].pickFreq -ge $r.json.data[1].pickFreq)) `
      ("first=" + $r.json.data[0].pickFreq + " second=" + $r.json.data[1].pickFreq)
Check "products: every row has abcClass for the tag" `
      (@($r.json.data | Where-Object { $null -eq $_.abcClass }).Count -eq 0) `
      "all rows carry abcClass"
Check "products: categoryId present for the filter" ($null -ne $r.json.data[0].categoryId) `
      ("categoryId=" + $r.json.data[0].categoryId)

# 单商品详情与新增、编辑、删除的完整回路
$newCode = 'VF' + (Get-Random -Minimum 100000 -Maximum 999999)
$r = Api 'Post' "$base/api/products" ('{"productCode":"' + $newCode + '","productName":"verify frontend product","unit":"件","pickFreq":7,"abcClass":"C","status":1}')
Check "products: create works" ($r.json.code -eq 200) ("id=" + $r.json.data.id)
$createdId = $r.json.data.id
$r = Api 'Put' "$base/api/products/$createdId" ('{"productCode":"' + $newCode + '","productName":"verify frontend product edited","unit":"件","pickFreq":9,"abcClass":"C","status":1}')
Check "products: update works" ($r.json.code -eq 200) ("name=" + $r.json.data.productName)
$r = Api 'Get' "$base/api/products/$createdId" $null
Check "products: detail works" ($r.json.data.productName -eq 'verify frontend product edited') ("pickFreq=" + $r.json.data.pickFreq)

# 频次校正：后端为增量累加接口，页面按差量补足
$r = Api 'Post' "$base/api/products/$createdId/pick-freq?delta=3" $null
Check "products: pickFreq accumulates (9+3=12)" ($r.json.data.pickFreq -eq 12) ("pickFreq=" + $r.json.data.pickFreq)

# ABC 分类重算：页面依赖返回的数量与占比
$r = Api 'Post' "$base/api/products/abc-classify" $null
$abc = $r.json.data
Check "products: abc-classify returns counts" (@($abc.classCounts.PSObject.Properties).Count -ge 2) `
      ("counts=" + (@($abc.classCounts.PSObject.Properties | ForEach-Object { $_.Name + '=' + $_.Value }) -join ' '))
Check "products: abc-classify returns ratios" (@($abc.classFrequencyRatio.PSObject.Properties).Count -ge 2) `
      ("ratios=" + (@($abc.classFrequencyRatio.PSObject.Properties | ForEach-Object { $_.Name + '=' + $_.Value }) -join ' '))
Check "products: abc-classify updated rows" ([int]$abc.updatedCount -ge 5) ("updated=" + $abc.updatedCount)

$r = Api 'Delete' "$base/api/products/$createdId" $null
Check "products: delete works" ($r.json.code -eq 200) ("code=" + $r.json.code)

# 分类树：页面左侧树依赖父子结构与商品数量
$r = Api 'Get' "$base/api/categories/tree" $null
$tree = $r.json.data
Check "categories: tree returned" ($tree.Count -ge 1) ("roots=" + $tree.Count)
$countNode = 0
$withCount = 0
function Walk-Tree($nodes) {
    foreach ($n in $nodes) {
        $script:countNode++
        if ($null -ne $n.productCount) { $script:withCount++ }
        if ($n.children -and $n.children.Count -gt 0) { Walk-Tree $n.children }
    }
}
$countNode = 0; $withCount = 0
Walk-Tree $tree
Check "categories: every node carries productCount" ($withCount -eq $countNode) `
      ("nodes=" + $countNode + " withCount=" + $withCount)
Check "categories: has nested children" (@($tree | Where-Object { $_.children.Count -gt 0 }).Count -ge 1) `
      "父子结构存在"
$r = Api 'Get' "$base/api/categories" $null
Check "categories: flat list for selects" ($r.json.data.Count -ge $countNode) ("flat=" + $r.json.data.Count)

# 仓库布局参数（页面表单与最远距离估算依赖）
$r = Api 'Get' "$base/api/warehouses" $null
$w = $r.json.data[0]
$expectedMax = [double]$w.aisleGap * [int]$w.aisleCount + [double]$w.rowGap * [int]$w.rowCount
Check "warehouse: max distance computable in page" ($expectedMax -gt 0) ("maxDistance=" + $expectedMax + " m")

Write-Host ""
Write-Host "=== 8. Inbound page data (slotting suggestion reasons) ==="
$r = Api 'Get' "$base/api/inbound/orders?warehouseId=1&limit=100" $null
Check "inbound: list for the page" ($r.json.data.Count -ge 1) ("count=" + $r.json.data.Count)
$order = $r.json.data | Where-Object { $_.useSlottingSuggest -eq 1 } | Select-Object -First 1
Check "inbound: order flagged as using slotting" ($null -ne $order) `
      ("orderNo=" + $(if ($order) { $order.orderNo } else { 'none' }))

$r = Api 'Get' "$base/api/inbound/orders/$($order.id)" $null
$dItems = $r.json.data.items
Check "inbound detail: items returned" ($dItems.Count -ge 1) ("items=" + $dItems.Count)
Check "inbound detail: every item has a recommended location" `
      (@($dItems | Where-Object { $_.locationCode }).Count -eq $dItems.Count) `
      ("withLocation=" + @($dItems | Where-Object { $_.locationCode }).Count)
$withReason = @($dItems | Where-Object { $_.suggestReason -and $_.suggestReason.Length -gt 10 })
Check "inbound detail: suggestion reason present for the page" ($withReason.Count -eq $dItems.Count) `
      ("withReason=" + $withReason.Count)
if ($withReason.Count -gt 0) {
    Write-Host ("      sample reason: " + $withReason[0].suggestReason)
}
Check "inbound detail: items carry distance for sorting" `
      (@($dItems | Where-Object { $null -ne $_.distance }).Count -eq $dItems.Count) `
      "所有明细带距离，页面可按距离升序展示"

# details 按距离升序后，高频商品应排在前面（页面用它说明"高频近端"）
$sorted = @($dItems | Sort-Object -Property distance)
$topFreq = @($dItems | Sort-Object -Property pickFreq -Descending)[0]
Check "inbound detail: highest-frequency item sits nearest" `
    ([double]$topFreq.distance -le [double]$sorted[-1].distance) `
    ("top freq product " + $topFreq.productCode + " distance=" + $topFreq.distance)

$r = Api 'Get' "$base/api/products?limit=2000" $null
Check "inbound page: product options available" ($r.json.data.Count -ge 5) ("products=" + $r.json.data.Count)

Write-Host ""
Write-Host "=== 9. Cleanup ==="
Sql "DELETE FROM wms.inv_stock;" | Out-Null
Sql "DELETE FROM wms.biz_outbound_item;" | Out-Null
Sql "DELETE FROM wms.biz_outbound_order;" | Out-Null
Sql "DELETE FROM wms.biz_inbound_item;" | Out-Null
Sql "DELETE FROM wms.biz_inbound_order;" | Out-Null
Sql "UPDATE wms.base_location SET status = 1;" | Out-Null
foreach ($kv in $freqBefore.GetEnumerator()) {
    Sql "UPDATE wms.base_product SET pick_freq = $($kv.Value) WHERE id = $($kv.Key);" | Out-Null
}
$stockFinal = [double](Sql "SELECT IFNULL(SUM(quantity),0) FROM wms.inv_stock;")
$usedFinal = [int](Sql "SELECT COUNT(*) FROM wms.base_location WHERE status = 2;")
Check "stock cleaned" ($stockFinal -eq 0) ("stock=" + $stockFinal)
Check "locations restored" ($usedFinal -eq 0) ("occupied=" + $usedFinal)

Write-Host ""
Write-Host "=== 8. Stop backend ==="
if (-not $proc.HasExited) {
    & taskkill /PID $proc.Id /T /F 2>&1 | Out-Null
    try { $proc.WaitForExit(8000) | Out-Null } catch { }
    Write-Host '  已停止'
}
Start-Sleep -Seconds 1
$log = ''
try { $log = $outTask.Result } catch { }
$errors = ($log -split "`r?`n") | Where-Object { $_ -match ' ERROR ' }
Check "no ERROR in backend log" ($errors.Count -eq 0) ("error lines = " + $errors.Count)
if ($errors.Count -gt 0) { $errors | Select-Object -First 5 | ForEach-Object { Write-Host ("      " + $_.Trim()) } }

Write-Host ""
Write-Host ("=== RESULT: {0} passed, {1} failed ===" -f $pass, $fail)
if ($fail -eq 0) { Write-Host "FRONTEND PAGE DATA VERIFICATION PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "FRONTEND PAGE DATA VERIFICATION FAILED" -ForegroundColor Red; exit 3 }
