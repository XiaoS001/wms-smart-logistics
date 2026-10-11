package com.lijunjie.wms.service;

import com.lijunjie.wms.dto.AbcClassifyResult;
import com.lijunjie.wms.dto.CategoryTreeNode;
import com.lijunjie.wms.entity.BaseCategory;
import com.lijunjie.wms.entity.BaseProduct;

import java.util.List;
import java.util.Map;

/**
 * 商品与分类业务接口。
 */
public interface ProductService {

    /** 分页查询商品：支持关键字（编码/名称）、分类、ABC 分类、状态过滤，按出库频次降序 */
    List<BaseProduct> listProducts(String keyword, Long categoryId, String abcClass,
                                   Integer status, int limit);

    /** 按 ID 查询商品 */
    BaseProduct getProduct(Long id);

    /** 新增商品（校验编码唯一） */
    BaseProduct createProduct(BaseProduct product);

    /** 修改商品 */
    BaseProduct updateProduct(Long id, BaseProduct product);

    /** 删除商品 */
    void deleteProduct(Long id);

    /** 累加商品出库频次（出库确认时调用），返回累加后的频次 */
    int increasePickFreq(Long productId, int delta);

    /** 查询分类树（含各级分类下的商品数量） */
    List<CategoryTreeNode> categoryTree();

    /** 查询全部分类（平铺） */
    List<BaseCategory> listCategories();

    /** 新增分类 */
    BaseCategory createCategory(BaseCategory category);

    /** 修改分类 */
    BaseCategory updateCategory(Long id, BaseCategory category);

    /** 删除分类（存在子分类或商品时禁止删除） */
    void deleteCategory(Long id);

    /**
     * 重新计算全部商品的 ABC 分类并写回数据库。
     *
     * @return 分类结果统计
     */
    AbcClassifyResult classifyAbc();

    /** 查询各 ABC 分类的商品数量与频次合计 */
    List<Map<String, Object>> abcSummary();
}
