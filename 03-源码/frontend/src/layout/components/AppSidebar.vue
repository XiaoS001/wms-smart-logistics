<script setup lang="ts">
/**
 * 侧边栏：品牌区 + 权限过滤后的菜单。
 *
 * 菜单由路由表推导，权限过滤在 useMenu 中完成，
 * 因此新增页面只需在路由表中登记，无需修改菜单代码。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useMenu } from '@/composables/useMenu'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()
const { menus } = useMenu()

const collapsed = computed(() => appStore.sidebarCollapsed)

/** 当前高亮菜单：优先取 meta.activeMenu，其次取路由 name */
const activeMenu = computed(() => {
  const meta = route.meta as Record<string, unknown>
  return (meta.activeMenu as string) || (route.name as string) || route.path
})
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__brand">
      <div class="sidebar__logo">WMS</div>
      <Transition name="fade">
        <div v-show="!collapsed" class="sidebar__title">
          <div class="sidebar__title-main">智慧仓储</div>
          <div class="sidebar__title-sub">管理系统</div>
        </div>
      </Transition>
    </div>

    <ElScrollbar class="sidebar__scroll">
      <ElMenu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="transparent"
        text-color="var(--wms-sidebar-text)"
        active-text-color="var(--wms-sidebar-text-active)"
        unique-opened
        router
      >
        <template v-for="menu in menus" :key="menu.key">
          <!-- 有子菜单：渲染可展开分组 -->
          <ElSubMenu v-if="menu.children && menu.children.length" :index="menu.key">
            <template #title>
              <ElIcon v-if="menu.icon"><component :is="menu.icon" /></ElIcon>
              <span>{{ menu.title }}</span>
            </template>

            <ElMenuItem
              v-for="child in menu.children"
              :key="child.key"
              :index="child.key"
              :route="{ name: child.key }"
            >
              <ElIcon v-if="child.icon"><component :is="child.icon" /></ElIcon>
              <template #title>{{ child.title }}</template>
            </ElMenuItem>
          </ElSubMenu>

          <!-- 无子菜单：直接渲染单项 -->
          <ElMenuItem v-else :index="menu.key" :route="{ name: menu.key }">
            <ElIcon v-if="menu.icon"><component :is="menu.icon" /></ElIcon>
            <template #title>{{ menu.title }}</template>
          </ElMenuItem>
        </template>
      </ElMenu>
    </ElScrollbar>

    <div v-show="!collapsed" class="sidebar__footer">
      <span>面向智慧物流</span>
      <span class="sidebar__footer-ver">v1.0.0</span>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--wms-sidebar-bg);
  overflow: hidden;

  &__brand {
    display: flex;
    align-items: center;
    gap: 10px;
    height: var(--wms-header-height);
    padding: 0 14px;
    flex-shrink: 0;
    border-bottom: 1px solid rgb(255 255 255 / 8%);
  }

  &__logo {
    flex-shrink: 0;
    width: 34px;
    height: 34px;
    border-radius: 8px;
    background: linear-gradient(135deg, var(--el-color-primary), #60a5fa);
    color: #fff;
    font-size: 13px;
    font-weight: 700;
    letter-spacing: 0.5px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__title {
    overflow: hidden;
    white-space: nowrap;
  }

  &__title-main {
    color: #fff;
    font-size: 15px;
    font-weight: 600;
    line-height: 1.2;
  }

  &__title-sub {
    color: var(--wms-sidebar-text);
    font-size: 11px;
    margin-top: 1px;
    letter-spacing: 2px;
  }

  &__scroll {
    flex: 1;
    min-height: 0;
  }

  &__footer {
    flex-shrink: 0;
    padding: 10px 14px;
    border-top: 1px solid rgb(255 255 255 / 8%);
    color: #6b7280;
    font-size: 11px;
    display: flex;
    justify-content: space-between;
    white-space: nowrap;
  }

  &__footer-ver {
    color: #4b5563;
  }
}

:deep(.el-menu) {
  border-right: none;
  padding: 6px 0;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 42px;
  line-height: 42px;
  margin: 2px 8px;
  border-radius: 6px;

  &:hover {
    background-color: var(--wms-sidebar-bg-hover) !important;
  }
}

:deep(.el-menu-item.is-active) {
  background-color: var(--el-color-primary) !important;
  color: #fff !important;
  font-weight: 500;
}

:deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  padding-left: 42px !important;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
