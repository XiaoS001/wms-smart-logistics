package com.lijunjie.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.dto.InboundOrderRequest;
import com.lijunjie.wms.dto.SlottingRecommendResult;
import com.lijunjie.wms.entity.BaseLocation;
import com.lijunjie.wms.entity.BaseProduct;
import com.lijunjie.wms.entity.BaseWarehouse;
import com.lijunjie.wms.entity.BizInboundItem;
import com.lijunjie.wms.entity.BizInboundOrder;
import com.lijunjie.wms.entity.InvStock;
import com.lijunjie.wms.mapper.BaseLocationMapper;
import com.lijunjie.wms.mapper.BaseProductMapper;
import com.lijunjie.wms.mapper.BaseWarehouseMapper;
import com.lijunjie.wms.mapper.BizInboundItemMapper;
import com.lijunjie.wms.mapper.BizInboundOrderMapper;
import com.lijunjie.wms.mapper.InvStockMapper;
import com.lijunjie.wms.service.InboundService;
import com.lijunjie.wms.service.SlottingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入库业务实现。
 *
 * <p>本模块是算法与业务的第一个真正结合点：创建入库单时可调用货位分配算法
 * 为每个商品推荐目标货位；入库确认时写入库存并占用货位，从而保证后续算法
 * 读取到的空闲货位始终准确。</p>
 */
@Service
public class InboundServiceImpl implements InboundService {

    private static final Logger log = LoggerFactory.getLogger(InboundServiceImpl.class);

    /** 单据状态：待入库 */
    private static final int STATUS_PENDING = 0;

    /** 单据状态：已完成 */
    private static final int STATUS_DONE = 2;

    /** 单据状态：已取消 */
    private static final int STATUS_CANCELLED = 9;

    /** 货位状态：空闲 */
    private static final int LOCATION_FREE = 1;

    /** 货位状态：占用 */
    private static final int LOCATION_USED = 2;

    private final BizInboundOrderMapper orderMapper;
    private final BizInboundItemMapper itemMapper;
    private final BaseProductMapper productMapper;
    private final BaseLocationMapper locationMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final InvStockMapper stockMapper;
    private final SlottingService slottingService;

    public InboundServiceImpl(BizInboundOrderMapper orderMapper,
                              BizInboundItemMapper itemMapper,
                              BaseProductMapper productMapper,
                              BaseLocationMapper locationMapper,
                              BaseWarehouseMapper warehouseMapper,
                              InvStockMapper stockMapper,
                              SlottingService slottingService) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.productMapper = productMapper;
        this.locationMapper = locationMapper;
        this.warehouseMapper = warehouseMapper;
        this.stockMapper = stockMapper;
        this.slottingService = slottingService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizInboundOrder createOrder(InboundOrderRequest request) {
        if (request == null || request.getWarehouseId() == null) {
            throw new BusinessException(400, "仓库不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(400, "入库明细不能为空");
        }
        BaseWarehouse warehouse = warehouseMapper.selectById(request.getWarehouseId());
        if (warehouse == null) {
            throw new BusinessException(404, "仓库不存在");
        }

        // 1. 组装并校验明细
        List<BizInboundItem> items = new ArrayList<BizInboundItem>();
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InboundOrderRequest.Item reqItem : request.getItems()) {
            if (reqItem.getProductId() == null) {
                throw new BusinessException(400, "明细中存在未指定商品的行");
            }
            BaseProduct product = productMapper.selectById(reqItem.getProductId());
            if (product == null) {
                throw new BusinessException(404, "商品不存在：id=" + reqItem.getProductId());
            }
            BigDecimal qty = reqItem.getPlanQuantity() == null ? BigDecimal.ZERO : reqItem.getPlanQuantity();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "商品 " + product.getProductCode() + " 的计划数量必须大于 0");
            }

            BizInboundItem item = new BizInboundItem();
            item.setProductId(reqItem.getProductId());
            item.setLocationId(reqItem.getLocationId());
            item.setPlanQuantity(qty);
            item.setActualQuantity(BigDecimal.ZERO);
            item.setUnitPrice(reqItem.getUnitPrice());
            item.setBatchNo(reqItem.getBatchNo());
            item.setCreateTime(new Date());
            items.add(item);

            totalQuantity = totalQuantity.add(qty);
            if (reqItem.getUnitPrice() != null) {
                totalAmount = totalAmount.add(reqItem.getUnitPrice().multiply(qty));
            }
        }

        // 2. 创建单据主体
        BizInboundOrder order = new BizInboundOrder();
        order.setOrderNo(nextOrderNo());
        order.setWarehouseId(request.getWarehouseId());
        order.setSupplier(request.getSupplier());
        order.setTotalQuantity(totalQuantity);
        order.setTotalAmount(totalAmount);
        order.setStatus(STATUS_PENDING);
        boolean useSuggest = Boolean.TRUE.equals(request.getUseSlottingSuggest());
        order.setUseSlottingSuggest(useSuggest ? 1 : 0);
        order.setRemark(request.getRemark());
        order.setCreateTime(new Date());
        orderMapper.insert(order);

        // 3. 算法推荐货位（可选）
        if (useSuggest) {
            applySlottingSuggestion(order, items, request.getStrategy());
        }

        // 4. 写入明细
        for (BizInboundItem item : items) {
            item.setOrderId(order.getId());
            itemMapper.insert(item);
        }

        log.info("inbound order created: id={}, no={}, items={}, useSuggest={}",
                order.getId(), order.getOrderNo(), items.size(), useSuggest);
        return orderMapper.selectById(order.getId());
    }

    /**
     * 调用货位分配算法为明细行推荐货位。
     *
     * <p>推荐结果按商品 ID 匹配到明细行；已有手工指定货位的行保持不动，
     * 体现“人工优先、算法辅助”的设计原则。</p>
     */
    private void applySlottingSuggestion(BizInboundOrder order, List<BizInboundItem> items, String strategy) {
        List<Long> productIds = new ArrayList<Long>();
        for (BizInboundItem item : items) {
            if (item.getLocationId() == null) {
                productIds.add(item.getProductId());
            }
        }
        if (productIds.isEmpty()) {
            log.info("all inbound items already have a manual location; slotting skipped");
            return;
        }
        String strategyCode = (strategy == null || strategy.trim().isEmpty())
                ? "frequency-greedy" : strategy.trim();

        SlottingRecommendResult recommendation;
        try {
            recommendation = slottingService.recommend(order.getWarehouseId(), productIds, strategyCode, 20261011L);
        } catch (BusinessException e) {
            // 算法不可用（例如无空闲货位）不应阻塞单据创建，仅记录警告
            log.warn("slotting recommendation unavailable for order {}: {}", order.getOrderNo(), e.getMessage());
            return;
        }

        Map<Long, SlottingRecommendResult.Recommendation> byProduct =
                new HashMap<Long, SlottingRecommendResult.Recommendation>();
        for (SlottingRecommendResult.Recommendation rec : recommendation.getRecommendations()) {
            byProduct.put(rec.getProductId(), rec);
        }

        int suggested = 0;
        for (BizInboundItem item : items) {
            if (item.getLocationId() != null) {
                continue;
            }
            SlottingRecommendResult.Recommendation rec = byProduct.get(item.getProductId());
            if (rec != null && rec.getLocationId() != null) {
                item.setLocationId(rec.getLocationId());
                item.setSuggestReason(rec.getReason());
                suggested++;
            }
        }
        log.info("slotting suggestion applied: order={}, strategy={}, suggested={}, objective={}",
                order.getOrderNo(), strategyCode, suggested, recommendation.getObjective());
    }

    @Override
    public List<BizInboundOrder> listOrders(Integer status, Long warehouseId, int limit) {
        LambdaQueryWrapper<BizInboundOrder> q = new LambdaQueryWrapper<BizInboundOrder>();
        if (status != null) {
            q.eq(BizInboundOrder::getStatus, status);
        }
        if (warehouseId != null) {
            q.eq(BizInboundOrder::getWarehouseId, warehouseId);
        }
        q.orderByDesc(BizInboundOrder::getId);
        q.last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        return orderMapper.selectList(q);
    }

    @Override
    public Map<String, Object> orderDetail(Long orderId) {
        BizInboundOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "入库单不存在");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("order", order);
        data.put("items", itemMapper.selectDetailWithProduct(orderId));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmInbound(Long orderId) {
        BizInboundOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "入库单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == STATUS_DONE) {
            throw new BusinessException(409, "该入库单已完成，不能重复确认");
        }
        if (order.getStatus() != null && order.getStatus() == STATUS_CANCELLED) {
            throw new BusinessException(409, "该入库单已取消，不能确认入库");
        }

        List<BizInboundItem> items = itemMapper.selectList(new LambdaQueryWrapper<BizInboundItem>()
                .eq(BizInboundItem::getOrderId, orderId).orderByAsc(BizInboundItem::getId));
        if (items.isEmpty()) {
            throw new BusinessException(409, "该入库单没有明细，无法确认");
        }

        int stockedItems = 0;
        int occupiedLocations = 0;
        for (BizInboundItem item : items) {
            BigDecimal qty = item.getActualQuantity() != null
                    && item.getActualQuantity().compareTo(BigDecimal.ZERO) > 0
                    ? item.getActualQuantity()
                    : (item.getPlanQuantity() == null ? BigDecimal.ZERO : item.getPlanQuantity());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (item.getLocationId() == null) {
                throw new BusinessException(409,
                        "商品 id=" + item.getProductId() + " 未指定货位，无法完成入库");
            }

            // 1. 库存：存在则累加，不存在则新增
            InvStock stock = stockMapper.selectByLocationProductBatch(
                    item.getLocationId(), item.getProductId(), item.getBatchNo());
            if (stock == null) {
                stock = new InvStock();
                stock.setWarehouseId(order.getWarehouseId());
                stock.setLocationId(item.getLocationId());
                stock.setProductId(item.getProductId());
                stock.setQuantity(qty);
                stock.setLockedQuantity(BigDecimal.ZERO);
                stock.setBatchNo(item.getBatchNo());
                stock.setUpdateTime(new Date());
                stockMapper.insert(stock);
            } else {
                stockMapper.increaseQuantity(stock.getId(), qty);
            }
            stockedItems++;

            // 2. 货位占用状态
            BaseLocation location = locationMapper.selectById(item.getLocationId());
            if (location != null && (location.getStatus() == null || location.getStatus() == LOCATION_FREE)) {
                BaseLocation update = new BaseLocation();
                update.setId(location.getId());
                update.setStatus(LOCATION_USED);
                locationMapper.updateById(update);
                occupiedLocations++;
            }

            // 3. 记录实收数量
            BizInboundItem itemUpdate = new BizInboundItem();
            itemUpdate.setId(item.getId());
            itemUpdate.setActualQuantity(qty);
            itemMapper.updateById(itemUpdate);
        }

        // 4. 单据状态
        BizInboundOrder orderUpdate = new BizInboundOrder();
        orderUpdate.setId(orderId);
        orderUpdate.setStatus(STATUS_DONE);
        orderUpdate.setInboundTime(new Date());
        orderMapper.updateById(orderUpdate);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("orderId", orderId);
        result.put("orderNo", order.getOrderNo());
        result.put("stockedItems", stockedItems);
        result.put("occupiedLocations", occupiedLocations);
        result.put("status", STATUS_DONE);
        log.info("inbound confirmed: order={}, items={}, occupiedLocations={}",
                order.getOrderNo(), stockedItems, occupiedLocations);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        BizInboundOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "入库单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == STATUS_DONE) {
            throw new BusinessException(409, "已完成的入库单不能取消");
        }
        if (order.getStatus() != null && order.getStatus() == STATUS_CANCELLED) {
            throw new BusinessException(409, "该入库单已取消");
        }
        BizInboundOrder update = new BizInboundOrder();
        update.setId(orderId);
        update.setStatus(STATUS_CANCELLED);
        orderMapper.updateById(update);
        log.info("inbound order cancelled: {}", order.getOrderNo());
    }

    @Override
    public List<Map<String, Object>> countByStatus() {
        return orderMapper.countGroupByStatus();
    }

    /**
     * 生成入库单号：IN + yyyyMMdd + 4 位当日流水号。
     *
     * <p>流水号按当日已有单号的最大值递增，保证同一仓库同一天内不重复。</p>
     */
    private String nextOrderNo() {
        String prefix = "IN" + new SimpleDateFormat("yyyyMMdd").format(new Date());
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
}
