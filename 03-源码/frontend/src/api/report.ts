import { request } from '@/utils/request'
import type {
  DashboardData,
  FrequencyContributionResult,
  OperationTrendResult,
  PickingEffectResult,
  SlottingEffectResult,
  StockDistributionResult
} from '@/types/business'

/** 报表统计接口 */
export const reportApi = {
  /** 首页概览：商品、货位、库存、单据与缺货预警的关键指标 */
  dashboard() {
    return request.get<DashboardData>('/report/dashboard')
  },

  /** 作业量趋势：按日期统计出入库单量与数量，无数据日期补 0 */
  operationTrend(days = 30) {
    return request.get<OperationTrendResult>('/report/operation-trend', { days })
  },

  /** 库存分布：按库区与按 ABC 分类两个维度 */
  stockDistribution(warehouseId?: number) {
    return request.get<StockDistributionResult>('/report/stock-distribution', { warehouseId })
  },

  /** 货位分配优化效果：各策略目标值与相对随机分配的降幅 */
  slottingEffect(warehouseId: number) {
    return request.get<SlottingEffectResult>(`/report/slotting-effect/${warehouseId}`)
  },

  /** 拣货路径优化效果：以当前有库存的货位为拣货点，比较各策略路径长度 */
  pickingEffect(warehouseId: number) {
    return request.get<PickingEffectResult>(`/report/picking-effect/${warehouseId}`)
  },

  /** 真实单据上的算法表现 */
  routeAlgorithmStatistics() {
    return request.get<Array<{ routeAlgo: string; orderCount: number; avgDistance: number }>>(
      '/report/route-algorithm-statistics'
    )
  },

  /** 出库频次贡献结构：说明 ABC 分类的划分依据 */
  frequencyContribution(limit = 20) {
    return request.get<FrequencyContributionResult>('/report/frequency-contribution', { limit })
  }
}

/** 健康检查接口 */
export const healthApi = {
  ping() {
    return request.get<string>('/health/ping')
  },
  health() {
    return request.get<Record<string, unknown>>('/health')
  }
}
