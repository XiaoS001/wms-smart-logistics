package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 报表统计接口。
 */
@Tag(name = "报表统计", description = "作业量、库存分布与算法效果统计")
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 首页概览 */
    @Operation(summary = "首页概览", description = "商品、货位、库存、单据与缺货预警的关键指标")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(reportService.dashboard());
    }

    /** 作业量趋势 */
    @Operation(summary = "作业量趋势", description = "按日期统计出入库单量与数量，缺失日期补 0")
    @GetMapping("/operation-trend")
    public Result<Map<String, Object>> operationTrend(
            @RequestParam(value = "days", required = false, defaultValue = "30") Integer days) {
        return Result.success(reportService.operationTrend(days == null ? 30 : days));
    }

    /** 库存分布 */
    @Operation(summary = "库存分布", description = "按库区与按 ABC 分类两个维度统计库存分布与占用率")
    @GetMapping("/stock-distribution")
    public Result<Map<String, Object>> stockDistribution(
            @RequestParam(value = "warehouseId", required = false) Long warehouseId) {
        return Result.success(reportService.stockDistribution(warehouseId));
    }

    /**
     * 货位分配优化效果。
     *
     * <p>用系统内的真实商品与货位数据运行各策略，输出目标函数值与相对随机分配的降幅。</p>
     */
    @Operation(summary = "货位分配优化效果",
            description = "运行随机/频次贪心/ABC 分区贪心/遗传算法，返回加权行走距离与相对随机分配的降幅")
    @GetMapping("/slotting-effect/{warehouseId}")
    public Result<Map<String, Object>> slottingEffect(@PathVariable("warehouseId") Long warehouseId) {
        return Result.success(reportService.slottingEffect(warehouseId));
    }

    /**
     * 拣货路径规划效果。
     *
     * <p>以当前有库存的货位为拣货点集合，比较各策略的路径长度。</p>
     */
    @Operation(summary = "拣货路径规划效果",
            description = "以当前有库存的货位为拣货点，比较全部路径策略的行走距离与相对 S 型的改善比例")
    @GetMapping("/picking-effect/{warehouseId}")
    public Result<Map<String, Object>> pickingEffect(@PathVariable("warehouseId") Long warehouseId) {
        return Result.success(reportService.pickingEffect(warehouseId));
    }

    /** 真实单据上的算法表现 */
    @Operation(summary = "算法实际表现", description = "按出库单所用的路径算法统计单据数与平均路径长度")
    @GetMapping("/route-algorithm-statistics")
    public Result<List<Map<String, Object>>> routeAlgorithmStatistics() {
        return Result.success(reportService.routeAlgorithmStatistics());
    }

    /** 出库频次贡献结构 */
    @Operation(summary = "出库频次贡献结构",
            description = "按出库频次降序给出商品与累计占比，说明 ABC 分类的划分依据")
    @GetMapping("/frequency-contribution")
    public Result<Map<String, Object>> frequencyContribution(
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        return Result.success(reportService.frequencyContribution(limit == null ? 20 : limit));
    }
}
