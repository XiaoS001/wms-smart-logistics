import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'
import { PERM } from '@/utils/constants'

NProgress.configure({ showSpinner: false, minimum: 0.15, speed: 400 })

/**
 * 路由表。
 *
 * 约定：
 * 1. meta.title 用于面包屑与浏览器标题；
 * 2. meta.icon 为菜单图标名（Element Plus 图标）；
 * 3. meta.perm 为访问所需权限编码，未配置表示登录即可访问；
 * 4. meta.hidden 为真时不在侧边菜单中显示（如详情页）；
 * 5. meta.activeMenu 指定高亮的父级菜单。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页概览', icon: 'HomeFilled', perm: PERM.REPORT_VIEW }
      }
    ]
  },
  {
    path: '/base',
    component: () => import('@/layout/index.vue'),
    redirect: '/base/warehouse',
    meta: { title: '基础数据', icon: 'Coin' },
    children: [
      {
        path: 'warehouse',
        name: 'Warehouse',
        component: () => import('@/views/base/warehouse/index.vue'),
        meta: { title: '仓库管理', icon: 'OfficeBuilding', perm: PERM.WAREHOUSE_VIEW }
      },
      {
        path: 'location',
        name: 'Location',
        component: () => import('@/views/base/location/index.vue'),
        meta: { title: '库位管理', icon: 'Grid', perm: PERM.LOCATION_VIEW }
      },
      {
        path: 'product',
        name: 'Product',
        component: () => import('@/views/base/product/index.vue'),
        meta: { title: '商品管理', icon: 'Goods', perm: PERM.PRODUCT_VIEW }
      },
      {
        path: 'category',
        name: 'Category',
        component: () => import('@/views/base/category/index.vue'),
        meta: { title: '商品分类', icon: 'Files', perm: PERM.PRODUCT_VIEW }
      }
    ]
  },
  {
    path: '/biz',
    component: () => import('@/layout/index.vue'),
    redirect: '/biz/inbound',
    meta: { title: '业务作业', icon: 'Van' },
    children: [
      {
        path: 'inbound',
        name: 'Inbound',
        component: () => import('@/views/inbound/index.vue'),
        meta: { title: '入库管理', icon: 'Download', perm: PERM.INBOUND_VIEW }
      },
      {
        path: 'outbound',
        name: 'Outbound',
        component: () => import('@/views/outbound/index.vue'),
        meta: { title: '出库管理', icon: 'Upload', perm: PERM.OUTBOUND_VIEW }
      },
      {
        path: 'stock',
        name: 'Stock',
        component: () => import('@/views/stock/index.vue'),
        meta: { title: '库存管理', icon: 'Box', perm: PERM.STOCK_VIEW }
      },
      {
        path: 'stocktake',
        name: 'Stocktake',
        component: () => import('@/views/stock/stocktake.vue'),
        meta: { title: '库存盘点', icon: 'DocumentChecked', perm: PERM.STOCK_VIEW }
      },
      {
        path: 'picking',
        name: 'Picking',
        component: () => import('@/views/picking/index.vue'),
        meta: { title: '拣货路径规划', icon: 'Guide', perm: PERM.PICKING_VIEW }
      }
    ]
  },
  {
    path: '/algo',
    component: () => import('@/layout/index.vue'),
    redirect: '/algo/slotting',
    meta: { title: '智能优化', icon: 'MagicStick' },
    children: [
      {
        path: 'slotting',
        name: 'Slotting',
        component: () => import('@/views/slotting/index.vue'),
        meta: { title: '货位优化', icon: 'Aim', perm: PERM.SLOTTING_VIEW }
      },
      {
        path: 'slotting-plan',
        name: 'SlottingPlan',
        component: () => import('@/views/slotting/plan.vue'),
        meta: { title: '优化方案', icon: 'List', perm: PERM.SLOTTING_VIEW }
      }
    ]
  },
  {
    path: '/report',
    component: () => import('@/layout/index.vue'),
    redirect: '/report/algorithm',
    meta: { title: '统计报表', icon: 'DataAnalysis' },
    children: [
      {
        path: 'algorithm',
        name: 'ReportAlgorithm',
        component: () => import('@/views/report/algorithm.vue'),
        meta: { title: '算法效果', icon: 'TrendCharts', perm: PERM.REPORT_VIEW }
      },
      {
        path: 'stock',
        name: 'ReportStock',
        component: () => import('@/views/report/stock.vue'),
        meta: { title: '库存与作业', icon: 'Histogram', perm: PERM.REPORT_VIEW }
      }
    ]
  },
  {
    path: '/system',
    component: () => import('@/layout/index.vue'),
    redirect: '/system/user',
    meta: { title: '系统管理', icon: 'Setting' },
    children: [
      {
        path: 'user',
        name: 'SysUser',
        component: () => import('@/views/system/user.vue'),
        meta: { title: '用户管理', icon: 'User', perm: PERM.SYSTEM_VIEW }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/system/profile.vue'),
        meta: { title: '个人信息', icon: 'UserFilled', hidden: true }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权访问', hidden: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', hidden: true }
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_PUBLIC_PATH || '/'),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/** 免登录白名单 */
const WHITE_LIST = ['/login', '/403', '/404']

router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  document.title = to.meta?.title
    ? `${to.meta.title} - ${import.meta.env.VITE_APP_TITLE || '智慧仓储管理系统'}`
    : import.meta.env.VITE_APP_TITLE || '智慧仓储管理系统'

  const userStore = useUserStore()

  // 未登录：放行白名单，其余跳转登录页并记录来源
  if (!userStore.isLogin) {
    if (WHITE_LIST.includes(to.path)) {
      next()
    } else {
      next({ path: '/login', query: { redirect: to.fullPath } })
    }
    return
  }

  // 已登录但访问登录页：直接回首页
  if (to.path === '/login') {
    next({ path: '/' })
    return
  }

  // 首次进入或刷新后拉取用户信息，确保权限列表可用
  if (!userStore.profileLoaded) {
    try {
      await userStore.loadProfile(true)
    } catch {
      // 拉取失败（如令牌失效）由请求拦截器负责跳转，这里只中断本次导航
      userStore.reset()
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }

  // 权限校验：无权限跳 403
  const perm = to.meta?.perm as string | undefined
  if (perm && !userStore.hasPermission(perm)) {
    next({ path: '/403' })
    return
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

router.onError(() => {
  NProgress.done()
})

export default router
