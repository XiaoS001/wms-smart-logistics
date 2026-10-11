package com.lijunjie.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.dto.StocktakeSubmitRequest;
import com.lijunjie.wms.entity.BaseLocation;
import com.lijunjie.wms.entity.BaseProduct;
import com.lijunjie.wms.entity.BaseWarehouse;
import com.lijunjie.wms.entity.BizStocktake;
import com.lijunjie.wms.entity.InvStock;
import com.lijunjie.wms.mapper.BaseLocationMapper;
import com.lijunjie.wms.mapper.BaseProductMapper;
import com.lijunjie.wms.mapper.BaseWarehouseMapper;
import com.lijunjie.wms.mapper.BizStocktakeMapper;
import com.lijunjie.wms.mapper.InvStockMapper;
import com.lijunjie.wms.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存业务实现。
 *
 * <p>本模块维护库存的查询、预警与盘点。库存数据是货位分配算法的直接输入
 * （空闲货位判定依赖库存），因此盘点调整后必须同步货位占用状态，避免算法读到脏数据。</p>
 */
@Service
public class StockServiceImpl implements StockService {

    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

    /** 货位状态：空闲 */
    private static final int LOCATION_FREE = 1;

    /** 货位状态：占用 */
    private static final int LOCATION_USED = 2;

    /** 临期预警天数 */
    private static final int NEAR_EXPIRY_DAYS = 30;

    private final InvStockMapper stockMapper;
    private final BaseProductMapper productMapper;
    private final BaseLocationMapper locationMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final BizStocktakeMapper stocktakeMapper;

    public StockServiceImpl(InvStockMapper stockMapper,
                            BaseProductMapper productMapper,
                            BaseLocationMapper locationMapper,
                            BaseWarehouseMapper warehouseMapper,
                            BizStocktakeMapper stocktakeMapper) {
        this.stockMapper = stockMapper;
        this.productMapper = productMapper;
        this.locationMapper = locationMapper;
        this.warehouseMapper = warehouseMapper;
        this.stocktakeMapper = stocktakeMapper;
    }

    @Override
    public List<Map<String, Object>> listStock(Long warehouseId, Long productId, int limit) {
        return stockMapper.selectDetail(warehouseId, productId, Math.max(1, Math.min(limit, 2000)));
    }

    @Override
    public List<Map<String, Object>> summaryByProduct(Long warehouseId) {
        List<Map<String, Object>> rows = stockMapper.summaryByProduct(warehouseId);
        // 计算预警标记：库存为 0 或低于安全库存
        for (Map<String, Object> row : rows) {
            BigDecimal total = toDecimal(row.get("totalQuantity"));
            BigDecimal safety = toDecimal(row.get("safetyStock"));
            boolean low = total.compareTo(BigDecimal.ZERO) <= 0
                    || (safety.compareTo(BigDecimal.ZERO) > 0 && total.compareTo(safety) < 0);
            row.put("lowStock", low ? 1 : 0);
        }
        return rows;
    }

    @Override
    public Map<String, Object> alerts(Long warehouseId) {
        List<Map<String, Object>> lowStock = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : summaryByProduct(warehouseId)) {
            Object flag = row.get("lowStock");
            if (flag != null && Integer.parseInt(String.valueOf(flag)) == 1) {
                lowStock.add(row);
            }
        }
        List<Map<String, Object>> nearExpiry = stockMapper.selectNearExpiry(warehouseId, NEAR_EXPIRY_DAYS);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("nearExpiryDays", NEAR_EXPIRY_DAYS);
        result.put("lowStockCount", lowStock.size());
        result.put("nearExpiryCount", nearExpiry.size());
        result.put("lowStock", lowStock);
        result.put("nearExpiry", nearExpiry);
        return result;
    }

    @Override
    public Map<String, Object> warehouseSummary(Long warehouseId) {
        BaseWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException(404, "仓库不存在");
        }
        Map<String, Object> summary = stockMapper.summaryByWarehouse(warehouseId);
        long totalLocations = locationMapper.selectCount(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getWarehouseId, warehouseId));
        long usedLocations = locationMapper.selectCount(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getWarehouseId, warehouseId)
                .eq(BaseLocation::getStatus, LOCATION_USED));

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("warehouseId", warehouseId);
        result.put("warehouseCode", warehouse.getWarehouseCode());
        result.put("warehouseName", warehouse.getWarehouseName());
        result.put("locationTotal", warehouse.getAisleCount() == null || warehouse.getRowCount() == null
                ? totalLocations : warehouse.totalSlots());
        result.put("locationRows", totalLocations);
        result.put("locationUsed", usedLocations);
        result.put("locationFree", totalLocations - usedLocations);
        result.put("usageRate", totalLocations == 0 ? 0.0
                : BigDecimal.valueOf(usedLocations * 100.0 / totalLocations)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue());
        result.put("productKinds", summary == null ? 0 : summary.get("productKinds"));
        result.put("totalQuantity", summary == null ? 0 : summary.get("totalQuantity"));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateStocktake(Long warehouseId) {
        BaseWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException(404, "仓库不存在");
        }
        List<InvStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
                .eq(InvStock::getWarehouseId, warehouseId)
                .gt(InvStock::getQuantity, BigDecimal.ZERO)
                .orderByAsc(InvStock::getLocationId));
        if (stocks.isEmpty()) {
            throw new BusinessException(409, "该仓库暂无库存记录，无需盘点");
        }

        String stocktakeNo = nextStocktakeNo();
        Date now = new Date();
        for (InvStock stock : stocks) {
            BizStocktake record = new BizStocktake();
            record.setStocktakeNo(stocktakeNo);
            record.setWarehouseId(warehouseId);
            record.setLocationId(stock.getLocationId());
            record.setProductId(stock.getProductId());
            record.setBookQuantity(stock.getQuantity());
            record.setActualQuantity(BigDecimal.ZERO);
            record.setDiffQuantity(BigDecimal.ZERO);
            record.setStatus(BizStocktake.STATUS_PENDING);
            record.setCreateTime(now);
            stocktakeMapper.insert(record);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("stocktakeNo", stocktakeNo);
        result.put("warehouseId", warehouseId);
        result.put("recordCount", stocks.size());
        log.info("stocktake generated: no={}, warehouse={}, records={}", stocktakeNo, warehouseId, stocks.size());
        return result;
    }

    @Override
    public List<Map<String, Object>> listStocktake(String stocktakeNo) {
        return stocktakeMapper.selectDetail(stocktakeNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitStocktake(StocktakeSubmitRequest request) {
        if (request == null || request.getStocktakeNo() == null || request.getStocktakeNo().trim().isEmpty()) {
            throw new BusinessException(400, "盘点单号不能为空");
        }
        if (request.getEntries() == null || request.getEntries().isEmpty()) {
            throw new BusinessException(400, "实盘明细不能为空");
        }
        String stocktakeNo = request.getStocktakeNo().trim();
        boolean adjust = Boolean.TRUE.equals(request.getAdjustStock());

        int counted = 0;
        int adjusted = 0;
        int diffRows = 0;
        BigDecimal absDiffSum = BigDecimal.ZERO;

        for (StocktakeSubmitRequest.Entry entry : request.getEntries()) {
            if (entry.getId() == null) {
                throw new BusinessException(400, "实盘明细缺少记录 ID");
            }
            BizStocktake record = stocktakeMapper.selectById(entry.getId());
            if (record == null) {
                throw new BusinessException(404, "盘点记录不存在：id=" + entry.getId());
            }
            if (!stocktakeNo.equals(record.getStocktakeNo())) {
                throw new BusinessException(400, "盘点记录 id=" + entry.getId() + " 不属于盘点单 " + stocktakeNo);
            }
            if (record.getStatus() != null && record.getStatus() == BizStocktake.STATUS_ADJUSTED) {
                throw new BusinessException(409, "盘点记录 id=" + entry.getId() + " 已调整，不能重复提交");
            }
            BigDecimal actual = entry.getActualQuantity() == null ? BigDecimal.ZERO : entry.getActualQuantity();
            if (actual.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(400, "实盘数量不能为负数：id=" + entry.getId());
            }
            BigDecimal book = record.getBookQuantity() == null ? BigDecimal.ZERO : record.getBookQuantity();
            BigDecimal diff = actual.subtract(book);

            BizStocktake update = new BizStocktake();
            update.setId(record.getId());
            update.setActualQuantity(actual);
            update.setDiffQuantity(diff);
            update.setStocktakeTime(new Date());
            update.setRemark(request.getRemark());
            update.setStatus(adjust ? BizStocktake.STATUS_ADJUSTED : BizStocktake.STATUS_COUNTED);
            stocktakeMapper.updateById(update);
            counted++;
            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                diffRows++;
                absDiffSum = absDiffSum.add(diff.abs());
            }

            if (!adjust) {
                continue;
            }

            // 把差异写回库存：实盘为 0 则删除库存记录，否则更新数量
            InvStock stock = stockMapper.selectByLocationProductBatch(
                    record.getLocationId(), record.getProductId(), null);
            if (stock == null) {
                List<InvStock> candidates = stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
                        .eq(InvStock::getLocationId, record.getLocationId())
                        .eq(InvStock::getProductId, record.getProductId()));
                if (!candidates.isEmpty()) {
                    stock = candidates.get(0);
                }
            }
            if (actual.compareTo(BigDecimal.ZERO) <= 0) {
                if (stock != null) {
                    stockMapper.deleteById(stock.getId());
                }
                setLocationStatus(record.getLocationId(), LOCATION_FREE);
            } else if (stock == null) {
                InvStock created = new InvStock();
                created.setWarehouseId(record.getWarehouseId());
                created.setLocationId(record.getLocationId());
                created.setProductId(record.getProductId());
                created.setQuantity(actual);
                created.setLockedQuantity(BigDecimal.ZERO);
                created.setUpdateTime(new Date());
                stockMapper.insert(created);
                setLocationStatus(record.getLocationId(), LOCATION_USED);
            } else {
                InvStock stockUpdate = new InvStock();
                stockUpdate.setId(stock.getId());
                stockUpdate.setQuantity(actual);
                stockUpdate.setUpdateTime(new Date());
                stockMapper.updateById(stockUpdate);
                setLocationStatus(record.getLocationId(), LOCATION_USED);
            }
            adjusted++;
        }

        Map<String, Object> summary = stocktakeMapper.summaryByNo(stocktakeNo);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("stocktakeNo", stocktakeNo);
        result.put("countedRows", counted);
        result.put("adjustedRows", adjusted);
        result.put("adjustStock", adjust);
        result.put("diffRows", diffRows);
        result.put("absDiffSum", absDiffSum);
        result.put("summary", summary);
        log.info("stocktake submitted: no={}, counted={}, diffRows={}, adjusted={}, absDiffSum={}",
                stocktakeNo, counted, diffRows, adjusted, absDiffSum);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelStocktake(String stocktakeNo) {
        if (stocktakeNo == null || stocktakeNo.trim().isEmpty()) {
            throw new BusinessException(400, "盘点单号不能为空");
        }
        String no = stocktakeNo.trim();
        List<BizStocktake> records = stocktakeMapper.selectList(new LambdaQueryWrapper<BizStocktake>()
                .eq(BizStocktake::getStocktakeNo, no));
        if (records.isEmpty()) {
            throw new BusinessException(404, "盘点单不存在：" + no);
        }
        for (BizStocktake record : records) {
            if (record.getStatus() != null && record.getStatus() == BizStocktake.STATUS_ADJUSTED) {
                throw new BusinessException(409, "盘点单 " + no + " 已调整库存，不能撤销");
            }
        }
        stocktakeMapper.deleteByStocktakeNo(no);
        log.info("stocktake cancelled: no={}, records={}", no, records.size());
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private void setLocationStatus(Long locationId, int status) {
        BaseLocation update = new BaseLocation();
        update.setId(locationId);
        update.setStatus(status);
        locationMapper.updateById(update);
    }

    private String nextStocktakeNo() {
        String prefix = "ST" + new SimpleDateFormat("yyyyMMdd").format(new Date());
        String maxNo = stocktakeMapper.selectMaxStocktakeNo(prefix);
        int seq = 1;
        if (maxNo != null && maxNo.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(maxNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        return prefix + String.format("%04d", seq);
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        return new BigDecimal(String.valueOf(v));
    }
}
