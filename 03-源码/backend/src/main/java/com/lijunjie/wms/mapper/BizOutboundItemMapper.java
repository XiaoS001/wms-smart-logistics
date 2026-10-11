package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BizOutboundItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 出库单明细数据访问接口。
 */
@Mapper
public interface BizOutboundItemMapper extends BaseMapper<BizOutboundItem> {

    /**
     * 查询某出库单的明细，并带出商品、货位与坐标信息。
     *
     * <p>带出货位坐标（aisleNo / rowNo）是为了让前端能够直接把拣货顺序画在库位图上，
     * 无需再逐条查询货位。</p>
     *
     * @param orderId 出库单 ID
     * @return 每行含商品编码/名称、货位编码/坐标/距离、数量与拣货顺序
     */
    @Select("SELECT i.id, i.order_id AS orderId, i.product_id AS productId, "
            + "p.product_code AS productCode, p.product_name AS productName, p.unit AS unit, "
            + "i.location_id AS locationId, l.location_code AS locationCode, "
            + "l.aisle_no AS aisleNo, l.row_no AS rowNo, l.distance AS distance, "
            + "i.quantity, i.pick_seq AS pickSeq, i.picked "
            + "FROM biz_outbound_item i "
            + "LEFT JOIN base_product p ON p.id = i.product_id "
            + "LEFT JOIN base_location l ON l.id = i.location_id "
            + "WHERE i.order_id = #{orderId} ORDER BY i.pick_seq, i.id")
    List<Map<String, Object>> selectDetailWithProduct(@Param("orderId") Long orderId);

    /** 更新某明细的拣货顺序与拣货状态 */
    @Update("UPDATE biz_outbound_item SET pick_seq = #{pickSeq}, picked = #{picked} WHERE id = #{id}")
    int updatePickSeq(@Param("id") Long id, @Param("pickSeq") Integer pickSeq, @Param("picked") Integer picked);

    /**
     * 按商品汇总出库量，用于报表统计。
     *
     * @return 每行含商品编码/名称与出库总量
     */
    @Select("SELECT p.product_code AS productCode, p.product_name AS productName, "
            + "SUM(i.quantity) AS totalQuantity, COUNT(DISTINCT i.order_id) AS orderCount "
            + "FROM biz_outbound_item i "
            + "JOIN biz_outbound_order o ON o.id = i.order_id AND o.deleted = 0 "
            + "LEFT JOIN base_product p ON p.id = i.product_id "
            + "GROUP BY p.product_code, p.product_name ORDER BY totalQuantity DESC")
    List<Map<String, Object>> summaryByProduct();
}
