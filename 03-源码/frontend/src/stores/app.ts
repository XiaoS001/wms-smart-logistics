import { defineStore } from 'pinia'
import { ref } from 'vue'
import { DEFAULT_WAREHOUSE_ID } from '@/utils/constants'

/**
 * 应用级状态：侧边栏折叠、当前仓库、页面标题等。
 *
 * 当前系统为单仓库场景，仍保留“当前仓库”概念，
 * 便于后续扩展为多仓库时页面无需大改。
 */
export const useAppStore = defineStore(
  'app',
  () => {
    /** 侧边栏是否折叠 */
    const sidebarCollapsed = ref(false)
    /** 当前操作的仓库 ID */
    const currentWarehouseId = ref<number>(DEFAULT_WAREHOUSE_ID)
    /** 当前操作的仓库名称，仅用于界面展示 */
    const currentWarehouseName = ref('智慧物流示范仓')
    /** 是否开启暗色模式（预留） */
    const dark = ref(false)

    function toggleSidebar() {
      sidebarCollapsed.value = !sidebarCollapsed.value
    }

    function setSidebarCollapsed(value: boolean) {
      sidebarCollapsed.value = value
    }

    function setCurrentWarehouse(id: number, name?: string) {
      currentWarehouseId.value = id
      if (name) {
        currentWarehouseName.value = name
      }
    }

    function toggleDark() {
      dark.value = !dark.value
      document.documentElement.classList.toggle('dark', dark.value)
    }

    return {
      sidebarCollapsed,
      currentWarehouseId,
      currentWarehouseName,
      dark,
      toggleSidebar,
      setSidebarCollapsed,
      setCurrentWarehouse,
      toggleDark
    }
  },
  {
    persist: {
      key: 'wms_app',
      // pinia-plugin-persistedstate v3 使用 paths（v4 才改名为 pick）
      paths: ['sidebarCollapsed', 'currentWarehouseId', 'currentWarehouseName', 'dark']
    }
  }
)
