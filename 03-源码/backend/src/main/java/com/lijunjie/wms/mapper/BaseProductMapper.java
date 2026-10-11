package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BaseProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 商品数据访问接口。
 */
@Mapper
public interface BaseProductMapper extends BaseMapper<BaseProduct> {

    /**
     * 按出库频次降序查询商品 ID 与频次，供 ABC 分类计算使用。
     *
     * <p>只取计算所需的两列，避免把整行数据读入内存。命中 {@code idx_pick_freq} 索引。</p>
     *
     * @return 每行含 id 与 pickFreq，按频次降序
     */
    @Select("SELECT id, pick_freq AS pickFreq FROM base_product "
            + "WHERE deleted = 0 ORDER BY pick_freq DESC, id ASC")
    List<Map<String, Object>> selectIdAndFreqOrderByFreqDesc();

    /**
     * 统计各 ABC 分类的商品数量与频次合计。
     *
     * @return 每行含 abcClass、productCount、freqSum
     */
    @Select("SELECT abc_class AS abcClass, COUNT(*) AS productCount, "
            + "IFNULL(SUM(pick_freq), 0) AS freqSum "
            + "FROM base_product WHERE deleted = 0 GROUP BY abc_class ORDER BY abc_class")
    List<Map<String, Object>> summaryByAbcClass();

    /** 更新单个商品的 ABC 分类 */
    @Update("UPDATE base_product SET abc_class = #{abcClass} WHERE id = #{id}")
    int updateAbcClass(@Param("id") Long id, @Param("abcClass") String abcClass);

    /** 批量更新 ABC 分类：按传入的商品 ID 列表统一设置 */
    @Update("<script>"
            + "UPDATE base_product SET abc_class = #{abcClass} WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int batchUpdateAbcClass(@Param("abcClass") String abcClass, @Param("ids") List<Long> ids);

    /**
     * 累加商品的出库频次（出库确认时调用）。
     *
     * <p>频次是货位分配算法的输入，因此每次出库都必须准确累加。</p>
     *
     * @param productId 商品 ID
     * @param delta     本次出库数量
     * @return 受影响行数
     */
    @Update("UPDATE base_product SET pick_freq = pick_freq + #{delta} "
            + "WHERE id = #{productId} AND deleted = 0")
    int increasePickFreq(@Param("productId") Long productId, @Param("delta") int delta);
}
