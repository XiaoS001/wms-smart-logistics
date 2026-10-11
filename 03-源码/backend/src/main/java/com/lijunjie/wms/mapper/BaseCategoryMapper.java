package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BaseCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 商品分类数据访问接口。
 */
@Mapper
public interface BaseCategoryMapper extends BaseMapper<BaseCategory> {

    /**
     * 统计各级分类下的商品数量，用于分类树展示。
     *
     * @return 每行含 categoryId 与 productCount
     */
    @Select("SELECT c.id AS categoryId, COUNT(p.id) AS productCount "
            + "FROM base_category c LEFT JOIN base_product p "
            + "ON p.category_id = c.id AND p.deleted = 0 "
            + "WHERE c.deleted = 0 GROUP BY c.id")
    List<Map<String, Object>> countProductsByCategory();
}
