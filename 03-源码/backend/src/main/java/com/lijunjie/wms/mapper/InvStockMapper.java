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
     * <p>SQL 以 {@code <where>} + {@code <if>} 组合拼接：{@code <choose>} 若写成
     * “一行只放一个标签、前面带缩进”的形式，其内部空白会成为元素内容，
     * 触发 XML 解析错误（元素内容必须由格式正确的字符数据或标记组成）。</p>
     *
     * @param locationId 货位 ID
     * @param productId  商品 ID
     * @param batchNo    批次号，可为 null
     * @return 库存记录；不存在时返回 null
     */
    @Select("<script>"
            + "SELECT * FROM inv_stock WHERE location_id = #{locationId} AND product_id = #{productId} "
            + "<if test='batchNo != null'>AND batch_no = #{batchNo}</if>"
            + "<if test='batchNo == null'>AND batch_no IS NULL</if>"
            + " LIMIT 1"
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

    /**
     * 库存明细查询：带出商品、货位与批次信息。
     *
     * <p>按货位到出入口的距离升序返回，便于直观看到“近端货位存放什么商品”。</p>
     *
     * @param warehouseId 仓库 ID，可为 null
     * @param productId   商品 ID，可为 null
     * @param limit       返回条数上限
     * @return 每行含商品编码/名称、货位编码/距离、数量、批次与有效期
     */
    @Select("<script>"
            + "SELECT s.id, s.warehouse_id AS warehouseId, s.location_id AS locationId, "
            + "l.location_code AS locationCode, l.aisle_no AS aisleNo, l.row_no AS rowNo, "
            + "l.distance AS distance, l.status AS locationStatus, "
            + "s.product_id AS productId, p.product_code AS productCode, p.product_name AS productName, "
            + "p.unit AS unit, p.abc_class AS abcClass, p.safety_stock AS safetyStock, "
            + "s.quantity, s.locked_quantity AS lockedQuantity, s.batch_no AS batchNo, "
            + "s.production_date AS productionDate, s.expiry_date AS expiryDate, s.update_time AS updateTime "
            + "FROM inv_stock s "
            + "LEFT JOIN base_product p ON p.id = s.product_id "
            + "LEFT JOIN base_location l ON l.id = s.location_id "
            + "<where>"
            + "<if test='warehouseId != null'>AND s.warehouse_id = #{warehouseId}</if> "
            + "<if test='productId != null'>AND s.product_id = #{productId}</if> "
            + "</where> "
            + "ORDER BY l.distance ASC, s.id ASC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> selectDetail(@Param("warehouseId") Long warehouseId,
                                           @Param("productId") Long productId,
                                           @Param("limit") int limit);

    /**
     * 临期库存查询：有效期在指定天数内或已经过期的库存记录。
     *
     * <p>注意：{@code <script>} 注解中的 SQL 由 MyBatis 按 XML 解析，
     * 因此比较运算符中的小于号必须写成 {@code &lt;}，否则抛
     * “元素内容必须由格式正确的字符数据或标记组成”。</p>
     *
     * @param warehouseId 仓库 ID，可为 null
     * @param days        提前预警天数
     * @return 每行含商品、货位、数量、有效期与剩余天数
     */
    @Select("<script>"
            + "SELECT s.id, s.product_id AS productId, p.product_code AS productCode, "
            + "p.product_name AS productName, l.location_code AS locationCode, "
            + "s.quantity, s.batch_no AS batchNo, s.expiry_date AS expiryDate, "
            + "DATEDIFF(s.expiry_date, CURDATE()) AS daysLeft "
            + "FROM inv_stock s "
            + "LEFT JOIN base_product p ON p.id = s.product_id "
            + "LEFT JOIN base_location l ON l.id = s.location_id "
            + "WHERE s.expiry_date IS NOT NULL "
            + "AND DATEDIFF(s.expiry_date, CURDATE()) &lt;= #{days} "
            + "<if test='warehouseId != null'>AND s.warehouse_id = #{warehouseId}</if> "
            + "ORDER BY s.expiry_date ASC"
            + "</script>")
    List<Map<String, Object>> selectNearExpiry(@Param("warehouseId") Long warehouseId,
                                               @Param("days") int days);
}
