import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiResult } from '@/types/common'

/**
 * HTTP 请求封装。
 *
 * 设计要点（对应后端《系统接口文档》的约定）：
 * 1. 请求拦截：自动注入 Authorization 令牌；
 * 2. 响应拦截：统一解包 Result，业务失败时弹出提示；
 * 3. 401 处理：清除登录态并跳转登录页（同一时刻只弹一次，避免并发请求弹多个框）；
 * 4. 错误提示去重：短时间内相同提示只显示一次，避免批量请求刷屏。
 */

/** 令牌在本地存储中的键名，与 user store 保持一致 */
export const TOKEN_KEY = 'wms_token'

/** 错误提示去重：记录最近提示过的内容与时间 */
const recentMessages = new Map<string, number>()
const MESSAGE_DEDUPE_MS = 1500

function showMessageOnce(message: string, type: 'error' | 'warning' | 'success' = 'error') {
  const now = Date.now()
  const last = recentMessages.get(message)
  if (last && now - last < MESSAGE_DEDUPE_MS) {
    return
  }
  recentMessages.set(message, now)
  // 清理过期记录，避免 Map 无限增长
  if (recentMessages.size > 50) {
    for (const [key, time] of recentMessages) {
      if (now - time > MESSAGE_DEDUPE_MS * 10) {
        recentMessages.delete(key)
      }
    }
  }
  ElMessage({ message, type, duration: 3000, showClose: true })
}

/** 是否正在处理登录过期，避免并发请求重复弹窗与跳转 */
let handlingUnauthorized = false

function handleUnauthorized(message: string) {
  if (handlingUnauthorized) {
    return
  }
  handlingUnauthorized = true
  localStorage.removeItem(TOKEN_KEY)
  ElMessageBox.confirm(message || '登录状态已失效，请重新登录。', '提示', {
    confirmButtonText: '重新登录',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      // 用整页跳转而非路由跳转：确保残留状态被彻底清空
      window.location.href = `/login?redirect=${encodeURIComponent(
        window.location.pathname + window.location.search
      )}`
    })
    .catch(() => {
      /* 用户取消时留在当前页 */
    })
    .finally(() => {
      handlingUnauthorized = false
    })
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' }
})

/* ------------------------------ 请求拦截 ------------------------------ */
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/* ------------------------------ 响应拦截 ------------------------------ */
service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const body = response.data

    // 非 JSON 响应（如文件下载）直接返回
    if (!body || typeof body.code === 'undefined') {
      return response
    }

    if (body.code === 200) {
      return response
    }

    // 业务失败：401 走登录失效流程，其余弹出提示
    if (body.code === 401) {
      handleUnauthorized(body.message)
    } else {
      showMessageOnce(body.message || '请求失败')
    }
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    // 网络层错误：区分超时、断网、服务未启动
    let message = '网络异常，请稍后重试'
    const status = error?.response?.status
    const serverMessage = error?.response?.data?.message

    if (error.code === 'ECONNABORTED' || String(error.message).includes('timeout')) {
      message = '请求超时，请检查网络或稍后重试'
    } else if (status === 401) {
      handleUnauthorized(serverMessage)
      return Promise.reject(error)
    } else if (status === 403) {
      message = serverMessage || '没有权限执行该操作'
    } else if (status === 404) {
      message = serverMessage || '请求的资源不存在'
    } else if (status === 405) {
      message = serverMessage || '请求方法不被支持'
    } else if (status === 409) {
      message = serverMessage || '操作与当前数据状态冲突'
    } else if (status === 415) {
      message = serverMessage || '请求内容类型不被支持'
    } else if (status && status >= 500) {
      message = serverMessage || '服务器内部错误，请联系管理员'
    } else if (serverMessage) {
      message = serverMessage
    } else if (!error.response) {
      message = '无法连接服务器，请确认后端服务已启动'
    }

    showMessageOnce(message)
    return Promise.reject(error)
  }
)

/* ------------------------------ 对外方法 ------------------------------ */

/**
 * 统一请求方法：直接返回业务数据 data，调用方无需再解包。
 *
 * @example
 *   const list = await request.get<BaseWarehouse[]>('/warehouses')
 */
export const request = {
  async get<T = unknown>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig) {
    const res = await service.get<ApiResult<T>>(url, { params, ...config })
    return res.data.data
  },

  async post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    const res = await service.post<ApiResult<T>>(url, data, config)
    return res.data.data
  },

  async put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    const res = await service.put<ApiResult<T>>(url, data, config)
    return res.data.data
  },

  async delete<T = unknown>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig) {
    const res = await service.delete<ApiResult<T>>(url, { params, ...config })
    return res.data.data
  },

  /** 需要同时拿到 message 时使用（如需要展示后端返回的成功提示） */
  async postWithMessage<T = unknown>(url: string, data?: unknown) {
    const res = await service.post<ApiResult<T>>(url, data)
    return { data: res.data.data, message: res.data.message }
  }
}

export default service
