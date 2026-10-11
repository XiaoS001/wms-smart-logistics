import { request } from '@/utils/request'
import type {
  SlottingPlan,
  SlottingPlanPreview,
  SlottingRecommendResult
} from '@/types/business'

/** 货位分配算法与优化方案接口 */
export const slottingApi = {
  /** 可用分配策略 */
  strategies() {
    return request.get<Record<string, string>>('/slotting/strategies')
  },

  /** 对整仓执行货位分配求解，返回目标函数值（加权拣货行走距离） */
  solve(warehouseId: number, strategy = 'frequency-greedy', seed = 20261011) {
    return request.post<{
      strategy: string
      objective: number
      itemCount: number
      elapsedMillis: number
      slotOfItem: number[]
    }>('/slotting/solve', null, { params: { warehouseId, strategy, seed } })
  },

  /**
   * 为指定商品推荐货位。
   * @param productIds 商品 ID 列表；为空时对全部商品给出推荐
   */
  recommend(warehouseId: number, productIds?: number[], strategy = 'frequency-greedy', seed = 20261011) {
    return request.post<SlottingRecommendResult>('/slotting/recommend/' + warehouseId, null, {
      params: {
        productIds: productIds && productIds.length ? productIds.join(',') : undefined,
        strategy,
        seed
      }
    })
  },

  /** 优化方案预览（不落库）：返回当前与优化后目标值、降幅、分配明细与搬迁清单 */
  preview(warehouseId: number, strategy = 'frequency-greedy', seed = 20261011) {
    return request.post<SlottingPlanPreview>('/slotting/plans/preview', null, {
      params: { warehouseId, strategy, seed }
    })
  },

  /** 生成并保存优化方案 */
  savePlan(warehouseId: number, strategy = 'frequency-greedy', seed = 20261011, remark?: string) {
    return request.post<SlottingPlanPreview>('/slotting/plans', null, {
      params: { warehouseId, strategy, seed, remark }
    })
  },

  /** 应用方案：把库存记录搬迁到优化后的货位 */
  applyPlan(planId: number) {
    return request.post<{
      planId: number
      planNo: string
      strategy: string
      movedRecords: number
      occupiedLocations: number
      moves: Array<Record<string, unknown>>
      multiBatchNotes: Array<Record<string, unknown>>
      currentObjective: number
      optimizedObjective: number
      improvementPercent: number
    }>(`/slotting/plans/${planId}/apply`)
  },

  /** 优化方案历史 */
  listPlans(params: { warehouseId?: number; applied?: number; limit?: number }) {
    return request.get<SlottingPlan[]>('/slotting/plans', params)
  },

  /** 方案详情（含分配数组） */
  planDetail(planId: number) {
    return request.get<SlottingPlan & { seed: number; slotOfProduct: number[] }>(
      `/slotting/plans/${planId}`
    )
  },

  /** 删除未应用的方案 */
  deletePlan(planId: number) {
    return request.delete<void>(`/slotting/plans/${planId}`)
  },

  /** 按策略统计历史优化效果 */
  planStatistics() {
    return request.get<Array<Record<string, unknown>>>('/slotting/plans/statistics')
  }
}
