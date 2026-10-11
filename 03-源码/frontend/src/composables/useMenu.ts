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
 * 1. meta.hidden 为真的路由不进入菜单（如登录页、错误页）；
 * 2. meta.layout 为真的路由是布局容器，本身不出现在菜单中，
 *    但它的子项要提升到上一级（根路由 "/" 就属于这种，若直接隐藏
 *    会连同「首页概览」一起消失）；
 * 3. meta.perm 存在时要求用户具备该权限；
 * 4. 父级若所有子项都不可见，则父级整项隐藏。
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

    // 布局容器：不出现在菜单里，由调用方把子项提升到上一层
    if (meta.layout) {
      if (children.length === 0) return null
      if (children.length === 1) return children[0]
      // 理论上不会出现多个子项，为保证结构完整用一个分组承载
      return {
        key: (route.name as string) || route.path,
        path: route.path,
        title: (meta.title as string) || route.path,
        icon: meta.icon as string | undefined,
        children
      }
    }

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

    // 原本有子路由，但过滤后一个都不剩：说明该分组下没有任何有权限的页面，
    // 此时必须隐藏分组本身。否则会出现空的「统计报表」「系统管理」标题，
    // 点开里面什么都没有（例如拣货员登录时）。
    const declaredChildren = (route.children || []).length
    if (declaredChildren > 0) return null

    const perm = meta.perm as string | undefined
    if (!userStore.hasPermission(perm)) return null

    return {
      key: (route.name as string) || route.path,
      path: route.path,
      title: (meta.title as string) || route.path,
      icon: meta.icon as string | undefined
    }
  }

  /** 过滤后的菜单树 */
  const menus = computed<MenuItem[]>(() => {
    const result: MenuItem[] = []
    for (const route of router.options.routes || []) {
      const raw = route as RouteRecordRaw
      const meta = (raw.meta || {}) as Record<string, unknown>
      if (meta.hidden) continue

      // 布局容器本身不显示，把它的子项直接提升到顶层
      if (meta.layout) {
        for (const child of raw.children || []) {
          const item = toMenuItem(child as RouteRecordRaw)
          if (item) result.push(item)
        }
        continue
      }

      const item = toMenuItem(raw)
      if (item) result.push(item)
    }
    return result
  })

  /**
   * 当前用户第一个可访问的页面路径。
   *
   * 用途：登录后与访问根路径时决定落在哪个页面。
   * 不能直接写死 /dashboard——该页需要 report:view 权限，
   * 拣货员没有此权限，登录后会直接看到 403 页面，像是系统故障。
   * 这里改为按菜单顺序找到第一个有权限进入的页面。
   */
  const firstAccessiblePath = computed<string | null>(() => {
    const findLeaf = (items: MenuItem[]): string | null => {
      for (const item of items) {
        if (item.children && item.children.length) {
          const inner = findLeaf(item.children)
          if (inner) return inner
          continue
        }
        const resolved = router.resolve({ name: item.key })
        if (resolved.matched.length > 0) return resolved.path
      }
      return null
    }
    return findLeaf(menus.value)
  })

  return { menus, firstAccessiblePath }
}
