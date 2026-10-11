package com.lijunjie.wms.service;

import java.util.List;
import java.util.Map;

/**
 * 报表统计业务接口。
 */
public interface ReportService {

    /** 首页概览：商品、货位、库存、单据与预警的关键指标 */
    Map<String, Object> dashboard();

    /**
     * 作业量趋势：按日期统计入库与出库的单量和数量。
     *
     * @param days 最近天数
     * @return 含 inbound、outbound 与合并后的趋势序列
     */
    Map<String, Object> operationTrend(int days);

    /** 库存分布：按库区与按 ABC 分类两个维度 */
    Map<String, Object> stockDistribution(Long warehouseId);

    /**
     * 货位分配优化效果对比。
     *
     * <p>对同一算例运行随机分配与频次贪心，给出目标函数值与降幅，
     * 用真实系统数据佐证算法效果。</p>
     *
     * @param warehouseId 仓库 ID
     * @return 各策略的目标函数值与改善比例
     */
    Map<String, Object> slottingEffect(Long warehouseId);

    /**
     * 拣货路径规划效果对比。
     *
     * <p>以当前全部有库存的货位为拣货点集合，运行全部路径策略并比较距离。</p>
     *
     * @param warehouseId 仓库 ID
     * @return 各策略的路径长度与相对 S 型基线的改善比例
     */
    Map<String, Object> pickingEffect(Long warehouseId);

    /**
     * 真实单据上的算法表现：按所用路径算法统计单据数与平均路径长度。
     *
     * @return 每行含 routeAlgo、orderCount、avgDistance
     */
    List<Map<String, Object>> routeAlgorithmStatistics();

    /**
     * 出库频次贡献结构：验证“少数高频商品贡献多数出库量”。
     *
     * @param limit 取前多少个商品
     * @return 含商品明细与累计占比
     */
    Map<String, Object> frequencyContribution(int limit);
}
