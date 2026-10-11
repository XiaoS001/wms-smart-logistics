package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BizInboundItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 入库单明细数据访问接口。
 */
@Mapper
public interface BizInboundItemMapper extends BaseMapper<BizInboundItem> {

    /**
     * 查询某入库单的明细，并带出商品与货位信息，用于单据详情展示。
     *
     * <p>三表关联：明细 → 商品 → 货位，一次查询取回界面需要的全部字段，
     * 避免逐行查询造成 N+1。</p>
     *
     * @param orderId 入库单 ID
     * @return 每行含商品编码/名称、货位编码/距离、数量与推荐理由
     */
    @Select("SELECT i.id, i.order_id AS orderId, i.product_id AS productId, "
            + "p.product_code AS productCode, p.product_name AS productName, "
            + "p.pick_freq AS pickFreq, p.abc_class AS abcClass, "
            + "i.location_id AS locationId, l.location_code AS locationCode, "
            + "l.distance AS distance, "
            + "i.plan_quantity AS planQuantity, i.actual_quantity AS actualQuantity, "
            + "i.unit_price AS unitPrice, i.batch_no AS batchNo, i.suggest_reason AS suggestReason "
            + "FROM biz_inbound_item i "
            + "LEFT JOIN base_product p ON p.id = i.product_id "
            + "LEFT JOIN base_location l ON l.id = i.location_id "
            + "WHERE i.order_id = #{orderId} ORDER BY i.id")
    List<Map<String, Object>> selectDetailWithProduct(@Param("orderId") Long orderId);
}
