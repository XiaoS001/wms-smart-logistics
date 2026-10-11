import { request } from '@/utils/request'
import type { AbcClassifyResult, BaseCategory, BaseProduct, CategoryTreeNode } from '@/types/base'

/** 商品管理接口 */
export const productApi = {
  /** 商品列表：按出库频次降序，支持关键字、分类、ABC 分类与状态过滤 */
  list(params: {
    keyword?: string
    categoryId?: number
    abcClass?: string
    status?: number
    limit?: number
  }) {
    return request.get<BaseProduct[]>('/products', params)
  },
  detail(id: number) {
    return request.get<BaseProduct>(`/products/${id}`)
  },
  create(data: Partial<BaseProduct>) {
    return request.post<BaseProduct>('/products', data)
  },
  update(id: number, data: Partial<BaseProduct>) {
    return request.put<BaseProduct>(`/products/${id}`, data)
  },
  remove(id: number) {
    return request.delete<void>(`/products/${id}`)
  },
  /** 累加出库频次（出库确认时由后端自动调用，此处供手工校正） */
  increasePickFreq(id: number, delta: number) {
    return request.post<{ productId: number; delta: number; pickFreq: number }>(
      `/products/${id}/pick-freq`,
      null,
      { params: { delta } }
    )
  },
  /** 重新计算 ABC 分类 */
  classifyAbc() {
    return request.post<AbcClassifyResult>('/products/abc-classify')
  },
  /** ABC 分类分布统计 */
  abcSummary() {
    return request.get<Array<Record<string, unknown>>>('/products/abc-summary')
  }
}

/** 商品分类接口 */
export const categoryApi = {
  /** 分类树（含各级分类下的商品数量） */
  tree() {
    return request.get<CategoryTreeNode[]>('/categories/tree')
  },
  list() {
    return request.get<BaseCategory[]>('/categories')
  },
  create(data: Partial<BaseCategory>) {
    return request.post<BaseCategory>('/categories', data)
  },
  update(id: number, data: Partial<BaseCategory>) {
    return request.put<BaseCategory>(`/categories/${id}`, data)
  },
  remove(id: number) {
    return request.delete<void>(`/categories/${id}`)
  }
}
