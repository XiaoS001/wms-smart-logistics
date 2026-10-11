package com.lijunjie.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lijunjie.wms.algorithm.SlottingAssignment;
import com.lijunjie.wms.algorithm.SlottingStrategyFactory;
import com.lijunjie.wms.algorithm.WarehouseLayout;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.entity.BaseLocation;
import com.lijunjie.wms.entity.BaseProduct;
import com.lijunjie.wms.entity.BaseWarehouse;
import com.lijunjie.wms.entity.BizSlottingPlan;
import com.lijunjie.wms.entity.InvStock;
import com.lijunjie.wms.mapper.BaseLocationMapper;
import com.lijunjie.wms.mapper.BaseProductMapper;
import com.lijunjie.wms.mapper.BaseWarehouseMapper;
import com.lijunjie.wms.mapper.BizSlottingPlanMapper;
import com.lijunjie.wms.mapper.InvStockMapper;
import com.lijunjie.wms.service.SlottingPlanService;
import com.lijunjie.wms.service.SlottingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 货位优化方案业务实现。
 *
 * <p>关键设计：算法求解结果是“抽象货位编号”，必须映射回真实货位记录才能落库与执行。
 * 映射规则与算法内核一致——<b>货位按到出入口距离升序、距离相同按 ID 升序编号</b>，
 * 即编号 k 对应第 k 个最近的货位。这样“抽象解 → 真实货位”的转换是确定且可复现的。</p>
 */
@Service
public class SlottingPlanServiceImpl implements SlottingPlanService {

    private static final Logger log = LoggerFactory.getLogger(SlottingPlanServiceImpl.class);

    /** 货位状态：空闲 */
    private static final int LOCATION_FREE = 1;

    /** 货位状态：占用 */
    private static final int LOCATION_USED = 2;

    /** 预览中返回的搬迁明细上限，避免响应过大 */
    private static final int MAX_PREVIEW_MOVES = 200;

    private final BizSlottingPlanMapper planMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final BaseLocationMapper locationMapper;
    private final BaseProductMapper productMapper;
    private final InvStockMapper stockMapper;
    private final SlottingService slottingService;
    private final SlottingStrategyFactory strategyFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SlottingPlanServiceImpl(BizSlottingPlanMapper planMapper,
                                   BaseWarehouseMapper warehouseMapper,
                                   BaseLocationMapper locationMapper,
                                   BaseProductMapper productMapper,
                                   InvStockMapper stockMapper,
                                   SlottingService slottingService,
                                   SlottingStrategyFactory strategyFactory) {
        this.planMapper = planMapper;
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.productMapper = productMapper;
        this.stockMapper = stockMapper;
        this.slottingService = slottingService;
        this.strategyFactory = strategyFactory;
    }

    @Override
    public Map<String, Object> preview(Long warehouseId, String strategyCode, long seed) {
        BaseWarehouse warehouse = requireWarehouse(warehouseId);
        PlanComputation computation = compute(warehouse, strategyCode, seed);
        return buildPreview(warehouseId, strategyCode, seed, computation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> savePlan(Long warehouseId, String strategyCode, long seed, String remark) {
        BaseWarehouse warehouse = requireWarehouse(warehouseId);
        PlanComputation computation = compute(warehouse, strategyCode, seed);
        Map<String, Object> preview = buildPreview(warehouseId, strategyCode, seed, computation);

        BizSlottingPlan plan = new BizSlottingPlan();
        plan.setPlanNo(nextPlanNo());
        plan.setWarehouseId(warehouseId);
        plan.setStrategy(strategyCode);
        plan.setStrategyLabel(computation.strategyLabel);
        plan.setSeed(seed);
        plan.setProductCount(computation.products.size());
        plan.setSlotCount(computation.locations.size());
        plan.setCurrentObjective(toMoney(computation.currentObjective));
        plan.setOptimizedObjective(toMoney(computation.optimizedObjective));
        plan.setImprovementPercent(toMoney(computation.improvementPercent));
        plan.setElapsedMillis(computation.elapsedMillis);
        plan.setApplied(0);
        plan.setPlanDetail(writeJson(computation.slotOfProduct));
        plan.setRemark(remark);
        plan.setCreateTime(new Date());
        planMapper.insert(plan);

        log.info("slotting plan saved: no={}, warehouse={}, strategy={}, current={}, optimized={}, improvement={}%",
                plan.getPlanNo(), warehouseId, strategyCode, plan.getCurrentObjective(),
                plan.getOptimizedObjective(), plan.getImprovementPercent());

        Map<String, Object> result = new LinkedHashMap<String, Object>(preview);
        result.put("planId", plan.getId());
        result.put("planNo", plan.getPlanNo());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyPlan(Long planId) {
        BizSlottingPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(404, "优化方案不存在");
        }
        if (plan.getApplied() != null && plan.getApplied() == 1) {
            throw new BusinessException(409, "该方案已应用，不能重复应用");
        }
        int[] slotOfProduct = readJson(plan.getPlanDetail());
        if (slotOfProduct.length == 0) {
            throw new BusinessException(409, "方案缺少分配明细，无法应用");
        }

        BaseWarehouse warehouse = requireWarehouse(plan.getWarehouseId());
        List<BaseLocation> locations = sortedLocations(warehouse.getId());
        List<BaseProduct> products = sortedProducts();
        if (slotOfProduct.length != products.size()) {
            throw new BusinessException(409, "商品数据已变化（方案记录 " + slotOfProduct.length
                    + " 个商品，当前 " + products.size() + " 个），请重新生成方案");
        }

        // 每个商品的库存记录，按数量降序；只搬第一条（数量最大），其余批次保持不动
        List<InvStock> allStock = stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
                .eq(InvStock::getWarehouseId, warehouse.getId())
                .gt(InvStock::getQuantity, BigDecimal.ZERO));
        Map<Long, List<InvStock>> stockByProduct = new LinkedHashMap<Long, List<InvStock>>();
        for (InvStock stock : allStock) {
            List<InvStock> list = stockByProduct.get(stock.getProductId());
            if (list == null) {
                list = new ArrayList<InvStock>();
                stockByProduct.put(stock.getProductId(), list);
            }
            list.add(stock);
        }
        for (List<InvStock> list : stockByProduct.values()) {
            list.sort(Comparator.comparing(InvStock::getQuantity).reversed());
        }

        List<Map<String, Object>> moves = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> skipped = new ArrayList<Map<String, Object>>();
        int moved = 0;

        for (int i = 0; i < products.size(); i++) {
            BaseProduct product = products.get(i);
            List<InvStock> stocks = stockByProduct.get(product.getId());
            if (stocks == null || stocks.isEmpty()) {
                continue;   // 无库存的商品无需搬迁
            }
            int targetSlot = slotOfProduct[i];
            if (targetSlot < 0 || targetSlot >= locations.size()) {
                throw new BusinessException(409, "方案中的货位编号越界：" + targetSlot);
            }
            BaseLocation target = locations.get(targetSlot);
            InvStock primary = stocks.get(0);
            if (target.getId().equals(primary.getLocationId())) {
                continue;   // 已在目标货位
            }

            Long fromLocationId = primary.getLocationId();
            BaseLocation from = locationMapper.selectById(fromLocationId);

            InvStock update = new InvStock();
            update.setId(primary.getId());
            update.setLocationId(target.getId());
            update.setUpdateTime(new Date());
            stockMapper.updateById(update);

            // 注意：这里不逐条维护货位占用状态。
            // 搬迁过程中“A 的原货位”可能正是“B 的目标货位”，若在每个商品处理时
            // 立即释放原货位，会把另一个商品刚占上的货位错误地置为空闲
            // （实测表现为 3 条库存却只有 2 个货位处于占用状态）。
            // 因此循环内只搬数据，循环结束后按最终库存统一重算。
            moved++;

            if (moves.size() < MAX_PREVIEW_MOVES) {
                Map<String, Object> move = new LinkedHashMap<String, Object>();
                move.put("productId", product.getId());
                move.put("productCode", product.getProductCode());
                move.put("pickFreq", product.getPickFreq());
                move.put("abcClass", product.getAbcClass());
                move.put("quantity", primary.getQuantity());
                move.put("fromLocationId", fromLocationId);
                move.put("fromLocationCode", from == null ? null : from.getLocationCode());
                move.put("fromDistance", from == null || from.getDistance() == null ? null : from.getDistance().doubleValue());
                move.put("toLocationId", target.getId());
                move.put("toLocationCode", target.getLocationCode());
                move.put("toDistance", target.getDistance() == null ? null : target.getDistance().doubleValue());
                moves.add(move);
            }
            if (stocks.size() > 1) {
                Map<String, Object> note = new LinkedHashMap<String, Object>();
                note.put("productCode", product.getProductCode());
                note.put("extraBatchRecords", stocks.size() - 1);
                note.put("note", "该商品有多个批次记录，仅搬迁数量最大的一条，其余保持原位");
                skipped.add(note);
            }
        }

        BizSlottingPlan update = new BizSlottingPlan();
        update.setId(planId);
        update.setApplied(1);
        update.setAppliedTime(new Date());
        update.setMovedRecords(moved);
        planMapper.updateById(update);

        // 按最终库存重算该仓库的货位占用状态，保证与库存完全一致
        int recalculated = recalculateLocationStatus(warehouse.getId());

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("planId", planId);
        result.put("planNo", plan.getPlanNo());
        result.put("strategy", plan.getStrategy());
        result.put("movedRecords", moved);
        result.put("occupiedLocations", recalculated);
        result.put("moves", moves);
        result.put("multiBatchNotes", skipped);
        result.put("currentObjective", plan.getCurrentObjective());
        result.put("optimizedObjective", plan.getOptimizedObjective());
        result.put("improvementPercent", plan.getImprovementPercent());
        log.info("slotting plan applied: no={}, movedRecords={}, multiBatch={}",
                plan.getPlanNo(), moved, skipped.size());
        return result;
    }

    @Override
    public List<Map<String, Object>> listPlans(Long warehouseId, Integer applied, int limit) {
        LambdaQueryWrapper<BizSlottingPlan> q = new LambdaQueryWrapper<BizSlottingPlan>();
        if (warehouseId != null) {
            q.eq(BizSlottingPlan::getWarehouseId, warehouseId);
        }
        if (applied != null) {
            q.eq(BizSlottingPlan::getApplied, applied);
        }
        q.orderByDesc(BizSlottingPlan::getId);
        q.last("LIMIT " + Math.max(1, Math.min(limit, 200)));

        List<BizSlottingPlan> plans = planMapper.selectList(q);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (BizSlottingPlan plan : plans) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", plan.getId());
            row.put("planNo", plan.getPlanNo());
            row.put("warehouseId", plan.getWarehouseId());
            row.put("strategy", plan.getStrategy());
            row.put("strategyLabel", plan.getStrategyLabel());
            row.put("productCount", plan.getProductCount());
            row.put("slotCount", plan.getSlotCount());
            row.put("currentObjective", plan.getCurrentObjective());
            row.put("optimizedObjective", plan.getOptimizedObjective());
            row.put("improvementPercent", plan.getImprovementPercent());
            row.put("elapsedMillis", plan.getElapsedMillis());
            row.put("applied", plan.getApplied());
            row.put("appliedTime", plan.getAppliedTime());
            row.put("movedRecords", plan.getMovedRecords());
            row.put("remark", plan.getRemark());
            row.put("createTime", plan.getCreateTime());
            rows.add(row);
        }
        return rows;
    }

    @Override
    public Map<String, Object> planDetail(Long planId) {
        BizSlottingPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(404, "优化方案不存在");
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", plan.getId());
        row.put("planNo", plan.getPlanNo());
        row.put("warehouseId", plan.getWarehouseId());
        row.put("strategy", plan.getStrategy());
        row.put("strategyLabel", plan.getStrategyLabel());
        row.put("seed", plan.getSeed());
        row.put("productCount", plan.getProductCount());
        row.put("slotCount", plan.getSlotCount());
        row.put("currentObjective", plan.getCurrentObjective());
        row.put("optimizedObjective", plan.getOptimizedObjective());
        row.put("improvementPercent", plan.getImprovementPercent());
        row.put("elapsedMillis", plan.getElapsedMillis());
        row.put("applied", plan.getApplied());
        row.put("appliedTime", plan.getAppliedTime());
        row.put("movedRecords", plan.getMovedRecords());
        row.put("remark", plan.getRemark());
        row.put("createTime", plan.getCreateTime());
        row.put("slotOfProduct", readJsonAsList(plan.getPlanDetail()));
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long planId) {
        BizSlottingPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(404, "优化方案不存在");
        }
        if (plan.getApplied() != null && plan.getApplied() == 1) {
            throw new BusinessException(409, "已应用的方案不能删除，请保留作为优化记录");
        }
        planMapper.deleteById(planId);
        log.info("slotting plan deleted: no={}", plan.getPlanNo());
    }

    @Override
    public List<Map<String, Object>> statisticsByStrategy() {
        return planMapper.statisticsByStrategy();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 一次求解所需的全部中间结果 */
    private static class PlanComputation {
        String strategyLabel;
        double currentObjective;
        double optimizedObjective;
        double improvementPercent;
        double currentAverageDistance;
        double optimizedAverageDistance;
        long elapsedMillis;
        List<BaseProduct> products;
        List<BaseLocation> locations;
        int[] slotOfProduct;
    }

    /** 求解并把抽象货位编号映射为真实货位 */
    private PlanComputation compute(BaseWarehouse warehouse, String strategyCode, long seed) {
        List<BaseProduct> products = sortedProducts();
        if (products.isEmpty()) {
            throw new BusinessException(409, "暂无商品数据，无法执行货位优化");
        }
        List<BaseLocation> locations = sortedLocations(warehouse.getId());
        if (locations.isEmpty()) {
            throw new BusinessException(409, "该仓库暂无货位，请先生成货位");
        }
        if (products.size() > locations.size()) {
            throw new BusinessException(409, "商品数(" + products.size()
                    + ") 超过货位数(" + locations.size() + ")，无法执行货位优化");
        }

        SlottingAssignment assignment = slottingService.solve(warehouse.getId(), strategyCode, seed);

        PlanComputation c = new PlanComputation();
        c.strategyLabel = strategyFactory.get(strategyCode).label();
        c.optimizedObjective = assignment.getObjective();
        c.elapsedMillis = assignment.getElapsedMillis();
        c.products = products;
        c.locations = locations;
        c.slotOfProduct = assignment.getSlotOfItem();

        // 优化前：按库存实际所在货位计算加权行走距离
        Map<String, Object> current = planMapper.currentObjective(warehouse.getId());
        c.currentObjective = current == null || current.get("objective") == null
                ? 0.0 : toDouble(current.get("objective"));

        // 平均加权行走距离 = 目标函数 / 频次合计，便于横向比较
        double freqSum = 0;
        for (BaseProduct p : products) {
            freqSum += (p.getPickFreq() == null ? 0 : p.getPickFreq());
        }
        c.currentAverageDistance = freqSum <= 0 ? 0 : c.currentObjective / freqSum;
        c.optimizedAverageDistance = freqSum <= 0 ? 0 : c.optimizedObjective / freqSum;
        c.improvementPercent = c.currentObjective <= 0 ? 0
                : (c.currentObjective - c.optimizedObjective) / c.currentObjective * 100.0;

        log.info("slotting computed: warehouse={}, strategy={}, products={}, locations={}, "
                        + "current={}, optimized={}, improvement={}%",
                warehouse.getId(), strategyCode, products.size(), locations.size(),
                round(c.currentObjective, 2), round(c.optimizedObjective, 2), round(c.improvementPercent, 2));
        return c;
    }

    /** 组装预览结果，含分配明细与搬迁预览 */
    private Map<String, Object> buildPreview(Long warehouseId, String strategyCode, long seed,
                                             PlanComputation c) {
        // 每个商品的库存位置（用于判断是否需要搬迁）
        List<InvStock> allStock = stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
                .eq(InvStock::getWarehouseId, warehouseId)
                .gt(InvStock::getQuantity, BigDecimal.ZERO));
        Map<Long, InvStock> primaryStock = new LinkedHashMap<Long, InvStock>();
        for (InvStock stock : allStock) {
            InvStock exist = primaryStock.get(stock.getProductId());
            if (exist == null || stock.getQuantity().compareTo(exist.getQuantity()) > 0) {
                primaryStock.put(stock.getProductId(), stock);
            }
        }

        List<Map<String, Object>> assignments = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> moves = new ArrayList<Map<String, Object>>();
        int sameLocation = 0;
        int noStock = 0;

        for (int i = 0; i < c.products.size(); i++) {
            BaseProduct product = c.products.get(i);
            BaseLocation target = c.locations.get(c.slotOfProduct[i]);
            InvStock stock = primaryStock.get(product.getId());

            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("productId", product.getId());
            row.put("productCode", product.getProductCode());
            row.put("productName", product.getProductName());
            row.put("pickFreq", product.getPickFreq());
            row.put("abcClass", product.getAbcClass());
            row.put("targetLocationId", target.getId());
            row.put("targetLocationCode", target.getLocationCode());
            row.put("targetDistance", target.getDistance() == null ? null : target.getDistance().doubleValue());
            assignments.add(row);

            if (stock == null) {
                noStock++;
                continue;
            }
            BaseLocation from = locationMapper.selectById(stock.getLocationId());
            row.put("currentLocationId", stock.getLocationId());
            row.put("currentLocationCode", from == null ? null : from.getLocationCode());
            row.put("currentDistance", from == null || from.getDistance() == null ? null : from.getDistance().doubleValue());

            if (target.getId().equals(stock.getLocationId())) {
                sameLocation++;
                continue;
            }
            if (moves.size() < MAX_PREVIEW_MOVES) {
                Map<String, Object> move = new LinkedHashMap<String, Object>();
                move.put("productId", product.getId());
                move.put("productCode", product.getProductCode());
                move.put("pickFreq", product.getPickFreq());
                move.put("quantity", stock.getQuantity());
                move.put("fromLocationCode", from == null ? null : from.getLocationCode());
                move.put("fromDistance", from == null || from.getDistance() == null ? null : from.getDistance().doubleValue());
                move.put("toLocationCode", target.getLocationCode());
                move.put("toDistance", target.getDistance() == null ? null : target.getDistance().doubleValue());
                move.put("distanceSaving", from == null || from.getDistance() == null || target.getDistance() == null
                        ? null : round(from.getDistance().subtract(target.getDistance()).doubleValue(), 2));
                moves.add(move);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("productCount", c.products.size());
        summary.put("locationCount", c.locations.size());
        summary.put("stockedProducts", primaryStock.size());
        summary.put("productsWithoutStock", noStock);
        summary.put("alreadyInPlace", sameLocation);
        summary.put("movesNeeded", moves.size());
        summary.put("currentObjective", round(c.currentObjective, 2));
        summary.put("optimizedObjective", round(c.optimizedObjective, 2));
        summary.put("currentAverageDistance", round(c.currentAverageDistance, 3));
        summary.put("optimizedAverageDistance", round(c.optimizedAverageDistance, 3));
        summary.put("improvementPercent", round(c.improvementPercent, 2));
        summary.put("elapsedMillis", c.elapsedMillis);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("warehouseId", warehouseId);
        data.put("strategy", strategyCode);
        data.put("strategyLabel", c.strategyLabel);
        data.put("seed", seed);
        data.put("summary", summary);
        data.put("moves", moves);
        data.put("assignments", assignments);
        data.put("note", "目标函数为加权拣货行走距离 Σ(出库频次 × 货位到出入口距离)，单位：米·次；"
                + "improvementPercent 为优化后相对当前实际布局的降幅");
        return data;
    }

    private BaseWarehouse requireWarehouse(Long warehouseId) {
        BaseWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException(404, "仓库不存在");
        }
        if (warehouse.getAisleCount() == null || warehouse.getRowCount() == null
                || warehouse.getAisleCount() <= 0 || warehouse.getRowCount() <= 0) {
            throw new BusinessException(409, "仓库未配置布局参数（巷道数与纵深数），无法执行货位优化");
        }
        return warehouse;
    }

    /**
     * 货位按到出入口距离升序排列，距离相同按 ID 升序。
     *
     * <p>该顺序即算法中“抽象货位编号”的含义，二者必须严格一致，
     * 否则求解结果映射到真实货位时会整体偏移。</p>
     */
    private List<BaseLocation> sortedLocations(Long warehouseId) {
        return locationMapper.selectList(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getWarehouseId, warehouseId)
                .orderByAsc(BaseLocation::getDistance)
                .orderByAsc(BaseLocation::getId));
    }

    /** 商品按 ID 升序，与算法算例的构造顺序一致 */
    private List<BaseProduct> sortedProducts() {
        return productMapper.selectList(new LambdaQueryWrapper<BaseProduct>()
                .orderByAsc(BaseProduct::getId));
    }

    private void setLocationStatus(Long locationId, int status) {
        BaseLocation update = new BaseLocation();
        update.setId(locationId);
        update.setStatus(status);
        locationMapper.updateById(update);
    }

    /**
     * 按最终库存重算某仓库的货位占用状态。
     *
     * <p>先把该仓库全部货位置为空闲，再把有库存的货位置为占用。
     * 相比在搬迁过程中逐条维护状态，这种方式与库存的最终状态天然一致，
     * 不受搬迁先后顺序影响。</p>
     *
     * @param warehouseId 仓库 ID
     * @return 重算后有库存的货位数
     */
    private int recalculateLocationStatus(Long warehouseId) {
        BaseLocation clear = new BaseLocation();
        clear.setStatus(LOCATION_FREE);
        locationMapper.update(clear, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BaseLocation>()
                .eq(BaseLocation::getWarehouseId, warehouseId)
                .eq(BaseLocation::getStatus, LOCATION_USED));

        List<InvStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
                .eq(InvStock::getWarehouseId, warehouseId)
                .gt(InvStock::getQuantity, BigDecimal.ZERO));
        java.util.Set<Long> occupied = new java.util.HashSet<Long>();
        for (InvStock stock : stocks) {
            occupied.add(stock.getLocationId());
        }
        for (Long locationId : occupied) {
            setLocationStatus(locationId, LOCATION_USED);
        }
        log.info("location status recalculated: warehouse={}, occupiedLocations={}, stockRows={}",
                warehouseId, occupied.size(), stocks.size());
        return occupied.size();
    }

    private String nextPlanNo() {
        String prefix = "SP" + new SimpleDateFormat("yyyyMMdd").format(new Date());
        String maxNo = planMapper.selectMaxPlanNo(prefix);
        int seq = 1;
        if (maxNo != null && maxNo.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(maxNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        return prefix + String.format("%04d", seq);
    }

    private String writeJson(int[] values) {
        try {
            List<Integer> list = new ArrayList<Integer>(values.length);
            for (int v : values) {
                list.add(v);
            }
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("failed to serialize plan detail: {}", e.getMessage());
            return "[]";
        }
    }

    private List<Integer> readJsonAsList(String json) {
        List<Integer> list = new ArrayList<Integer>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            int[] values = objectMapper.readValue(json, int[].class);
            for (int v : values) {
                list.add(v);
            }
        } catch (Exception e) {
            log.warn("failed to parse plan detail: {}", e.getMessage());
        }
        return list;
    }

    private int[] readJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new int[0];
        }
        try {
            return objectMapper.readValue(json, int[].class);
        } catch (Exception e) {
            log.warn("failed to parse plan detail: {}", e.getMessage());
            return new int[0];
        }
    }

    private static BigDecimal toMoney(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static double toDouble(Object v) {
        if (v == null) {
            return 0.0;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return Double.parseDouble(String.valueOf(v));
    }
}
