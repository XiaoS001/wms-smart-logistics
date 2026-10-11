import { request } from '@/utils/request'
import type { LoginRequest, LoginResponse, SysUser, UserProfile } from '@/types/auth'

/** 认证与用户管理接口 */
export const authApi = {
  /** 登录：成功返回令牌、角色与权限编码列表 */
  login(data: LoginRequest) {
    return request.post<LoginResponse>('/auth/login', data)
  },

  /** 查询当前登录用户信息 */
  currentUser() {
    return request.get<UserProfile>('/auth/me')
  },

  /** 退出登录（服务端无状态，主要用于记录日志） */
  logout() {
    return request.post<void>('/auth/logout')
  }
}

/** 用户管理接口 */
export const userApi = {
  list() {
    return request.get<SysUser[]>('/users')
  },
  detail(id: number) {
    return request.get<SysUser>(`/users/${id}`)
  }
}
