<script setup lang="ts">
/**
 * 多标签页导航。
 *
 * 参考国内主流管理后台的交互习惯：
 * - 访问过的页面自动加入标签栏，支持快速切换；
 * - 关闭当前标签后自动跳到相邻标签，不会停留在空白页；
 * - 提供“关闭其他 / 关闭全部”快捷操作；
 * - 标签状态用 sessionStorage 保存，刷新页面后不丢失。
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

interface TabItem {
  name: string
  title: string
  path: string
  closable: boolean
}

const route = useRoute()
const router = useRouter()

const STORAGE_KEY = 'wms_tabs'

/** 首页标签不可关闭 */
const HOME_TAB: TabItem = {
  name: 'Dashboard',
  title: '首页概览',
  path: '/dashboard',
  closable: false
}

function loadTabs(): TabItem[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as TabItem[]
      if (Array.isArray(parsed) && parsed.length) return parsed
    }
  } catch {
    /* 解析失败时回退到默认标签 */
  }
  return [{ ...HOME_TAB }]
}

const tabs = ref<TabItem[]>(loadTabs())

function persist() {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tabs.value))
}

/** 当前激活的标签 */
const activeTab = computed(() => (route.name as string) || route.path)

/** 路由变化时把页面加入标签栏 */
watch(
  () => route.fullPath,
  () => {
    const name = route.name as string
    const meta = (route.meta || {}) as Record<string, unknown>
    if (!name || meta.hidden || meta.noTab) return

    const exists = tabs.value.some((t) => t.name === name)
    if (!exists) {
      tabs.value.push({
        name,
        title: (meta.title as string) || name,
        path: route.fullPath,
        closable: name !== HOME_TAB.name
      })
      persist()
    } else {
      // 同步路径，保证同一页面带不同参数时能正确跳回
      const tab = tabs.value.find((t) => t.name === name)
      if (tab) tab.path = route.fullPath
      persist()
    }
  },
  { immediate: true }
)

/** 关闭指定标签 */
function closeTab(tab: TabItem) {
  const index = tabs.value.findIndex((t) => t.name === tab.name)
  if (index < 0) return
  tabs.value.splice(index, 1)
  persist()

  // 关闭的是当前页：跳到相邻标签
  if (tab.name === activeTab.value) {
    const next = tabs.value[index] || tabs.value[index - 1] || tabs.value[0]
    if (next) router.push(next.path)
  }
}

/** 关闭其他标签 */
function closeOthers() {
  tabs.value = tabs.value.filter((t) => t.name === activeTab.value || !t.closable)
  persist()
}

/** 关闭全部（保留首页） */
function closeAll() {
  tabs.value = tabs.value.filter((t) => !t.closable)
  persist()
  router.push(HOME_TAB.path)
}

function handleCommand(command: string) {
  if (command === 'others') closeOthers()
  if (command === 'all') closeAll()
}

function goTo(tab: TabItem) {
  if (tab.name !== activeTab.value) router.push(tab.path)
}
</script>

<template>
  <div class="app-tabs">
    <ElScrollbar class="app-tabs__scroll" :horizontal="true">
      <div class="app-tabs__list">
        <div
          v-for="tab in tabs"
          :key="tab.name"
          class="app-tabs__item"
          :class="{ 'is-active': tab.name === activeTab }"
          role="button"
          tabindex="0"
          @click="goTo(tab)"
          @keydown.enter="goTo(tab)"
        >
          <span class="app-tabs__dot" />
          <span class="app-tabs__text">{{ tab.title }}</span>
          <ElIcon
            v-if="tab.closable"
            class="app-tabs__close"
            @click.stop="closeTab(tab)"
          >
            <Close />
          </ElIcon>
        </div>
      </div>
    </ElScrollbar>

    <ElDropdown class="app-tabs__actions" trigger="click" @command="handleCommand">
      <button class="app-tabs__actions-btn" type="button">
        <ElIcon :size="14"><MoreFilled /></ElIcon>
      </button>
      <template #dropdown>
        <ElDropdownMenu>
          <ElDropdownItem command="others">关闭其他标签</ElDropdownItem>
          <ElDropdownItem command="all">关闭全部标签</ElDropdownItem>
        </ElDropdownMenu>
      </template>
    </ElDropdown>
  </div>
</template>

<style scoped lang="scss">
.app-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  height: var(--wms-tabbar-height);
  padding: 0 6px 0 10px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;

  &__scroll {
    flex: 1;
    min-width: 0;
  }

  &__list {
    display: flex;
    align-items: center;
    gap: 6px;
    height: var(--wms-tabbar-height);
    white-space: nowrap;
  }

  &__item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 28px;
    padding: 0 10px;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    background: #fff;
    color: #606266;
    font-size: 12px;
    cursor: pointer;
    user-select: none;
    transition: all 0.2s;

    &:hover {
      color: var(--el-color-primary);
      border-color: var(--el-color-primary-light-5);
    }

    &.is-active {
      color: #fff;
      background: var(--el-color-primary);
      border-color: var(--el-color-primary);

      .app-tabs__dot {
        background: #fff;
      }

      .app-tabs__close:hover {
        background: rgb(255 255 255 / 25%);
      }
    }
  }

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #c0c4cc;
    flex-shrink: 0;
  }

  &__close {
    font-size: 11px;
    border-radius: 50%;
    padding: 1px;
    transition: background-color 0.15s;

    &:hover {
      background: rgb(0 0 0 / 12%);
    }
  }

  &__actions {
    flex-shrink: 0;
  }

  &__actions-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    background: #fff;
    color: #606266;
    cursor: pointer;

    &:hover {
      color: var(--el-color-primary);
      border-color: var(--el-color-primary-light-5);
    }
  }
}
</style>
