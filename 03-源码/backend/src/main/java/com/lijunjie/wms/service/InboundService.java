package com.lijunjie.wms.service;

import com.lijunjie.wms.dto.InboundOrderRequest;
import com.lijunjie.wms.entity.BizInboundOrder;

import java.util.List;
import java.util.Map;

/**
 * 入库业务接口。
 */
public interface InboundService {

    /**
     * 创建入库单。
     *
     * <p>若请求中 {@code useSlottingSuggest = true}，则对每个明细行调用货位分配算法
     * 推荐目标货位，并把推荐理由写入明细，便于仓管员核对。</p>
     *
     * @param request 入库单参数
     * @return 已创建的入库单
     */
    BizInboundOrder createOrder(InboundOrderRequest request);

    /** 分页查询入库单，可按状态过滤 */
    List<BizInboundOrder> listOrders(Integer status, Long warehouseId, int limit);

    /** 入库单详情（含明细、商品与货位信息） */
    Map<String, Object> orderDetail(Long orderId);

    /**
     * 入库确认：写入库存、更新货位占用状态、累加商品出库频次并更新单据状态。
     *
     * <p>整个操作在事务中完成，任何一步失败都会整体回滚，保证库存与单据一致。</p>
     *
     * @param orderId 入库单 ID
     * @return 确认结果统计
     */
    Map<String, Object> confirmInbound(Long orderId);

    /**
     * 取消入库单。
     *
     * @param orderId 入库单 ID
     */
    void cancelOrder(Long orderId);

    /** 按状态统计入库单数量，用于报表 */
    List<Map<String, Object>> countByStatus();
}
