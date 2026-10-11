import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'
import { TOKEN_KEY } from '@/utils/request'
import type { LoginRequest, UserProfile } from '@/types/auth'

/**
 * 用户状态：令牌、身份信息、角色与权限。
 *
 * 令牌单独存放于 localStorage 的固定键（与请求拦截器共用），
 * 其余信息由 pinia 持久化插件保存，刷新页面后仍保持登录态。
 */
export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
    const userId = ref<number | null>(null)
    const username = ref('')
    const realName = ref('')
    const roles = ref<string[]>([])
    const permissions = ref<string[]>([])
    /** 是否已拉取过用户信息，避免重复请求 */
    const profileLoaded = ref(false)

    const isLogin = computed(() => !!token.value)
    /** 管理员拥有全部权限 */
    const isAdmin = computed(() => roles.value.includes('ADMIN'))
    const displayName = computed(() => realName.value || username.value || '未登录')

    /** 判断是否具备某个权限编码 */
    function hasPermission(code?: string | string[]): boolean {
      if (!code) return true
      if (isAdmin.value) return true
      const codes = Array.isArray(code) ? code : [code]
      return codes.some((c) => permissions.value.includes(c))
    }

    function setToken(value: string) {
      token.value = value
      if (value) {
        localStorage.setItem(TOKEN_KEY, value)
      } else {
        localStorage.removeItem(TOKEN_KEY)
      }
    }

    /** 应用登录响应中的身份数据 */
    function setProfile(profile: UserProfile) {
      userId.value = profile.userId ?? null
      username.value = profile.username ?? ''
      realName.value = profile.realName ?? ''
      roles.value = profile.roles ?? []
      permissions.value = profile.permissions ?? []
      profileLoaded.value = true
    }

    /** 登录 */
    async function login(data: LoginRequest) {
      const res = await authApi.login(data)
      setToken(res.token)
      setProfile({
        userId: res.userId,
        username: res.username,
        realName: res.realName,
        roles: res.roles ?? [],
        permissions: res.permissions ?? []
      })
      return res
    }

    /** 拉取当前用户信息（刷新页面后调用） */
    async function loadProfile(force = false) {
      if (!token.value) return null
      if (profileLoaded.value && !force) return null
      const profile = await authApi.currentUser()
      setProfile(profile)
      return profile
    }

    /** 清空登录态 */
    function reset() {
      setToken('')
      userId.value = null
      username.value = ''
      realName.value = ''
      roles.value = []
      permissions.value = []
      profileLoaded.value = false
    }

    /** 退出登录 */
    async function logout() {
      try {
        await authApi.logout()
      } catch {
        // 服务端无状态，退出失败也要清理本地登录态
      } finally {
        reset()
      }
    }

    return {
      token,
      userId,
      username,
      realName,
      roles,
      permissions,
      profileLoaded,
      isLogin,
      isAdmin,
      displayName,
      hasPermission,
      setToken,
      setProfile,
      login,
      loadProfile,
      reset,
      logout
    }
  },
  {
    persist: {
      key: 'wms_user',
      // 令牌已单独存于 localStorage，此处持久化其余身份信息。
      // 注意：pinia-plugin-persistedstate v3 使用 paths（v4 才改名为 pick）。
      paths: ['userId', 'username', 'realName', 'roles', 'permissions', 'profileLoaded']
    }
  }
)
