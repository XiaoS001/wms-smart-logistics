package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.dto.CategoryTreeNode;
import com.lijunjie.wms.entity.BaseCategory;
import com.lijunjie.wms.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品分类接口。
 */
@Tag(name = "商品分类", description = "树形分类的查询与维护")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final ProductService productService;

    public CategoryController(ProductService productService) {
        this.productService = productService;
    }

    /** 分类树（含各级分类下的商品数量） */
    @Operation(summary = "分类树", description = "返回树形分类结构，每个节点带该分类下的商品数量")
    @GetMapping("/tree")
    public Result<List<CategoryTreeNode>> tree() {
        return Result.success(productService.categoryTree());
    }

    /** 分类平铺列表 */
    @Operation(summary = "分类列表", description = "返回全部未删除分类的平铺列表")
    @GetMapping
    public Result<List<BaseCategory>> list() {
        return Result.success(productService.listCategories());
    }

    /** 新增分类 */
    @Operation(summary = "新增分类", description = "parentId 为 0 表示顶级分类")
    @PostMapping
    public Result<BaseCategory> create(@RequestBody BaseCategory category) {
        return Result.success("分类创建成功", productService.createCategory(category));
    }

    /** 修改分类 */
    @Operation(summary = "修改分类", description = "不允许把分类的父级设置为自身")
    @PutMapping("/{id}")
    public Result<BaseCategory> update(@PathVariable("id") Long id,
                                       @RequestBody BaseCategory category) {
        return Result.success("分类修改成功", productService.updateCategory(id, category));
    }

    /** 删除分类（存在子分类或商品时拒绝） */
    @Operation(summary = "删除分类", description = "存在子分类或关联商品时返回 409")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        productService.deleteCategory(id);
        return Result.success("分类已删除", null);
    }
}
