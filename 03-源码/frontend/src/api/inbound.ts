import { request } from '@/utils/request'
import type { BizInboundItem, BizInboundOrder, InboundOrderRequest } from '@/types/business'

/** 入库管理接口 */
export const inboundApi = {
  list(params: { status?: number; warehouseId?: number; limit?: number }) {
    return request.get<BizInboundOrder[]>('/inbound/orders', params)
  },

  /** 单据详情：明细带出商品、推荐货位与推荐理由 */
  detail(id: number) {
    return request.get<{ order: BizInboundOrder; items: BizInboundItem[] }>(`/inbound/orders/${id}`)
  },

  /** 创建入库单；useSlottingSuggest 为真时后端会调用算法推荐货位 */
  create(data: InboundOrderRequest) {
    return request.post<BizInboundOrder>('/inbound/orders', data)
  },

  /** 入库确认：写入库存、占用货位并完成单据 */
  confirm(id: number) {
    return request.post<{
      orderId: number
      orderNo: string
      stockedItems: number
      occupiedLocations: number
      status: number
    }>(`/inbound/orders/${id}/confirm`)
  },

  cancel(id: number) {
    return request.post<void>(`/inbound/orders/${id}/cancel`)
  },

  statistics() {
    return request.get<Array<{ status: number; orderCount: number }>>('/inbound/statistics')
  }
}
