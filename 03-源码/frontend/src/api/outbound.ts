import { request } from '@/utils/request'
import type {
  BizOutboundItem,
  BizOutboundOrder,
  OutboundOrderRequest,
  PlanRouteResult
} from '@/types/business'

/** 出库管理接口 */
export const outboundApi = {
  list(params: { status?: number; warehouseId?: number; limit?: number }) {
    return request.get<BizOutboundOrder[]>('/outbound/orders', params)
  },

  /** 单据详情：明细带出货位坐标（aisleNo / rowNo）与拣货顺序 */
  detail(id: number) {
    return request.get<{ order: BizOutboundOrder; items: BizOutboundItem[] }>(`/outbound/orders/${id}`)
  },

  /** 创建出库单：后端按“就近出库”原则从最近货位分配库存 */
  create(data: OutboundOrderRequest) {
    return request.post<BizOutboundOrder>('/outbound/orders', data)
  },

  /**
   * 规划拣货路径。
   * 返回选定策略的路径与距离，同时给出全部策略的对比，便于直观展示算法优化效果。
   */
  planRoute(id: number, strategy = 'ant-colony') {
    return request.post<PlanRouteResult>(`/outbound/orders/${id}/plan-route`, null, {
      params: { strategy }
    })
  },

  /** 出库确认：扣减库存、释放空货位、累加商品出库频次 */
  confirm(id: number) {
    return request.post<{
      orderId: number
      orderNo: string
      shippedItems: number
      freedLocations: number
      shippedQuantity: number
      status: number
    }>(`/outbound/orders/${id}/confirm`)
  },

  cancel(id: number) {
    return request.post<void>(`/outbound/orders/${id}/cancel`)
  },

  statistics() {
    return request.get<Array<{ status: number; orderCount: number }>>('/outbound/statistics')
  },

  /** 可用路径规划策略 */
  strategies() {
    return request.get<Record<string, string>>('/outbound/strategies')
  }
}
