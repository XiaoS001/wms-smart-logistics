package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.InvStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 库存数据访问接口。
 */
@Mapper
public interface InvStockMapper extends BaseMapper<InvStock> {

    /**
     * 查询指定货位与商品（含批次）的库存记录。
     *
     * <p>入库确认时先查后写：存在则累加数量，不存在则新增记录。</p>
     *
     * @param locationId 货位 ID
     * @param productId  商品 ID
     * @param batchNo    批次号，可为 null
     * @return 库存记录；不存在时返回 null
     */
    @Select("<script>"
            + "SELECT * FROM inv_stock WHERE location_id = #{locationId} AND product_id = #{productId} "
            + "<choose><when test='batchNo == null'>AND batch_no IS NULL</when>"
            + "<otherwise>AND batch_no = #{batchNo}</otherwise></choose> LIMIT 1"
            + "</script>")
    InvStock selectByLocationProductBatch(@Param("locationId") Long locationId,
                                          @Param("productId") Long productId,
                                          @Param("batchNo") String batchNo);

    /**
     * 库存数量累加。
     *
     * @param id   库存记录 ID
     * @param delta 增量
     * @return 受影响行数
     */
    @Update("UPDATE inv_stock SET quantity = quantity + #{delta}, "
            + "update_time = NOW() WHERE id = #{id}")
    int increaseQuantity(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * 统计某仓库的库存汇总：货位数、商品数、库存总量。
     *
     * @param warehouseId 仓库 ID
     * @return 单行统计结果
     */
    @Select("SELECT COUNT(DISTINCT location_id) AS usedLocations, "
            + "COUNT(DISTINCT product_id) AS productKinds, "
            + "IFNULL(SUM(quantity), 0) AS totalQuantity "
            + "FROM inv_stock WHERE warehouse_id = #{warehouseId}")
    Map<String, Object> summaryByWarehouse(@Param("warehouseId") Long warehouseId);

    /**
     * 按商品汇总库存，用于库存列表与预警。
     *
     * @param warehouseId 仓库 ID，可为 null 表示全部仓库
     * @return 每行含商品编码/名称、库存合计、安全库存与预警标记
     */
    @Select("<script>"
            + "SELECT p.id AS productId, p.product_code AS productCode, p.product_name AS productName, "
            + "p.unit AS unit, p.abc_class AS abcClass, p.safety_stock AS safetyStock, "
            + "IFNULL(SUM(s.quantity), 0) AS totalQuantity, COUNT(DISTINCT s.location_id) AS locationCount "
            + "FROM base_product p LEFT JOIN inv_stock s ON s.product_id = p.id "
            + "<if test='warehouseId != null'>AND s.warehouse_id = #{warehouseId}</if> "
            + "WHERE p.deleted = 0 GROUP BY p.id, p.product_code, p.product_name, p.unit, p.abc_class, p.safety_stock "
            + "ORDER BY totalQuantity DESC, p.id"
            + "</script>")
    List<Map<String, Object>> summaryByProduct(@Param("warehouseId") Long warehouseId);
}
