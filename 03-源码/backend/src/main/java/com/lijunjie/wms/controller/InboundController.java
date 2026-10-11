package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.dto.InboundOrderRequest;
import com.lijunjie.wms.entity.BizInboundOrder;
import com.lijunjie.wms.service.InboundService;
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
 * 入库管理接口。
 */
@Tag(name = "入库管理", description = "入库单创建、货位推荐、入库确认与取消")
@RestController
@RequestMapping("/api/inbound")
public class InboundController {

    private final InboundService inboundService;

    public InboundController(InboundService inboundService) {
        this.inboundService = inboundService;
    }

    /** 入库单列表 */
    @Operation(summary = "入库单列表", description = "按状态与仓库过滤，按下单时间倒序")
    @GetMapping("/orders")
    public Result<List<BizInboundOrder>> list(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return Result.success(inboundService.listOrders(status, warehouseId, limit));
    }

    /** 入库单详情（含明细、商品与货位） */
    @Operation(summary = "入库单详情", description = "返回单据主体与明细，明细带出商品编码、货位编码与推荐理由")
    @GetMapping("/orders/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.success(inboundService.orderDetail(id));
    }

    /**
     * 创建入库单。
     *
     * <p>请求中 {@code useSlottingSuggest = true} 时，服务端会调用货位分配算法
     * 为每个明细行推荐目标货位，并写入推荐理由。</p>
     */
    @Operation(summary = "创建入库单",
            description = "创建入库单；useSlottingSuggest=true 时调用货位分配算法推荐货位并返回推荐理由")
    @PostMapping("/orders")
    public Result<BizInboundOrder> create(@RequestBody InboundOrderRequest request) {
        return Result.success("入库单创建成功", inboundService.createOrder(request));
    }

    /**
     * 入库确认。
     *
     * <p>写入库存、占用货位、记录实收数量并更新单据状态，整个过程在同一事务中完成。</p>
     */
    @Operation(summary = "入库确认",
            description = "写入库存、把货位状态更新为占用、记录实收数量并完成单据；不可重复确认")
    @PostMapping("/orders/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable("id") Long id) {
        return Result.success("入库完成", inboundService.confirmInbound(id));
    }

    /** 取消入库单 */
    @Operation(summary = "取消入库单", description = "已完成的入库单不能取消")
    @PostMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        inboundService.cancelOrder(id);
        return Result.success("入库单已取消", null);
    }

    /** 按状态统计入库单 */
    @Operation(summary = "入库单状态统计", description = "按状态汇总入库单数量，供报表使用")
    @GetMapping("/statistics")
    public Result<List<Map<String, Object>>> statistics() {
        return Result.success(inboundService.countByStatus());
    }
}
