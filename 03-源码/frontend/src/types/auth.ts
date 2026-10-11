/**
 * 认证与用户相关类型。
 */

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应 */
export interface LoginResponse {
  token: string
  userId: number
  username: string
  realName: string
  roles: string[]
  permissions: string[]
}

/** 当前登录用户信息（GET /api/auth/me） */
export interface UserProfile {
  userId: number
  username: string
  realName: string
  roles: string[]
  permissions: string[]
}

/** 用户列表项 */
export interface SysUser {
  id: number
  username: string
  realName: string
  phone?: string
  email?: string
  status: number
  lastLoginTime?: string
  createTime?: string
}

/** 角色 */
export interface SysRole {
  id: number
  roleCode: string
  roleName: string
  remark?: string
}

/** 权限项 */
export interface SysPermission {
  id: number
  parentId: number
  permCode: string
  permName: string
  permType: number
  path?: string
  sort: number
}
