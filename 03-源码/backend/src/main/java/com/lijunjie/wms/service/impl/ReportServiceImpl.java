package com.lijunjie.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lijunjie.wms.algorithm.PickPath;
import com.lijunjie.wms.algorithm.PickingRoute;
import com.lijunjie.wms.algorithm.PickingStrategy;
import com.lijunjie.wms.algorithm.PickingStrategyFactory;
import com.lijunjie.wms.algorithm.SlottingAssignment;
import com.lijunjie.wms.algorithm.WarehouseLayout;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.entity.BaseLocation;
import com.lijunjie.wms.entity.BaseProduct;
import com.lijunjie.wms.entity.BaseWarehouse;
import com.lijunjie.wms.entity.InvStock;
import com.lijunjie.wms.mapper.BaseLocationMapper;
import com.lijunjie.wms.mapper.BaseProductMapper;
import com.lijunjie.wms.mapper.BaseWarehouseMapper;
import com.lijunjie.wms.mapper.BizOutboundOrderMapper;
import com.lijunjie.wms.mapper.InvStockMapper;
import com.lijunjie.wms.mapper.ReportMapper;
import com.lijunjie.wms.service.ReportService;
import com.lijunjie.wms.service.SlottingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 报表统计业务实现。
 *
 * <p>本模块除常规作业统计外，还提供**算法效果统计**：用系统内的真实数据
 * 运行货位分配与拣货路径策略，输出可写入论文的效果对比数据，
 * 使“系统—算法—论文”三者的数据口径一致。</p>
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    /** 作业量趋势的最大统计天数 */
    private static final int MAX_TREND_DAYS = 365;

    /** 拣货路径效果对比涉及的货位上限 */
    private static final int MAX_PICK_POINTS = 120;

    private final ReportMapper reportMapper;
    private final BizOutboundOrderMapper outboundOrderMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final BaseLocationMapper locationMapper;
    private final BaseProductMapper productMapper;
    private final InvStockMapper stockMapper;
    private final SlottingService slottingService;
    private final PickingStrategyFactory pickingFactory;

    public ReportServiceImpl(ReportMapper reportMapper,
                            BizOutboundOrderMapper outboundOrderMapper,
                            BaseWarehouseMapper warehouseMapper,
                            BaseLocationMapper locationMapper,
                            BaseProductMapper productMapper,
                            InvStockMapper stockMapper,
                            SlottingService slottingService,
                            PickingStrategyFactory pickingFactory) {
        this.reportMapper = reportMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.productMapper = productMapper;
        this.stockMapper = stockMapper;
        this.slottingService = slottingService;
        this.pickingFactory = pickingFactory;
    }

    @Override
    public Map<String, Object> dashboard() {
        long locations = reportMapper.countLocations();
        long occupied = reportMapper.countOccupiedLocations();

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("productCount", reportMapper.countProducts());
        data.put("locationCount", locations);
        data.put("occupiedLocationCount", occupied);
        data.put("freeLocationCount", locations - occupied);
        data.put("occupancyRate", locations == 0 ? 0.0
                : round(occupied * 100.0 / locations, 2));
        data.put("stockQuantity", reportMapper.sumStockQuantity());
        data.put("lowStockCount", reportMapper.countLowStockProducts());
        data.put("pendingInboundCount", reportMapper.countPendingInbound());
        data.put("pendingOutboundCount", reportMapper.countPendingOutbound());
        data.put("doneInboundCount", reportMapper.countDoneInbound());
        data.put("doneOutboundCount", reportMapper.countDoneOutbound());
        return data;
    }

    @Override
    public Map<String, Object> operationTrend(int days) {
        int window = Math.max(1, Math.min(days, MAX_TREND_DAYS));
        List<Map<String, Object>> inbound = reportMapper.inboundByDay(window);
        List<Map<String, Object>> outbound = reportMapper.outboundByDay(window);

        // 合并两条序列，按日期对齐，缺失日补 0
        TreeSet<String> dates = new TreeSet<String>();
        Map<String, Map<String, Object>> inboundByDate = indexByDate(inbound, dates);
        Map<String, Map<String, Object>> outboundByDate = indexByDate(outbound, dates);

        List<Map<String, Object>> trend = new ArrayList<Map<String, Object>>();
        long totalInQty = 0;
        long totalOutQty = 0;
        long totalInOrders = 0;
        long totalOutOrders = 0;
        for (String date : dates) {
            Map<String, Object> in = inboundByDate.get(date);
            Map<String, Object> out = outboundByDate.get(date);
            long inOrders = in == null ? 0 : toLong(in.get("orderCount"));
            long inQty = in == null ? 0 : toLong(in.get("totalQuantity"));
            long outOrders = out == null ? 0 : toLong(out.get("orderCount"));
            long outQty = out == null ? 0 : toLong(out.get("totalQuantity"));

            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("statDate", date);
            row.put("inboundOrders", inOrders);
            row.put("inboundQuantity", inQty);
            row.put("outboundOrders", outOrders);
            row.put("outboundQuantity", outQty);
            trend.add(row);

            totalInOrders += inOrders;
            totalInQty += inQty;
            totalOutOrders += outOrders;
            totalOutQty += outQty;
        }

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("days", window);
        summary.put("activeDays", dates.size());
        summary.put("inboundOrders", totalInOrders);
        summary.put("inboundQuantity", totalInQty);
        summary.put("outboundOrders", totalOutOrders);
        summary.put("outboundQuantity", totalOutQty);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("summary", summary);
        data.put("trend", trend);
        return data;
    }

    @Override
    public Map<String, Object> stockDistribution(Long warehouseId) {
        List<Map<String, Object>> byZone = reportMapper.stockDistributionByZone(warehouseId);
        for (Map<String, Object> row : byZone) {
            long locations = toLong(row.get("locationCount"));
            long used = toLong(row.get("usedLocations"));
            row.put("freeLocations", locations - used);
            row.put("usageRate", locations == 0 ? 0.0 : round(used * 100.0 / locations, 2));
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("byZone", byZone);
        data.put("byAbcClass", reportMapper.stockDistributionByAbc());
        return data;
    }

    @Override
    public Map<String, Object> slottingEffect(Long warehouseId) {
        BaseWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException(404, "仓库不存在");
        }
        long productCount = productMapper.selectCount(new LambdaQueryWrapper<BaseProduct>());
        if (productCount == 0) {
            throw new BusinessException(409, "暂无商品数据，无法统计货位分配效果");
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Map<String, Double> objectives = new LinkedHashMap<String, Double>();
        Map<String, Long> elapsed = new LinkedHashMap<String, Long>();
        for (String code : new String[]{"random", "frequency-greedy", "abc-greedy", "genetic"}) {
            try {
                SlottingAssignment assignment = slottingService.solve(warehouseId, code, 20261011L);
                objectives.put(code, round(assignment.getObjective(), 2));
                elapsed.put(code, assignment.getElapsedMillis());
            } catch (BusinessException e) {
                log.warn("slotting effect for strategy {} unavailable: {}", code, e.getMessage());
            }
        }

        Map<String, Object> improvement = new LinkedHashMap<String, Object>();
        Double random = objectives.get("random");
        Double greedy = objectives.get("frequency-greedy");
        if (random != null) {
            for (Map.Entry<String, Double> e : objectives.entrySet()) {
                if ("random".equals(e.getKey())) {
                    continue;
                }
                improvement.put(e.getKey(), round((random - e.getValue()) / random * 100.0, 2));
            }
        }

        data.put("warehouseId", warehouseId);
        data.put("productCount", productCount);
        data.put("slotCount", warehouse.totalSlots());
        data.put("objectives", objectives);
        data.put("elapsedMillis", elapsed);
        data.put("improvementPercent", improvement);
        data.put("greedyVsRandomPercent",
                random != null && greedy != null ? round((random - greedy) / random * 100.0, 2) : null);
        data.put("note", "目标函数为加权拣货行走距离 Σ(出库频次 × 货位到出入口距离)，单位：米·次");
        return data;
    }

    @Override
    public Map<String, Object> pickingEffect(Long warehouseId) {
        BaseWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException(404, "仓库不存在");
        }
        // 以有库存的货位作为拣货点集合。
        // 按货位到出入口的距离升序、再按货位 id 升序排序后再截断：
        // 若直接按数据库返回顺序截断，超过上限时取到哪 120 个货位取决于
        // 底层返回顺序，同样的数据可能得到不同的统计数据，无法复现。
        List<InvStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
                .eq(InvStock::getWarehouseId, warehouseId)
                .gt(InvStock::getQuantity, BigDecimal.ZERO)
                .orderByAsc(InvStock::getLocationId));
        if (stocks.isEmpty()) {
            throw new BusinessException(409, "该仓库暂无库存，无法统计拣货路径效果");
        }

        int aisleCount = warehouse.getAisleCount() == null ? 1 : warehouse.getAisleCount();
        int rowCount = warehouse.getRowCount() == null ? 1 : warehouse.getRowCount();
        double aisleGap = warehouse.getAisleGap() == null ? 2.5 : warehouse.getAisleGap().doubleValue();
        double rowGap = warehouse.getRowGap() == null ? 1.2 : warehouse.getRowGap().doubleValue();

        List<int[]> picks = new ArrayList<int[]>();
        List<double[]> candidates = new ArrayList<double[]>();
        for (InvStock stock : stocks) {
            BaseLocation location = locationMapper.selectById(stock.getLocationId());
            if (location == null || location.getAisleNo() == null || location.getRowNo() == null) {
                continue;
            }
            double distance = aisleGap * location.getAisleNo() + rowGap * location.getRowNo();
            candidates.add(new double[]{distance, location.getAisleNo(), location.getRowNo()});
        }
        // 距离升序、同距离时按坐标升序，保证结果完全确定
        candidates.sort(new java.util.Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                int c = Double.compare(a[0], b[0]);
                if (c != 0) {
                    return c;
                }
                c = Double.compare(a[1], b[1]);
                return c != 0 ? c : Double.compare(a[2], b[2]);
            }
        });
        for (double[] c : candidates) {
            if (picks.size() >= MAX_PICK_POINTS) {
                break;
            }
            int[] point = new int[]{(int) c[1], (int) c[2]};
            boolean exists = false;
            for (int[] p : picks) {
                if (p[0] == point[0] && p[1] == point[1]) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                picks.add(point);
            }
        }
        if (picks.isEmpty()) {
            throw new BusinessException(409, "没有可用的货位坐标，无法统计拣货路径效果");
        }
        // 截断只影响参与统计的货位数量，取样情况一并返回，便于判断口径
        int totalStockLocations = candidates.size();

        WarehouseLayout layout = new WarehouseLayout(aisleCount, rowCount, aisleGap, rowGap);
        PickPath path = new PickPath(layout, picks);

        Map<String, Double> distances = new LinkedHashMap<String, Double>();
        Map<String, Long> elapsed = new LinkedHashMap<String, Long>();
        Map<String, String> labels = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : pickingFactory.available().entrySet()) {
            try {
                PickingStrategy strategy = pickingFactory.get(entry.getKey());
                PickingRoute route = strategy.solve(path, 20261011L);
                distances.put(entry.getKey(), round(route.getDistance(), 2));
                elapsed.put(entry.getKey(), route.getElapsedMillis());
                labels.put(entry.getKey(), strategy.label());
            } catch (RuntimeException e) {
                log.warn("picking effect for strategy {} failed: {}", entry.getKey(), e.getMessage());
            }
        }

        Double baseline = distances.get("s-shape");
        Map<String, Object> improvement = new LinkedHashMap<String, Object>();
        if (baseline != null && baseline > 0) {
            for (Map.Entry<String, Double> e : distances.entrySet()) {
                improvement.put(e.getKey(), round((baseline - e.getValue()) / baseline * 100.0, 2));
            }
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("warehouseId", warehouseId);
        data.put("pickPointCount", picks.size());
        data.put("labels", labels);
        data.put("distances", distances);
        data.put("elapsedMillis", elapsed);
        data.put("improvementVsSShapePercent", improvement);
        // 取样信息：有库存的货位可能超过统计上限，此处说明实际参与统计的数量
        data.put("stockLocationCount", totalStockLocations);
        data.put("sampled", totalStockLocations > picks.size());
        data.put("note", "以当前有库存的货位为拣货点集合（按距出入口距离升序取样），"
                + "改善比例相对传统 S 型穿越策略计算");
        return data;
    }

    @Override
    public List<Map<String, Object>> routeAlgorithmStatistics() {
        return outboundOrderMapper.routeAlgorithmStatistics();
    }

    @Override
    public Map<String, Object> frequencyContribution(int limit) {
        int top = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> rows = reportMapper.topFrequencyProducts(top);

        // 分母必须是全部商品的频次合计，而不是这 top 条的合计：
        // 后者在商品总数超过 top 时会抬高占比，使“贡献 70% 频次所需的商品数”
        // 偏小，与 ABC 分类（按全量频次累加划分）产生口径矛盾。
        long totalFreq = reportMapper.sumAllPickFrequency();

        BigDecimal total = BigDecimal.valueOf(totalFreq);
        // 累计占比：用于说明“少数商品贡献多数出库量”
        BigDecimal cumulative = BigDecimal.ZERO;
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        int crossingIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            long freq = toLong(row.get("pickFreq"));
            cumulative = cumulative.add(BigDecimal.valueOf(freq));
            double ratio = totalFreq <= 0 ? 0.0
                    : cumulative.doubleValue() / (double) totalFreq * 100.0;
            if (crossingIndex < 0 && ratio >= 70.0) {
                crossingIndex = i + 1;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>(row);
            item.put("cumulativeRatio", round(ratio, 2));
            items.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("topCount", top);
        data.put("frequencySum", total);
        // 明示统计范围，避免把 top 条合计误读为全量合计
        data.put("totalProductCount", reportMapper.countAllProducts());
        data.put("items", items);
        data.put("productsFor70Percent", crossingIndex < 0 ? null : crossingIndex);
        data.put("note", "累计出库频次占比达到 70% 所需的商品数，用于说明 ABC 分类的划分依据；"
                + "分母为全部商品的出库频次合计，与 ABC 分类口径一致");
        return data;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 把按日期分组的行放入 map，并同步收集日期集合 */
    private Map<String, Map<String, Object>> indexByDate(List<Map<String, Object>> rows, TreeSet<String> dates) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Object date = row.get("statDate");
            if (date == null) {
                continue;
            }
            String key = String.valueOf(date);
            map.put(key, row);
            dates.add(key);
        }
        return map;
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return new BigDecimal(String.valueOf(v)).longValue();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
