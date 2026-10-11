/**
 * 全局通用类型定义。
 *
 * 命名约定：与后端 DTO 保持一致，使用小驼峰字段名，
 * 这样接口返回的 JSON 可以零转换直接绑定到类型上。
 */

/** 统一响应体，与后端 com.lijunjie.wms.common.Result 对应 */
export interface ApiResult<T = unknown> {
  /** 业务状态码，同时作为 HTTP 状态码返回 */
  code: number
  /** 可直接展示给用户的提示 */
  message: string
  /** 业务数据 */
  data: T
  /** 布尔快捷判断 */
  success: boolean
  /** 服务端时间戳 */
  timestamp: number
}

/** 分页查询参数 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

/** 下拉选项 */
export interface SelectOption {
  label: string
  value: string | number
  disabled?: boolean
}

/** 通用状态枚举值：与后端约定 1 启用 / 0 停用 */
export const STATUS_ENABLED = 1
export const STATUS_DISABLED = 0
