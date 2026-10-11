package com.lijunjie.wms.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 报表统计数据访问接口。
 *
 * <p>把报表涉及的聚合查询集中在此，避免各业务 Mapper 承担与自身领域无关的统计职责。</p>
 *
 * <p>注意：注解中的 SQL 若以 {@code <script>} 开头会被 MyBatis 按 XML 解析，
 * 因此比较运算符中的小于号必须写成 {@code &lt;}。</p>
 */
@Mapper
public interface ReportMapper {

    /**
     * 按日期统计入库单量与入库数量。
     *
     * @param days 最近天数
     * @return 每行含 statDate、orderCount、totalQuantity
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate, "
            + "COUNT(*) AS orderCount, IFNULL(SUM(total_quantity), 0) AS totalQuantity "
            + "FROM biz_inbound_order "
            + "WHERE deleted = 0 AND status <> 9 "
            + "AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') ORDER BY statDate")
    List<Map<String, Object>> inboundByDay(@Param("days") int days);

    /**
     * 按日期统计出库单量与出库数量。
     *
     * @param days 最近天数
     * @return 每行含 statDate、orderCount、totalQuantity
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate, "
            + "COUNT(*) AS orderCount, IFNULL(SUM(total_quantity), 0) AS totalQuantity "
            + "FROM biz_outbound_order "
            + "WHERE deleted = 0 AND status <> 9 "
            + "AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') ORDER BY statDate")
    List<Map<String, Object>> outboundByDay(@Param("days") int days);

    /**
     * 库存按库区分布统计。
     *
     * @param warehouseId 仓库 ID，可为 null
     * @return 每行含 zoneId、zoneName、zoneType、locationCount、usedLocations、stockQuantity
     */
    @Select("<script>"
            + "SELECT z.id AS zoneId, z.zone_name AS zoneName, z.zone_type AS zoneType, "
            + "COUNT(DISTINCT l.id) AS locationCount, "
            + "COUNT(DISTINCT CASE WHEN l.status = 2 THEN l.id END) AS usedLocations, "
            + "IFNULL(SUM(s.quantity), 0) AS stockQuantity "
            + "FROM base_zone z "
            + "LEFT JOIN base_location l ON l.zone_id = z.id AND l.deleted = 0 "
            + "LEFT JOIN inv_stock s ON s.location_id = l.id "
            + "<if test='warehouseId != null'>AND s.warehouse_id = #{warehouseId}</if> "
            + "WHERE z.deleted = 0 "
            + "<if test='warehouseId != null'>AND z.warehouse_id = #{warehouseId}</if> "
            + "GROUP BY z.id, z.zone_name, z.zone_type ORDER BY z.id"
            + "</script>")
    List<Map<String, Object>> stockDistributionByZone(@Param("warehouseId") Long warehouseId);

    /**
     * 库存按商品 ABC 分类的分布统计。
     *
     * @return 每行含 abcClass、productKinds、stockQuantity、pickFreqSum
     */
    @Select("SELECT IFNULL(p.abc_class, 'C') AS abcClass, "
            + "COUNT(DISTINCT p.id) AS productKinds, "
            + "IFNULL(SUM(s.quantity), 0) AS stockQuantity, "
            + "IFNULL(SUM(DISTINCT p.pick_freq), 0) AS pickFreqSum "
            + "FROM base_product p LEFT JOIN inv_stock s ON s.product_id = p.id "
            + "WHERE p.deleted = 0 GROUP BY IFNULL(p.abc_class, 'C') ORDER BY abcClass")
    List<Map<String, Object>> stockDistributionByAbc();

    /** 商品总数 */
    @Select("SELECT COUNT(*) FROM base_product WHERE deleted = 0")
    long countProducts();

    /** 货位总数 */
    @Select("SELECT COUNT(*) FROM base_location WHERE deleted = 0")
    long countLocations();

    /** 已占用货位数 */
    @Select("SELECT COUNT(*) FROM base_location WHERE deleted = 0 AND status = 2")
    long countOccupiedLocations();

    /** 待入库单数 */
    @Select("SELECT COUNT(*) FROM biz_inbound_order WHERE deleted = 0 AND status = 0")
    long countPendingInbound();

    /** 待拣货单数 */
    @Select("SELECT COUNT(*) FROM biz_outbound_order WHERE deleted = 0 AND status IN (0, 1)")
    long countPendingOutbound();

    /** 已完成入库单数 */
    @Select("SELECT COUNT(*) FROM biz_inbound_order WHERE deleted = 0 AND status = 2")
    long countDoneInbound();

    /** 已完成出库单数 */
    @Select("SELECT COUNT(*) FROM biz_outbound_order WHERE deleted = 0 AND status = 2")
    long countDoneOutbound();

    /** 库存总量 */
    @Select("SELECT IFNULL(SUM(quantity), 0) FROM inv_stock")
    java.math.BigDecimal sumStockQuantity();

    /**
     * 库存预警商品数：库存为 0（缺货）或低于安全库存。
     *
     * <p>注意这里必须<b>取并集而不是把两个计数相加</b>：安全库存大于 0 时，
     * “库存为 0”的商品同时满足“低于安全库存”，相加会重复计数。</p>
     *
     * <p>同时注意本方法<b>不带</b> {@code <script>} 标签，属于普通 SQL 注解，
     * 不会被 XML 解析，因此小于号必须写成字面 {@code <}，
     * 写成 {@code &lt;} 会被原样发往数据库造成语法错误。</p>
     */
    @Select("SELECT COUNT(*) FROM ("
            + "SELECT p.id, IFNULL(SUM(s.quantity), 0) AS qty, IFNULL(p.safety_stock, 0) AS safety "
            + "FROM base_product p LEFT JOIN inv_stock s ON s.product_id = p.id "
            + "WHERE p.deleted = 0 GROUP BY p.id, p.safety_stock"
            + ") t WHERE t.qty <= 0 OR t.qty < t.safety")
    long countLowStockProducts();

    /**
     * 出库商品的频次贡献结构：验证“少数高频商品贡献多数出库量”。
     *
     * @return 每行含 productCode、productName、pickFreq、abcClass
     */
    @Select("SELECT product_code AS productCode, product_name AS productName, "
            + "IFNULL(pick_freq, 0) AS pickFreq, IFNULL(abc_class, 'C') AS abcClass "
            + "FROM base_product WHERE deleted = 0 ORDER BY pick_freq DESC, id LIMIT #{limit}")
    List<Map<String, Object>> topFrequencyProducts(@Param("limit") int limit);

    /**
     * 全部商品的出库频次合计。
     *
     * <p>频次贡献结构必须用<b>全量</b>频次作为分母：累计占比的含义是
     * “前若干个高频商品占全部出库频次的比例”，而 ABC 分类也正是按全量频次
     * 累加划分 70%/90% 的。若改用“前 N 个商品的频次和”作分母，
     * 当商品总数超过查询条数时占比会被系统性抬高，
     * 得到的“贡献 70% 频次所需商品数”会偏小，与 ABC 分类结果不一致。</p>
     */
    @Select("SELECT IFNULL(SUM(pick_freq), 0) FROM base_product WHERE deleted = 0")
    long sumAllPickFrequency();

    /**
     * 未删除的商品总数。
     *
     * <p>频次贡献接口只返回前 N 条，调用方需要知道商品总数才能判断
     * 展示的是否为全量，因此一并返回。</p>
     */
    @Select("SELECT COUNT(*) FROM base_product WHERE deleted = 0")
    long countAllProducts();
}
