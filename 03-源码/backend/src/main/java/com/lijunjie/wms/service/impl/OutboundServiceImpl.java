package com.lijunjie.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lijunjie.wms.algorithm.PickPath;
import com.lijunjie.wms.algorithm.PickingRoute;
import com.lijunjie.wms.algorithm.PickingStrategy;
import com.lijunjie.wms.algorithm.PickingStrategyFactory;
import com.lijunjie.wms.algorithm.WarehouseLayout;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.dto.OutboundOrderRequest;
import com.lijunjie.wms.entity.BaseLocation;
import com.lijunjie.wms.entity.BaseProduct;
import com.lijunjie.wms.entity.BaseWarehouse;
import com.lijunjie.wms.entity.BizOutboundItem;
import com.lijunjie.wms.entity.BizOutboundOrder;
import com.lijunjie.wms.entity.InvStock;
import com.lijunjie.wms.mapper.BaseLocationMapper;
import com.lijunjie.wms.mapper.BaseProductMapper;
import com.lijunjie.wms.mapper.BaseWarehouseMapper;
import com.lijunjie.wms.mapper.BizOutboundItemMapper;
import com.lijunjie.wms.mapper.BizOutboundOrderMapper;
import com.lijunjie.wms.mapper.InvStockMapper;
import com.lijunjie.wms.service.OutboundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出库业务实现。
 *
 * <p>本模块是算法与业务的第二个结合点：创建出库单时按“就近出库”原则分配货位，
 * 规划路径时调用拣货路径规划算法求解访问顺序，并把结果写回明细与单据。</p>
 */
@Service
public class OutboundServiceImpl implements OutboundService {

    private static final Logger log = LoggerFactory.getLogger(OutboundServiceImpl.class);

    /** 货位状态：空闲 */
    private static final int LOCATION_FREE = 1;

    /** 货位状态：占用 */
    private static final int LOCATION_USED = 2;

    private final BizOutboundOrderMapper orderMapper;
    private final BizOutboundItemMapper itemMapper;
    private final BaseProductMapper productMapper;
    private final BaseLocationMapper locationMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final InvStockMapper stockMapper;
    private final PickingStrategyFactory pickingFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboundServiceImpl(BizOutboundOrderMapper orderMapper,
                               BizOutboundItemMapper itemMapper,
                               BaseProductMapper productMapper,
                               BaseLocationMapper locationMapper,
                               BaseWarehouseMapper warehouseMapper,
                               InvStockMapper stockMapper,
                               PickingStrategyFactory pickingFactory) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.productMapper = productMapper;
        this.locationMapper = locationMapper;
        this.warehouseMapper = warehouseMapper;
        this.stockMapper = stockMapper;
        this.pickingFactory = pickingFactory;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizOutboundOrder createOrder(OutboundOrderRequest request) {
        if (request == null || request.getWarehouseId() == null) {
            throw new BusinessException(400, "仓库不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(400, "出库明细不能为空");
        }
        BaseWarehouse warehouse = warehouseMapper.selectById(request.getWarehouseId());
        if (warehouse == null) {
            throw new BusinessException(404, "仓库不存在");
        }

        List<BizOutboundItem> items = new ArrayList<BizOutboundItem>();
        BigDecimal totalQuantity = BigDecimal.ZERO;

        for (OutboundOrderRequest.Item reqItem : request.getItems()) {
            if (reqItem.getProductId() == null) {
                throw new BusinessException(400, "明细中存在未指定商品的行");
            }
            BaseProduct product = productMapper.selectById(reqItem.getProductId());
            if (product == null) {
                throw new BusinessException(404, "商品不存在：id=" + reqItem.getProductId());
            }
            BigDecimal need = reqItem.getQuantity() == null ? BigDecimal.ZERO : reqItem.getQuantity();
            if (need.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "商品 " + product.getProductCode() + " 的出库数量必须大于 0");
            }

            // 该商品在本仓库的全部库存，按“货位到出入口距离”升序 = 就近出库
            List<InvStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
                    .eq(InvStock::getWarehouseId, request.getWarehouseId())
                    .eq(InvStock::getProductId, reqItem.getProductId())
                    .gt(InvStock::getQuantity, BigDecimal.ZERO));
            if (stocks.isEmpty()) {
                throw new BusinessException(409, "商品 " + product.getProductCode() + " 无可用库存");
            }
            Map<Long, BigDecimal> distanceByLocation = new LinkedHashMap<Long, BigDecimal>();
            for (InvStock s : stocks) {
                BaseLocation loc = locationMapper.selectById(s.getLocationId());
                distanceByLocation.put(s.getLocationId(),
                        loc == null || loc.getDistance() == null ? BigDecimal.valueOf(Long.MAX_VALUE) : loc.getDistance());
            }
            stocks.sort(Comparator.comparing(s -> distanceByLocation.get(s.getLocationId())));

            BigDecimal available = BigDecimal.ZERO;
            for (InvStock s : stocks) {
                available = available.add(s.getQuantity());
            }
            if (available.compareTo(need) < 0) {
                throw new BusinessException(409, "商品 " + product.getProductCode()
                        + " 库存不足：需要 " + need.stripTrailingZeros().toPlainString()
                        + "，可用 " + available.stripTrailingZeros().toPlainString());
            }

            // 按就近顺序逐货位分配
            BigDecimal remaining = need;
            for (InvStock s : stocks) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal take = s.getQuantity().min(remaining);
                BizOutboundItem item = new BizOutboundItem();
                item.setProductId(reqItem.getProductId());
                item.setLocationId(s.getLocationId());
                item.setQuantity(take);
                item.setPickSeq(0);
                item.setPicked(0);
                item.setCreateTime(new Date());
                items.add(item);
                remaining = remaining.subtract(take);
            }
            totalQuantity = totalQuantity.add(need);
        }

        BizOutboundOrder order = new BizOutboundOrder();
        order.setOrderNo(nextOrderNo());
        order.setWarehouseId(request.getWarehouseId());
        order.setCustomer(request.getCustomer());
        order.setTotalQuantity(totalQuantity);
        order.setStatus(BizOutboundOrder.STATUS_PENDING);
        order.setRemark(request.getRemark());
        order.setCreateTime(new Date());
        orderMapper.insert(order);

        for (BizOutboundItem item : items) {
            item.setOrderId(order.getId());
            itemMapper.insert(item);
        }

        log.info("outbound order created: id={}, no={}, lines={}, totalQty={}",
                order.getId(), order.getOrderNo(), items.size(), totalQuantity);
        return orderMapper.selectById(order.getId());
    }

    @Override
    public List<BizOutboundOrder> listOrders(Integer status, Long warehouseId, int limit) {
        LambdaQueryWrapper<BizOutboundOrder> q = new LambdaQueryWrapper<BizOutboundOrder>();
        if (status != null) {
            q.eq(BizOutboundOrder::getStatus, status);
        }
        if (warehouseId != null) {
            q.eq(BizOutboundOrder::getWarehouseId, warehouseId);
        }
        q.orderByDesc(BizOutboundOrder::getId);
        q.last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        return orderMapper.selectList(q);
    }

    @Override
    public Map<String, Object> orderDetail(Long orderId) {
        BizOutboundOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "出库单不存在");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("order", order);
        data.put("items", itemMapper.selectDetailWithProduct(orderId));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> planRoute(Long orderId, String strategyCode) {
        BizOutboundOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "出库单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == BizOutboundOrder.STATUS_DONE) {
            throw new BusinessException(409, "该出库单已完成，无法重新规划路径");
        }
        if (order.getStatus() != null && order.getStatus() == BizOutboundOrder.STATUS_CANCELLED) {
            throw new BusinessException(409, "该出库单已取消");
        }

        List<Map<String, Object>> details = itemMapper.selectDetailWithProduct(orderId);
        if (details.isEmpty()) {
            throw new BusinessException(409, "该出库单没有明细，无法规划路径");
        }

        // 汇总拣货点（去重后参与路径规划）
        List<int[]> picks = new ArrayList<int[]>();
        List<Long> itemIds = new ArrayList<Long>();
        for (Map<String, Object> row : details) {
            Object aisle = row.get("aisleNo");
            Object rowNo = row.get("rowNo");
            if (aisle == null || rowNo == null) {
                throw new BusinessException(409, "存在没有货位坐标的明细行，无法规划路径");
            }
            int[] point = new int[]{toInt(aisle), toInt(rowNo)};
            boolean exists = false;
            for (int[] p : picks) {
                if (p[0] == point[0] && p[1] == point[1]) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                picks.add(point);
            }
            itemIds.add(toLong(row.get("id")));
        }

        BaseWarehouse warehouse = warehouseMapper.selectById(order.getWarehouseId());
        if (warehouse == null) {
            throw new BusinessException(404, "仓库不存在");
        }
        WarehouseLayout layout = toLayout(warehouse);
        PickPath path = new PickPath(layout, picks);

        // 对全部策略求解，便于横向对比
        Map<String, Double> comparison = new LinkedHashMap<String, Double>();
        Map<String, PickingRoute> solved = new LinkedHashMap<String, PickingRoute>();
        for (String code : pickingFactory.available().keySet()) {
            try {
                PickingRoute r = pickingFactory.get(code).solve(path, 20261011L);
                solved.put(code, r);
                comparison.put(code, round(r.getDistance(), 2));
            } catch (RuntimeException e) {
                log.warn("picking strategy {} failed on order {}: {}", code, order.getOrderNo(), e.getMessage());
            }
        }

        String chosen = (strategyCode == null || strategyCode.trim().isEmpty()) ? "ant-colony" : strategyCode.trim();
        PickingRoute route = solved.get(chosen);
        if (route == null) {
            route = pickingFactory.get(chosen).solve(path, 20261011L);
        }

        // 把访问顺序写回明细：坐标为 (0,0) 的起终点不参与，按路径顺序匹配
        Map<String, Integer> seqByPoint = new LinkedHashMap<String, Integer>();
        int seq = 0;
        for (int[] p : route.getRoute()) {
            if (p[0] == 0 && p[1] == 0) {
                continue;
            }
            String key = p[0] + "," + p[1];
            if (!seqByPoint.containsKey(key)) {
                seqByPoint.put(key, ++seq);
            }
        }
        int updated = 0;
        for (Map<String, Object> row : details) {
            String key = toInt(row.get("aisleNo")) + "," + toInt(row.get("rowNo"));
            Integer pickSeq = seqByPoint.get(key);
            if (pickSeq != null) {
                itemMapper.updatePickSeq(toLong(row.get("id")), pickSeq, 0);
                updated++;
            }
        }

        // 路径明细与算法信息写入单据
        BizOutboundOrder update = new BizOutboundOrder();
        update.setId(orderId);
        update.setRouteAlgo(chosen);
        update.setRouteDistance(BigDecimal.valueOf(route.getDistance()).setScale(2, RoundingMode.HALF_UP));
        update.setRouteDetail(toJson(route.getRoute()));
        update.setStatus(BizOutboundOrder.STATUS_PICKING);
        orderMapper.updateById(update);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("orderId", orderId);
        result.put("orderNo", order.getOrderNo());
        result.put("strategy", chosen);
        result.put("strategyLabel", pickingFactory.get(chosen).label());
        result.put("pickPointCount", picks.size());
        result.put("distance", round(route.getDistance(), 2));
        result.put("elapsedMillis", route.getElapsedMillis());
        result.put("updatedItems", updated);
        result.put("route", toPointList(route.getRoute()));
        result.put("comparison", comparison);
        log.info("picking route planned: order={}, strategy={}, points={}, distance={}, updatedItems={}",
                order.getOrderNo(), chosen, picks.size(), round(route.getDistance(), 2), updated);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmOutbound(Long orderId) {
        BizOutboundOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "出库单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == BizOutboundOrder.STATUS_DONE) {
            throw new BusinessException(409, "该出库单已完成，不能重复确认");
        }
        if (order.getStatus() != null && order.getStatus() == BizOutboundOrder.STATUS_CANCELLED) {
            throw new BusinessException(409, "该出库单已取消，不能确认出库");
        }

        List<BizOutboundItem> items = itemMapper.selectList(new LambdaQueryWrapper<BizOutboundItem>()
                .eq(BizOutboundItem::getOrderId, orderId).orderByAsc(BizOutboundItem::getId));
        if (items.isEmpty()) {
            throw new BusinessException(409, "该出库单没有明细，无法确认");
        }

        int shippedItems = 0;
        int freedLocations = 0;
        BigDecimal shipped = BigDecimal.ZERO;

        for (BizOutboundItem item : items) {
            // 1. 扣减库存
            InvStock stock = stockMapper.selectByLocationProductBatch(
                    item.getLocationId(), item.getProductId(), null);
            if (stock == null) {
                // 批次可能不同，退化为按货位+商品取第一条
                List<InvStock> candidates = stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
                        .eq(InvStock::getLocationId, item.getLocationId())
                        .eq(InvStock::getProductId, item.getProductId())
                        .gt(InvStock::getQuantity, BigDecimal.ZERO));
                if (!candidates.isEmpty()) {
                    stock = candidates.get(0);
                }
            }
            if (stock == null) {
                throw new BusinessException(409, "商品 id=" + item.getProductId()
                        + " 在货位 id=" + item.getLocationId() + " 上的库存已不存在，无法出库");
            }
            BigDecimal qty = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
            if (stock.getQuantity().compareTo(qty) < 0) {
                throw new BusinessException(409, "商品 id=" + item.getProductId()
                        + " 库存不足：需要 " + qty.stripTrailingZeros().toPlainString()
                        + "，实际 " + stock.getQuantity().stripTrailingZeros().toPlainString());
            }

            BigDecimal left = stock.getQuantity().subtract(qty);
            if (left.compareTo(BigDecimal.ZERO) <= 0) {
                stockMapper.deleteById(stock.getId());
            } else {
                InvStock stockUpdate = new InvStock();
                stockUpdate.setId(stock.getId());
                stockUpdate.setQuantity(left);
                stockUpdate.setUpdateTime(new Date());
                stockMapper.updateById(stockUpdate);
            }
            shippedItems++;
            shipped = shipped.add(qty);

            // 2. 该货位若已无任何库存，则释放为空闲
            Long leftAtLocation = stockMapper.selectCount(new LambdaQueryWrapper<InvStock>()
                    .eq(InvStock::getLocationId, item.getLocationId())
                    .gt(InvStock::getQuantity, BigDecimal.ZERO));
            if (leftAtLocation == null || leftAtLocation == 0) {
                BaseLocation update = new BaseLocation();
                update.setId(item.getLocationId());
                update.setStatus(LOCATION_FREE);
                locationMapper.updateById(update);
                freedLocations++;
            }

            // 3. 标记已拣
            BizOutboundItem itemUpdate = new BizOutboundItem();
            itemUpdate.setId(item.getId());
            itemUpdate.setPicked(1);
            itemMapper.updateById(itemUpdate);

            // 4. 累加商品出库频次：频次是货位分配算法的输入，必须准确
            productMapper.increasePickFreq(item.getProductId(),
                    qty.setScale(0, RoundingMode.HALF_UP).intValue());
        }

        BizOutboundOrder orderUpdate = new BizOutboundOrder();
        orderUpdate.setId(orderId);
        orderUpdate.setStatus(BizOutboundOrder.STATUS_DONE);
        orderUpdate.setOutboundTime(new Date());
        orderMapper.updateById(orderUpdate);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("orderId", orderId);
        result.put("orderNo", order.getOrderNo());
        result.put("shippedItems", shippedItems);
        result.put("freedLocations", freedLocations);
        result.put("shippedQuantity", shipped);
        result.put("status", BizOutboundOrder.STATUS_DONE);
        log.info("outbound confirmed: order={}, items={}, freedLocations={}, shippedQty={}",
                order.getOrderNo(), shippedItems, freedLocations, shipped);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        BizOutboundOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "出库单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == BizOutboundOrder.STATUS_DONE) {
            throw new BusinessException(409, "已完成的出库单不能取消");
        }
        if (order.getStatus() != null && order.getStatus() == BizOutboundOrder.STATUS_CANCELLED) {
            throw new BusinessException(409, "该出库单已取消");
        }
        BizOutboundOrder update = new BizOutboundOrder();
        update.setId(orderId);
        update.setStatus(BizOutboundOrder.STATUS_CANCELLED);
        orderMapper.updateById(update);
        log.info("outbound order cancelled: {}", order.getOrderNo());
    }

    @Override
    public List<Map<String, Object>> countByStatus() {
        return orderMapper.countGroupByStatus();
    }

    @Override
    public Map<String, String> availableStrategies() {
        return pickingFactory.available();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private String nextOrderNo() {
        String prefix = "OUT" + new SimpleDateFormat("yyyyMMdd").format(new Date());
        String maxOrderNo = orderMapper.selectMaxOrderNo(prefix);
        int seq = 1;
        if (maxOrderNo != null && maxOrderNo.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(maxOrderNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        return prefix + String.format("%04d", seq);
    }

    private WarehouseLayout toLayout(BaseWarehouse warehouse) {
        double aisleGap = warehouse.getAisleGap() == null ? 2.5 : warehouse.getAisleGap().doubleValue();
        double rowGap = warehouse.getRowGap() == null ? 1.2 : warehouse.getRowGap().doubleValue();
        int aisles = warehouse.getAisleCount() == null ? 1 : warehouse.getAisleCount();
        int rows = warehouse.getRowCount() == null ? 1 : warehouse.getRowCount();
        return new WarehouseLayout(aisles, rows, aisleGap, rowGap);
    }

    private String toJson(List<int[]> route) {
        try {
            List<List<Integer>> plain = new ArrayList<List<Integer>>();
            for (int[] p : route) {
                plain.add(Arrays.asList(p[0], p[1]));
            }
            return objectMapper.writeValueAsString(plain);
        } catch (Exception e) {
            log.warn("failed to serialize route detail: {}", e.getMessage());
            return "[]";
        }
    }

    private List<Map<String, Integer>> toPointList(List<int[]> route) {
        List<Map<String, Integer>> list = new ArrayList<Map<String, Integer>>();
        for (int[] p : route) {
            Map<String, Integer> m = new LinkedHashMap<String, Integer>();
            m.put("aisleNo", p[0]);
            m.put("rowNo", p[1]);
            list.add(m);
        }
        return list;
    }

    private static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static int toInt(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return Integer.parseInt(String.valueOf(v));
    }

    private static long toLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }
}
