<script setup lang="ts">
/**
 * 主布局：左侧菜单 + 顶部导航 + 多标签页 + 内容区。
 *
 * 采用经典管理后台结构；标签页支持关闭与刷新，
 * 关闭当前标签后自动跳转到相邻标签，避免停留在已关闭的页面。
 */
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'
import AppNavbar from './components/AppNavbar.vue'
import AppTabs from './components/AppTabs.vue'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()

/** 内容区是否需要缓存：meta.keepAlive 为真时保留组件状态 */
const cachedViews = computed(() =>
  route.matched.filter((r) => r.meta?.keepAlive).map((r) => r.name as string)
)

/** 页面切换时的过渡动画名 */
const transitionName = 'fade-slide'

// 路由切换时滚动内容区到顶部
watch(
  () => route.fullPath,
  () => {
    const el = document.querySelector('.app-main')
    if (el) el.scrollTop = 0
  }
)
</script>

<template>
  <div class="app-layout" :class="{ 'is-collapsed': appStore.sidebarCollapsed }">
    <AppSidebar class="app-sidebar" />

    <div class="app-body">
      <AppNavbar class="app-navbar" />
      <AppTabs class="app-tabs" />

      <main class="app-main">
        <RouterView v-slot="{ Component, route: current }">
          <Transition :name="transitionName" mode="out-in">
            <KeepAlive :include="cachedViews">
              <component :is="Component" :key="current.path" />
            </KeepAlive>
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-layout {
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.app-sidebar {
  flex: 0 0 var(--wms-sidebar-width);
  width: var(--wms-sidebar-width);
  transition: width 0.25s ease, flex-basis 0.25s ease;
  z-index: 1001;
}

.is-collapsed .app-sidebar {
  flex-basis: var(--wms-sidebar-collapsed-width);
  width: var(--wms-sidebar-collapsed-width);
}

.app-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.app-main {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background-color: var(--wms-page-bg);
}
</style>
