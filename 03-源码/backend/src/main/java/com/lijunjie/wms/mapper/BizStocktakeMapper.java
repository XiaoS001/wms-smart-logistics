package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BizStocktake;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 库存盘点数据访问接口。
 */
@Mapper
public interface BizStocktakeMapper extends BaseMapper<BizStocktake> {

    /**
     * 查询盘点明细，带出商品与货位信息。
     *
     * @param stocktakeNo 盘点单号；为空时查询全部
     * @return 每行含商品编码/名称、货位编码、账面/实盘/差异数量与状态
     */
    @Select("<script>"
            + "SELECT s.id, s.stocktake_no AS stocktakeNo, s.warehouse_id AS warehouseId, "
            + "s.location_id AS locationId, l.location_code AS locationCode, l.distance AS distance, "
            + "s.product_id AS productId, p.product_code AS productCode, p.product_name AS productName, "
            + "p.unit AS unit, s.book_quantity AS bookQuantity, s.actual_quantity AS actualQuantity, "
            + "s.diff_quantity AS diffQuantity, s.status, s.stocktake_time AS stocktakeTime, s.remark "
            + "FROM biz_stocktake s "
            + "LEFT JOIN base_product p ON p.id = s.product_id "
            + "LEFT JOIN base_location l ON l.id = s.location_id "
            + "<where>"
            + "<if test='stocktakeNo != null'>s.stocktake_no = #{stocktakeNo}</if> "
            + "</where> "
            + "ORDER BY s.stocktake_no DESC, s.id"
            + "</script>")
    List<Map<String, Object>> selectDetail(@Param("stocktakeNo") String stocktakeNo);

    /**
     * 盘点差异汇总：按盘点单号统计差异条数与差异总量。
     *
     * @param stocktakeNo 盘点单号
     * @return 单行统计结果
     */
    @Select("SELECT COUNT(*) AS totalRows, "
            + "SUM(CASE WHEN diff_quantity <> 0 THEN 1 ELSE 0 END) AS diffRows, "
            + "IFNULL(SUM(ABS(diff_quantity)), 0) AS absDiffSum, "
            + "IFNULL(SUM(diff_quantity), 0) AS netDiffSum "
            + "FROM biz_stocktake WHERE stocktake_no = #{stocktakeNo}")
    Map<String, Object> summaryByNo(@Param("stocktakeNo") String stocktakeNo);

    /**
     * 取某前缀下的最大盘点单号，用于生成递增单号。
     *
     * @param prefix 单号前缀，如 ST20261012
     * @return 已存在的最大单号；无记录时返回 null
     */
    @Select("SELECT MAX(stocktake_no) FROM biz_stocktake WHERE stocktake_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxStocktakeNo(@Param("prefix") String prefix);

    /** 删除某盘点单的全部记录（用于撤销尚未调整的盘点） */
    @org.apache.ibatis.annotations.Delete("DELETE FROM biz_stocktake WHERE stocktake_no = #{stocktakeNo}")
    int deleteByStocktakeNo(@Param("stocktakeNo") String stocktakeNo);
}
