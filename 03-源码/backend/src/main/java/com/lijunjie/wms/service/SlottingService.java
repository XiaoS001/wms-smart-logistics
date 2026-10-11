package com.lijunjie.wms.service;

import com.lijunjie.wms.algorithm.SlottingAssignment;
import com.lijunjie.wms.algorithm.SlottingInstance;
import com.lijunjie.wms.algorithm.SlottingStrategy;
import com.lijunjie.wms.algorithm.SlottingStrategyFactory;
import com.lijunjie.wms.algorithm.WarehouseLayout;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.dto.SlottingRecommendResult;
import com.lijunjie.wms.entity.BaseLocation;
import com.lijunjie.wms.entity.BaseProduct;
import com.lijunjie.wms.entity.BaseWarehouse;
import com.lijunjie.wms.mapper.BaseLocationMapper;
import com.lijunjie.wms.mapper.BaseProductMapper;
import com.lijunjie.wms.mapper.BaseWarehouseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 货位分配算法服务：把算法内核接入业务流程。
 *
 * <p>本服务是“算法”与“业务”的桥梁：</p>
 * <ul>
 *   <li>从数据库读取仓库布局与商品出库频次，构造算法算例；</li>
 *   <li>调用 {@link SlottingStrategyFactory} 中的策略求解；</li>
 *   <li>把求解结果映射回真实的货位记录，供入库等业务使用。</li>
 * </ul>
 */
@Service
public class SlottingService {

    private static final Logger log = LoggerFactory.getLogger(SlottingService.class);

    /** 单次推荐最多涉及的货位数，避免大仓库下的排序开销 */
    private static final int MAX_RECOMMEND_SLOTS = 2000;

    private final SlottingStrategyFactory strategyFactory;
    private final BaseWarehouseMapper warehouseMapper;
    private final BaseLocationMapper locationMapper;
    private final BaseProductMapper productMapper;

    public SlottingService(SlottingStrategyFactory strategyFactory,
                           BaseWarehouseMapper warehouseMapper,
                           BaseLocationMapper locationMapper,
                           BaseProductMapper productMapper) {
        this.strategyFactory = strategyFactory;
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.productMapper = productMapper;
    }

    /** 可用策略列表，供前端下拉框使用 */
    public Map<String, String> availableStrategies() {
        return strategyFactory.available();
    }

    /**
     * 对整仓执行货位分配求解（用于货位优化模块与算法实验对照）。
     *
     * @param warehouseId 仓库 ID
     * @param strategyCode 策略编码
     * @param seed         随机种子
     * @return 分配结果
     */
    public SlottingAssignment solve(Long warehouseId, String strategyCode, long seed) {
        BaseWarehouse warehouse = requireWarehouse(warehouseId);
        WarehouseLayout layout = toLayout(warehouse);

        List<BaseProduct> products = productMapper.selectList(new LambdaQueryWrapper<BaseProduct>()
                .orderByAsc(BaseProduct::getId));
        if (products.isEmpty()) {
            throw new BusinessException(409, "暂无商品数据，无法执行货位分配");
        }
        if (products.size() > layout.slotCount()) {
            throw new BusinessException(409, "商品数(" + products.size()
                    + ") 超过货位数(" + layout.slotCount() + ")，请先扩充货位或减少商品");
        }

        double[] freqs = new double[products.size()];
        for (int i = 0; i < products.size(); i++) {
            Integer f = products.get(i).getPickFreq();
            freqs[i] = f == null ? 0 : f.doubleValue();
        }
        SlottingInstance instance = new SlottingInstance(layout, freqs);

        SlottingStrategy strategy = strategyFactory.get(strategyCode);
        SlottingAssignment assignment = strategy.solve(instance, seed);
        log.info("slotting solved: warehouse={}, strategy={}, items={}, objective={}, elapsed={}ms",
                warehouseId, strategyCode, products.size(), assignment.getObjective(), assignment.getElapsedMillis());
        return assignment;
    }

    /**
     * 为一批待入库商品推荐货位。
     *
     * @param warehouseId 仓库 ID
     * @param productIds  本次入库的商品 ID 列表；为空时取频次最高的若干商品
     * @param strategyCode 策略编码
     * @param seed        随机种子
     * @return 推荐结果（含全局目标函数值与逐商品推荐货位）
     */
    public SlottingRecommendResult recommend(Long warehouseId, List<Long> productIds,
                                             String strategyCode, long seed) {
        long start = System.currentTimeMillis();
        BaseWarehouse warehouse = requireWarehouse(warehouseId);
        WarehouseLayout layout = toLayout(warehouse);

        // 1. 全局求解：给出目标函数值，可与论文实验数据对照
        SlottingAssignment assignment = solve(warehouseId, strategyCode, seed);
        SlottingStrategy strategy = strategyFactory.get(strategyCode);

        List<BaseProduct> allProducts = productMapper.selectList(new LambdaQueryWrapper<BaseProduct>()
                .orderByAsc(BaseProduct::getId));
        double freqSum = 0;
        for (BaseProduct p : allProducts) {
            freqSum += (p.getPickFreq() == null ? 0 : p.getPickFreq());
        }

        SlottingRecommendResult result = new SlottingRecommendResult();
        result.setStrategy(strategyCode);
        result.setStrategyLabel(strategy.label());
        result.setObjective(assignment.getObjective());
        result.setAverageDistance(freqSum <= 0 ? 0
                : BigDecimal.valueOf(assignment.getObjective() / freqSum)
                        .setScale(3, RoundingMode.HALF_UP).doubleValue());
        result.setItemCount(allProducts.size());
        result.setSlotCount(layout.slotCount());
        result.setElapsedMillis(System.currentTimeMillis() - start);

        // 2. 空闲货位按距离升序
        List<BaseLocation> free = locationMapper.selectList(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getWarehouseId, warehouseId)
                .eq(BaseLocation::getStatus, 1)
                .orderByAsc(BaseLocation::getDistance)
                .orderByAsc(BaseLocation::getId)
                .last("LIMIT " + MAX_RECOMMEND_SLOTS));
        result.setFreeSlotCount(free.size());

        if (free.isEmpty()) {
            log.warn("warehouse {} has no free locations; recommendation skipped", warehouseId);
            return result;
        }

        // 3. 本次入库商品按出库频次降序，优先获得近端货位
        List<BaseProduct> targets = new ArrayList<BaseProduct>();
        if (productIds != null && !productIds.isEmpty()) {
            Set<Long> wanted = new HashSet<Long>(productIds);
            for (BaseProduct p : allProducts) {
                if (wanted.contains(p.getId())) {
                    targets.add(p);
                }
            }
        } else {
            targets.addAll(allProducts);
        }
        targets.sort(Comparator.comparingInt((BaseProduct p) -> -(p.getPickFreq() == null ? 0 : p.getPickFreq())));

        int limit = Math.min(targets.size(), free.size());
        for (int i = 0; i < limit; i++) {
            BaseProduct product = targets.get(i);
            BaseLocation location = free.get(i);

            SlottingRecommendResult.Recommendation rec = new SlottingRecommendResult.Recommendation();
            rec.setProductId(product.getId());
            rec.setProductCode(product.getProductCode());
            rec.setProductName(product.getProductName());
            rec.setPickFreq(product.getPickFreq());
            rec.setAbcClass(product.getAbcClass());
            rec.setLocationId(location.getId());
            rec.setLocationCode(location.getLocationCode());
            rec.setDistance(location.getDistance() == null ? null : location.getDistance().doubleValue());
            rec.setReason(buildReason(product, location, i));
            result.getRecommendations().add(rec);
        }

        if (targets.size() > free.size()) {
            log.warn("warehouse {}: {} products requested but only {} free locations available",
                    warehouseId, targets.size(), free.size());
        }
        result.setElapsedMillis(System.currentTimeMillis() - start);
        log.info("slotting recommendation done: warehouse={}, strategy={}, requested={}, recommended={}, free={}",
                warehouseId, strategyCode, targets.size(), result.getRecommendations().size(), free.size());
        return result;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private BaseWarehouse requireWarehouse(Long warehouseId) {
        BaseWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException(404, "仓库不存在");
        }
        if (warehouse.getAisleCount() == null || warehouse.getRowCount() == null
                || warehouse.getAisleCount() <= 0 || warehouse.getRowCount() <= 0) {
            throw new BusinessException(409, "仓库未配置布局参数（巷道数与纵深数），无法执行算法");
        }
        return warehouse;
    }

    private WarehouseLayout toLayout(BaseWarehouse warehouse) {
        double aisleGap = warehouse.getAisleGap() == null ? 2.5 : warehouse.getAisleGap().doubleValue();
        double rowGap = warehouse.getRowGap() == null ? 1.2 : warehouse.getRowGap().doubleValue();
        return new WarehouseLayout(warehouse.getAisleCount(), warehouse.getRowCount(), aisleGap, rowGap);
    }

    /** 生成可读的推荐理由，让用户理解算法的判断依据 */
    private String buildReason(BaseProduct product, BaseLocation location, int rank) {
        String abc = product.getAbcClass() == null ? "C" : product.getAbcClass();
        Integer freq = product.getPickFreq() == null ? 0 : product.getPickFreq();
        BigDecimal distance = location.getDistance();

        StringBuilder sb = new StringBuilder();
        if ("A".equalsIgnoreCase(abc)) {
            sb.append("A 类高频商品");
        } else if ("B".equalsIgnoreCase(abc)) {
            sb.append("B 类中频商品");
        } else {
            sb.append("C 类低频商品");
        }
        sb.append("（出库频次 ").append(freq).append("）");
        sb.append("，按频次降序排在第 ").append(rank + 1).append(" 位");
        if (distance != null) {
            sb.append("，分配距出入口 ").append(distance.setScale(2, RoundingMode.HALF_UP)).append(" 米的空闲货位");
        }
        return sb.toString();
    }
}
