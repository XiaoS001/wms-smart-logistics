import { request } from '@/utils/request'
import type {
  BaseLocation,
  BaseWarehouse,
  BaseZone,
  GenerateLocationsRequest,
  GenerateLocationsResult,
  LocationSummary
} from '@/types/base'

/** 仓库管理接口 */
export const warehouseApi = {
  list(keyword?: string) {
    return request.get<BaseWarehouse[]>('/warehouses', { keyword })
  },
  detail(id: number) {
    return request.get<BaseWarehouse>(`/warehouses/${id}`)
  },
  create(data: Partial<BaseWarehouse>) {
    return request.post<BaseWarehouse>('/warehouses', data)
  },
  update(id: number, data: Partial<BaseWarehouse>) {
    return request.put<BaseWarehouse>(`/warehouses/${id}`, data)
  },
  remove(id: number) {
    return request.delete<void>(`/warehouses/${id}`)
  },
  /** 按仓库布局批量生成货位（幂等：默认跳过已存在坐标） */
  generateLocations(id: number, data: GenerateLocationsRequest) {
    return request.post<GenerateLocationsResult>(`/warehouses/${id}/locations/generate`, data)
  },
  /** 库区货位分布概览 */
  locationSummary(id: number) {
    return request.get<LocationSummary[]>(`/warehouses/${id}/locations/summary`)
  }
}

/** 库位（货位）接口 */
export const locationApi = {
  /** 货位列表：按到出入口距离升序返回 */
  list(params: { warehouseId?: number; zoneId?: number; status?: number; limit?: number }) {
    return request.get<BaseLocation[]>('/locations', params)
  },
  /** 最近的空闲货位，用于展示算法关注的近端货位 */
  nearestFree(warehouseId: number, limit = 20) {
    return request.get<BaseLocation[]>('/locations/nearest-free', { warehouseId, limit })
  }
}

/** 库区接口（库区随货位生成自动创建，此处提供只读访问） */
export const zoneApi = {
  /** 通过仓库详情间接获取：后端未单独开放库区接口，此处以货位汇总代替 */
  summary(warehouseId: number) {
    return warehouseApi.locationSummary(warehouseId)
  }
}

export type { BaseZone }
