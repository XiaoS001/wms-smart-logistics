package com.lijunjie.wms.service;

import com.lijunjie.wms.dto.OutboundOrderRequest;
import com.lijunjie.wms.entity.BizOutboundOrder;

import java.util.List;
import java.util.Map;

/**
 * 出库业务接口。
 */
public interface OutboundService {

    /**
     * 创建出库单。
     *
     * <p>按“就近出库”原则从库存中分配货位：同一商品的库存可能分布在多个货位，
     * 系统按货位到出入口的距离升序依次扣减，直至满足需求量。</p>
     *
     * @param request 出库单参数
     * @return 已创建的出库单
     */
    BizOutboundOrder createOrder(OutboundOrderRequest request);

    /** 查询出库单列表 */
    List<BizOutboundOrder> listOrders(Integer status, Long warehouseId, int limit);

    /** 出库单详情（含明细、商品、货位坐标与拣货顺序） */
    Map<String, Object> orderDetail(Long orderId);

    /**
     * 为出库单规划拣货路径。
     *
     * <p>汇总订单内全部拣货点坐标，按选定策略求解访问顺序，
     * 把顺序写回明细的 {@code pickSeq}，并把路径长度与算法编码记录在单据上。</p>
     *
     * @param orderId      出库单 ID
     * @param strategyCode 路径规划策略
     * @return 规划结果（路径、距离、耗时、各策略对比）
     */
    Map<String, Object> planRoute(Long orderId, String strategyCode);

    /**
     * 拣货确认：扣减库存、释放货位、累加商品出库频次并完成单据。
     *
     * @param orderId 出库单 ID
     * @return 确认结果统计
     */
    Map<String, Object> confirmOutbound(Long orderId);

    /** 取消出库单 */
    void cancelOrder(Long orderId);

    /** 按状态统计出库单数量 */
    List<Map<String, Object>> countByStatus();

    /** 可用路径规划策略 */
    Map<String, String> availableStrategies();
}
