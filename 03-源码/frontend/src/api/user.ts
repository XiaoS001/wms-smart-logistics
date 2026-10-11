import { request } from '@/utils/request'
import type { SysUser } from '@/types/auth'

/**
 * 用户管理接口。
 *
 * 后端当前仅开放查询能力：
 *   GET /api/users          用户列表
 *   GET /api/users/{id}     用户详情
 * 新增/编辑/删除需要对应的写接口，尚未实现，因此此处不提供相应方法，
 * 页面也不会给出可点击但无效的按钮。
 */
export const userApi = {
  /** 用户列表（响应中不含口令字段） */
  list() {
    return request.get<SysUser[]>('/users')
  },

  /** 用户详情 */
  detail(id: number) {
    return request.get<SysUser>(`/users/${id}`)
  }
}
