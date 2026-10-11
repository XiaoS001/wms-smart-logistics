import { computed } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

/** 菜单项：由路由表推导而来 */
export interface MenuItem {
  /** 菜单唯一标识：优先取路由 name，其次取 path */
  key: string
  /** 完整路径（可作为点击跳转目标） */
  path: string
  title: string
  icon?: string
  children?: MenuItem[]
}

/**
 * 菜单权限过滤。
 *
 * 规则：
 * 1. meta.hidden 为真的路由不进入菜单（如详情页、错误页）；
 * 2. meta.perm 存在时要求用户具备该权限；
 * 3. 父级若所有子项都无权限，则父级整项隐藏；
 * 4. 父级自身路径默认重定向到第一个有权限的子项。
 */
export function useMenu() {
  const router = useRouter()
  const userStore = useUserStore()

  /** 把单条路由转换为菜单项（递归处理子级） */
  function toMenuItem(route: RouteRecordRaw): MenuItem | null {
    const meta = (route.meta || {}) as Record<string, unknown>
    if (meta.hidden) return null

    const children = (route.children || [])
      .map((child) => toMenuItem(child as RouteRecordRaw))
      .filter((c): c is MenuItem => c !== null)

    // 父级无子项但有权限：保留自身
    const perm = meta.perm as string | undefined
    const hasPerm = userStore.hasPermission(perm)

    if (children.length > 0) {
      // 父级仅作为分组容器：本身不做权限判断，交给子项
      return {
        key: (route.name as string) || route.path,
        path: route.path,
        title: (meta.title as string) || route.path,
        icon: meta.icon as string | undefined,
        children
      }
    }

    if (!hasPerm) return null

    return {
      key: (route.name as string) || route.path,
      path: route.path,
      title: (meta.title as string) || route.path,
      icon: meta.icon as string | undefined
    }
  }

  /** 过滤后的菜单树 */
  const menus = computed<MenuItem[]>(() => {
    return (router.options.routes || [])
      .map((route) => toMenuItem(route as RouteRecordRaw))
      .filter((m): m is MenuItem => m !== null)
  })

  return { menus }
}
