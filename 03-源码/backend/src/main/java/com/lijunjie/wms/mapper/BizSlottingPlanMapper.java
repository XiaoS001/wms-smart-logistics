package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BizSlottingPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 货位优化方案数据访问接口。
 */
@Mapper
public interface BizSlottingPlanMapper extends BaseMapper<BizSlottingPlan> {

    /**
     * 取某前缀下的最大方案编号，用于生成递增编号。
     *
     * @param prefix 编号前缀，如 SP20261012
     * @return 已存在的最大编号；无记录时返回 null
     */
    @Select("SELECT MAX(plan_no) FROM biz_slotting_plan WHERE plan_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxPlanNo(@Param("prefix") String prefix);

    /**
     * 当前库存的实际加权行走距离。
     *
     * <p>对每一条库存记录计算 {@code 商品出库频次 × 该货位到出入口距离} 并求和，
     * 即当前布局的目标函数值。它反映的是“商品现在放在哪里”，与算法求解出的
     * 理想布局形成对比，从而量化优化空间。</p>
     *
     * @param warehouseId 仓库 ID
     * @return 单行：objective（加权行走距离合计）、freqSum（频次合计）、stockRows（库存记录数）
     */
    @Select("SELECT IFNULL(SUM(IFNULL(p.pick_freq, 0) * IFNULL(l.distance, 0)), 0) AS objective, "
            + "IFNULL(SUM(IFNULL(p.pick_freq, 0)), 0) AS freqSum, "
            + "COUNT(*) AS stockRows "
            + "FROM inv_stock s "
            + "JOIN base_product p ON p.id = s.product_id "
            + "JOIN base_location l ON l.id = s.location_id "
            + "WHERE s.warehouse_id = #{warehouseId}")
    Map<String, Object> currentObjective(@Param("warehouseId") Long warehouseId);

    /**
     * 按策略统计历史方案的优化效果，用于报表与论文佐证。
     *
     * @return 每行含 strategy、planCount、avgImprovement、bestImprovement
     */
    @Select("SELECT strategy, COUNT(*) AS planCount, "
            + "ROUND(AVG(improvement_percent), 2) AS avgImprovement, "
            + "ROUND(MAX(improvement_percent), 2) AS bestImprovement "
            + "FROM biz_slotting_plan WHERE improvement_percent IS NOT NULL "
            + "GROUP BY strategy ORDER BY avgImprovement DESC")
    List<Map<String, Object>> statisticsByStrategy();
}
