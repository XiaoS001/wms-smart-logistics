package com.lijunjie.wms.service;

import com.lijunjie.wms.dto.StocktakeSubmitRequest;

import java.util.List;
import java.util.Map;

/**
 * 库存业务接口。
 */
public interface StockService {

    /**
     * 库存明细：按商品或仓库过滤，返回货位级的库存记录。
     *
     * @param warehouseId 仓库 ID，可为空
     * @param productId   商品 ID，可为空
     * @param limit       返回条数上限
     */
    List<Map<String, Object>> listStock(Long warehouseId, Long productId, int limit);

    /** 按商品汇总库存（含安全库存与预警标记） */
    List<Map<String, Object>> summaryByProduct(Long warehouseId);

    /**
     * 库存预警：库存低于安全库存的商品（缺货预警），以及临期库存。
     *
     * @param warehouseId 仓库 ID，可为空
     * @return 含 lowStock 与 nearExpiry 两个列表
     */
    Map<String, Object> alerts(Long warehouseId);

    /** 仓库库存概览：货位数、商品种类数、库存总量，以及货位占用率 */
    Map<String, Object> warehouseSummary(Long warehouseId);

    /**
     * 生成盘点任务。
     *
     * <p>以当前库存为基础生成账面数量，状态为待盘点。
     * 同一仓库同一盘点单号内的“货位 + 商品 + 批次”唯一。</p>
     *
     * @param warehouseId 仓库 ID
     * @return 生成的盘点单号与记录数
     */
    Map<String, Object> generateStocktake(Long warehouseId);

    /** 查询盘点明细 */
    List<Map<String, Object>> listStocktake(String stocktakeNo);

    /**
     * 提交盘点结果。
     *
     * <p>录入实盘数量并计算差异；若 {@code adjustStock} 为真，
     * 则把差异写回库存表，并同步货位占用状态。</p>
     *
     * @param request 盘点结果
     * @return 处理结果统计
     */
    Map<String, Object> submitStocktake(StocktakeSubmitRequest request);

    /** 撤销尚未调整的盘点单 */
    void cancelStocktake(String stocktakeNo);
}
