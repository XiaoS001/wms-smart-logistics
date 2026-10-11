package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.dto.AbcClassifyResult;
import com.lijunjie.wms.entity.BaseProduct;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 商品管理接口。
 */
@Tag(name = "商品管理", description = "商品维护、出库频次与 ABC 分类")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 商品列表：按出库频次降序，支持关键字、分类、ABC 分类、状态过滤 */
    @Operation(summary = "商品列表",
            description = "按出库频次降序返回商品，支持关键字（编码/名称）、分类、ABC 分类与状态过滤")
    @GetMapping
    public Result<List<BaseProduct>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "abcClass", required = false) String abcClass,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit) {
        return Result.success(productService.listProducts(keyword, categoryId, abcClass, status, limit));
    }

    /** 商品详情 */
    @Operation(summary = "商品详情", description = "按 ID 查询商品")
    @GetMapping("/{id}")
    public Result<BaseProduct> detail(@PathVariable("id") Long id) {
        return Result.success(productService.getProduct(id));
    }

    /** 新增商品 */
    @Operation(summary = "新增商品", description = "登记商品编码、名称、分类与规格等信息")
    @PostMapping
    public Result<BaseProduct> create(@RequestBody BaseProduct product) {
        return Result.success("商品创建成功", productService.createProduct(product));
    }

    /** 修改商品 */
    @Operation(summary = "修改商品", description = "修改商品信息")
    @PutMapping("/{id}")
    public Result<BaseProduct> update(@PathVariable("id") Long id,
                                      @RequestBody BaseProduct product) {
        return Result.success("商品修改成功", productService.updateProduct(id, product));
    }

    /** 删除商品 */
    @Operation(summary = "删除商品", description = "逻辑删除商品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        productService.deleteProduct(id);
        return Result.success("商品已删除", null);
    }

    /**
     * 累加出库频次。
     *
     * <p>出库确认时调用。频次是货位分配算法的输入，必须准确累加。</p>
     */
    @Operation(summary = "累加出库频次", description = "出库确认时累加商品的累计出库频次，返回累加后的值")
    @PostMapping("/{id}/pick-freq")
    public Result<Map<String, Object>> increasePickFreq(
            @PathVariable("id") Long id,
            @RequestParam(value = "delta", required = false, defaultValue = "1") Integer delta) {
        int after = productService.increasePickFreq(id, delta == null ? 1 : delta);
        Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
        data.put("productId", id);
        data.put("delta", delta == null ? 1 : delta);
        data.put("pickFreq", after);
        return Result.success("出库频次已累加", data);
    }

    /**
     * 重新计算 ABC 分类。
     *
     * <p>按出库频次累计占比划分：达到 70% 为 A 类、70%~90% 为 B 类、其余为 C 类。
     * 该规则与算法内核保持一致。</p>
     */
    @Operation(summary = "重新计算 ABC 分类",
            description = "按累计出库频次占比重新划分 A/B/C 类（70% / 90% 两个分界点）并写回数据库")
    @PostMapping("/abc-classify")
    public Result<AbcClassifyResult> classifyAbc() {
        return Result.success("ABC 分类计算完成", productService.classifyAbc());
    }

    /** ABC 分类分布统计 */
    @Operation(summary = "ABC 分类统计", description = "统计各 ABC 分类的商品数量与出库频次合计")
    @GetMapping("/abc-summary")
    public Result<List<Map<String, Object>>> abcSummary() {
        return Result.success(productService.abcSummary());
    }
}
