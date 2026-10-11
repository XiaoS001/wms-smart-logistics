package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.dto.OutboundOrderRequest;
import com.lijunjie.wms.entity.BizOutboundOrder;
import com.lijunjie.wms.service.OutboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 出库管理接口。
 */
@Tag(name = "出库管理", description = "出库单创建、拣货路径规划与出库确认")
@RestController
@RequestMapping("/api/outbound")
public class OutboundController {

    private final OutboundService outboundService;

    public OutboundController(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    /** 出库单列表 */
    @Operation(summary = "出库单列表", description = "按状态与仓库过滤，按下单时间倒序")
    @GetMapping("/orders")
    public Result<List<BizOutboundOrder>> list(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return Result.success(outboundService.listOrders(status, warehouseId, limit));
    }

    /** 出库单详情 */
    @Operation(summary = "出库单详情", description = "返回单据主体与明细，明细带出货位坐标与拣货顺序")
    @GetMapping("/orders/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.success(outboundService.orderDetail(id));
    }

    /**
     * 创建出库单。
     *
     * <p>按“就近出库”原则从库存分配货位：同一商品分布在多个货位时，
     * 优先从离出入口最近的货位出库。</p>
     */
    @Operation(summary = "创建出库单",
            description = "创建出库单并按就近原则从库存分配货位；库存不足时返回 409")
    @PostMapping("/orders")
    public Result<BizOutboundOrder> create(@RequestBody OutboundOrderRequest request) {
        return Result.success("出库单创建成功", outboundService.createOrder(request));
    }

    /**
     * 规划拣货路径。
     *
     * <p>返回选定策略的路径与距离，同时给出全部策略的对比结果，
     * 便于直观展示算法优化效果。</p>
     */
    @Operation(summary = "规划拣货路径",
            description = "按选定策略求解拣货顺序并写回明细，返回路径距离与全部策略的对比")
    @PostMapping("/orders/{id}/plan-route")
    public Result<Map<String, Object>> planRoute(
            @PathVariable("id") Long id,
            @RequestParam(value = "strategy", required = false, defaultValue = "ant-colony") String strategy) {
        return Result.success("拣货路径规划完成", outboundService.planRoute(id, strategy));
    }

    /** 出库确认 */
    @Operation(summary = "出库确认",
            description = "扣减库存、释放空货位、累加商品出库频次并完成单据；不可重复确认")
    @PostMapping("/orders/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable("id") Long id) {
        return Result.success("出库完成", outboundService.confirmOutbound(id));
    }

    /** 取消出库单 */
    @Operation(summary = "取消出库单", description = "已完成的出库单不能取消")
    @PostMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        outboundService.cancelOrder(id);
        return Result.success("出库单已取消", null);
    }

    /** 按状态统计 */
    @Operation(summary = "出库单状态统计", description = "按状态汇总出库单数量")
    @GetMapping("/statistics")
    public Result<List<Map<String, Object>>> statistics() {
        return Result.success(outboundService.countByStatus());
    }

    /** 可用路径规划策略 */
    @Operation(summary = "可用路径规划策略", description = "返回全部拣货路径规划策略的编码与名称")
    @GetMapping("/strategies")
    public Result<Map<String, String>> strategies() {
        return Result.success(outboundService.availableStrategies());
    }
}
