package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BizInboundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 入库单数据访问接口。
 */
@Mapper
public interface BizInboundOrderMapper extends BaseMapper<BizInboundOrder> {

    /**
     * 按状态统计入库单数量，用于报表统计模块。
     *
     * @return 每行含 status 与 orderCount
     */
    @Select("SELECT status, COUNT(*) AS orderCount FROM biz_inbound_order "
            + "WHERE deleted = 0 GROUP BY status ORDER BY status")
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 取当日最大单号序号，用于生成递增单号。
     *
     * @param prefix 单号前缀，如 IN20261012
     * @return 已存在的最大单号；无记录时返回 null
     */
    @Select("SELECT MAX(order_no) FROM biz_inbound_order WHERE order_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxOrderNo(@Param("prefix") String prefix);
}
