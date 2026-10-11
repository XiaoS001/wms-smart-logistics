package com.lijunjie.wms.service;

import java.util.Map;

/**
 * 货位优化方案业务接口。
 *
 * <p>本模块把货位分配算法的求解结果沉淀为可管理的“方案”：
 * 预览 → 保存 → 应用 → 查询历史。方案中同时记录优化前后的加权行走距离，
 * 使优化效果有据可查。</p>
 */
public interface SlottingPlanService {

    /**
     * 生成优化方案预览（不落库）。
     *
     * @param warehouseId  仓库 ID
     * @param strategyCode 策略编码
     * @param seed         随机种子
     * @return 含当前与优化后目标值、降幅、分配明细与搬迁预览
     */
    Map<String, Object> preview(Long warehouseId, String strategyCode, long seed);

    /**
     * 生成并保存优化方案。
     *
     * @param warehouseId  仓库 ID
     * @param strategyCode 策略编码
     * @param seed         随机种子
     * @param remark       备注
     * @return 已保存的方案
     */
    Map<String, Object> savePlan(Long warehouseId, String strategyCode, long seed, String remark);

    /**
     * 应用方案：把商品对应的库存记录搬到优化后的货位。
     *
     * <p>整个操作在同一事务中完成，任何一步冲突都会整体回滚，
     * 不会出现“搬了一半”的中间状态。每个商品只搬运其数量最大的一条库存记录，
     * 其余批次记录保持不变（避免同一商品的多个批次争抢同一个货位）。</p>
     *
     * @param planId 方案 ID
     * @return 搬迁结果统计
     */
    Map<String, Object> applyPlan(Long planId);

    /** 查询优化方案历史 */
    java.util.List<Map<String, Object>> listPlans(Long warehouseId, Integer applied, int limit);

    /** 查询方案详情（含分配明细） */
    Map<String, Object> planDetail(Long planId);

    /** 删除未应用的方案 */
    void deletePlan(Long planId);

    /** 按策略统计历史优化效果 */
    java.util.List<Map<String, Object>> statisticsByStrategy();
}
