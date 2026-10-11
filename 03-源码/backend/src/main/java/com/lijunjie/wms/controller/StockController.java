package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.dto.StocktakeSubmitRequest;
import com.lijunjie.wms.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 库存管理接口。
 */
@Tag(name = "库存管理", description = "库存查询、预警与盘点")
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /** 库存明细：按货位到出入口距离升序 */
    @Operation(summary = "库存明细", description = "返回货位级库存记录，带出商品、货位坐标与批次，按距离升序")
    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit) {
        return Result.success(stockService.listStock(warehouseId, productId, limit));
    }

    /** 按商品汇总库存（含预警标记） */
    @Operation(summary = "商品库存汇总", description = "按商品汇总库存总量与占用货位数，并标记是否低于安全库存")
    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> summary(
            @RequestParam(value = "warehouseId", required = false) Long warehouseId) {
        return Result.success(stockService.summaryByProduct(warehouseId));
    }

    /** 库存预警 */
    @Operation(summary = "库存预警", description = "返回缺货（低于安全库存）与临期库存两类预警信息")
    @GetMapping("/alerts")
    public Result<Map<String, Object>> alerts(
            @RequestParam(value = "warehouseId", required = false) Long warehouseId) {
        return Result.success(stockService.alerts(warehouseId));
    }

    /** 仓库库存概览：货位占用率与库存总量 */
    @Operation(summary = "仓库库存概览", description = "返回货位总数、占用数、占用率、商品种类数与库存总量")
    @GetMapping("/warehouse/{warehouseId}/summary")
    public Result<Map<String, Object>> warehouseSummary(@PathVariable("warehouseId") Long warehouseId) {
        return Result.success(stockService.warehouseSummary(warehouseId));
    }

    /** 生成盘点任务 */
    @Operation(summary = "生成盘点任务", description = "以当前库存生成账面数量，创建状态为待盘点的盘点单")
    @PostMapping("/stocktake/generate")
    public Result<Map<String, Object>> generateStocktake(
            @RequestParam("warehouseId") Long warehouseId) {
        return Result.success("盘点任务已生成", stockService.generateStocktake(warehouseId));
    }

    /** 盘点明细 */
    @Operation(summary = "盘点明细", description = "按盘点单号查询明细；不传单号时返回全部盘点记录")
    @GetMapping("/stocktake")
    public Result<List<Map<String, Object>>> listStocktake(
            @RequestParam(value = "stocktakeNo", required = false) String stocktakeNo) {
        return Result.success(stockService.listStocktake(stocktakeNo));
    }

    /**
     * 提交盘点结果。
     *
     * <p>{@code adjustStock} 为真时把差异写回库存并同步货位占用状态。</p>
     */
    @Operation(summary = "提交盘点结果",
            description = "录入实盘数量并计算差异；adjustStock=true 时把差异写回库存并同步货位状态")
    @PostMapping("/stocktake/submit")
    public Result<Map<String, Object>> submitStocktake(@RequestBody StocktakeSubmitRequest request) {
        return Result.success("盘点结果已提交", stockService.submitStocktake(request));
    }

    /** 撤销盘点单 */
    @Operation(summary = "撤销盘点单", description = "已调整库存的盘点单不能撤销")
    @DeleteMapping("/stocktake/{stocktakeNo}")
    public Result<Void> cancelStocktake(@PathVariable("stocktakeNo") String stocktakeNo) {
        stockService.cancelStocktake(stocktakeNo);
        return Result.success("盘点单已撤销", null);
    }
}
