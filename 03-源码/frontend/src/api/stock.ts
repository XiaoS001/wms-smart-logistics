import { request } from '@/utils/request'
import type { BizStocktake, StockDetail, StockSummary, WarehouseStockSummary } from '@/types/business'

/** 库存管理接口 */
export const stockApi = {
  /** 库存明细：按货位到出入口距离升序 */
  list(params: { warehouseId?: number; productId?: number; limit?: number }) {
    return request.get<StockDetail[]>('/stock', params)
  },

  /** 按商品汇总库存（含缺货标记） */
  summary(warehouseId?: number) {
    return request.get<StockSummary[]>('/stock/summary', { warehouseId })
  },

  /** 缺货与临期预警 */
  alerts(warehouseId?: number) {
    return request.get<{
      nearExpiryDays: number
      lowStockCount: number
      nearExpiryCount: number
      lowStock: StockSummary[]
      nearExpiry: Array<Record<string, unknown>>
    }>('/stock/alerts', { warehouseId })
  },

  /** 仓库库存概览：占用率、库存总量、商品种类数 */
  warehouseSummary(warehouseId: number) {
    return request.get<WarehouseStockSummary>(`/stock/warehouse/${warehouseId}/summary`)
  },

  /* ------------------------- 盘点 ------------------------- */

  /** 生成盘点任务 */
  generateStocktake(warehouseId: number) {
    return request.post<{ stocktakeNo: string; warehouseId: number; recordCount: number }>(
      '/stock/stocktake/generate',
      null,
      { params: { warehouseId } }
    )
  },

  listStocktake(stocktakeNo?: string) {
    return request.get<BizStocktake[]>('/stock/stocktake', { stocktakeNo })
  },

  /** 提交盘点结果；adjustStock 为真时把差异写回库存 */
  submitStocktake(data: {
    stocktakeNo: string
    adjustStock: boolean
    remark?: string
    entries: Array<{ id: number; actualQuantity: number }>
  }) {
    return request.post<{
      stocktakeNo: string
      countedRows: number
      adjustedRows: number
      adjustStock: boolean
      diffRows: number
      absDiffSum: number
      summary: Record<string, unknown>
    }>('/stock/stocktake/submit', data)
  },

  cancelStocktake(stocktakeNo: string) {
    return request.delete<void>(`/stock/stocktake/${stocktakeNo}`)
  }
}
