package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BizOutboundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 出库单数据访问接口。
 */
@Mapper
public interface BizOutboundOrderMapper extends BaseMapper<BizOutboundOrder> {

    /**
     * 按状态统计出库单数量，用于报表统计模块。
     *
     * @return 每行含 status 与 orderCount
     */
    @Select("SELECT status, COUNT(*) AS orderCount FROM biz_outbound_order "
            + "WHERE deleted = 0 GROUP BY status ORDER BY status")
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 取当日最大单号，用于生成递增单号。
     *
     * @param prefix 单号前缀，如 OUT20261012
     * @return 已存在的最大单号；无记录时返回 null
     */
    @Select("SELECT MAX(order_no) FROM biz_outbound_order WHERE order_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxOrderNo(@Param("prefix") String prefix);

    /**
     * 统计各路径规划算法在真实单据上的表现（平均路径长度、单据数），
     * 用于报表模块佐证算法效果。
     *
     * @return 每行含 routeAlgo、orderCount、avgDistance
     */
    @Select("SELECT route_algo AS routeAlgo, COUNT(*) AS orderCount, "
            + "ROUND(AVG(route_distance), 2) AS avgDistance "
            + "FROM biz_outbound_order WHERE deleted = 0 AND route_algo IS NOT NULL "
            + "GROUP BY route_algo ORDER BY avgDistance")
    List<Map<String, Object>> routeAlgorithmStatistics();
}
