<script setup lang="ts">
/**
 * 顶部导航栏：折叠按钮 + 面包屑 + 仓库切换 + 后端连通状态 + 用户菜单。
 *
 * “后端连通状态”是答辩演示时很实用的细节：
 * 若后端未启动，页面会直接提示，而不是让操作静默失败。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppBreadcrumb from './AppBreadcrumb.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { healthApi } from '@/api/report'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

/** 后端连通状态：unknown 未检测 / online 正常 / offline 异常 */
const backendStatus = ref<'unknown' | 'online' | 'offline'>('unknown')
const checking = ref(false)

async function checkBackend() {
  checking.value = true
  try {
    await healthApi.ping()
    backendStatus.value = 'online'
  } catch {
    backendStatus.value = 'offline'
  } finally {
    checking.value = false
  }
}

onMounted(checkBackend)

/** 处理用户下拉菜单 */
async function handleCommand(command: string) {
  if (command === 'profile') {
    router.push('/system/profile')
  } else if (command === 'docs') {
    // 后端集成了 springdoc-openapi，可在新窗口打开在线接口文档
    window.open('http://127.0.0.1:8080/swagger-ui/index.html', '_blank')
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<template>
  <header class="navbar">
    <div class="navbar__left">
      <ElTooltip :content="appStore.sidebarCollapsed ? '展开菜单' : '收起菜单'" placement="bottom">
        <button class="navbar__icon-btn" type="button" @click="appStore.toggleSidebar()">
          <ElIcon :size="18">
            <component :is="appStore.sidebarCollapsed ? 'Expand' : 'Fold'" />
          </ElIcon>
        </button>
      </ElTooltip>

      <AppBreadcrumb class="navbar__breadcrumb" />
    </div>

    <div class="navbar__right">
      <!-- 当前仓库：单仓库场景下作为信息展示 -->
      <ElTag type="primary" effect="plain" size="small" class="navbar__warehouse">
        <ElIcon><OfficeBuilding /></ElIcon>
        <span>{{ appStore.currentWarehouseName }}</span>
      </ElTag>

      <!-- 后端连通状态 -->
      <ElTooltip placement="bottom" :content="'后端服务地址：http://127.0.0.1:8080'">
        <div
          class="navbar__status"
          :class="`is-${backendStatus}`"
          role="button"
          tabindex="0"
          @click="checkBackend"
          @keydown.enter="checkBackend"
        >
          <span class="navbar__status-dot" />
          <span class="navbar__status-text">
            {{
              checking
                ? '检测中'
                : backendStatus === 'online'
                  ? '服务正常'
                  : backendStatus === 'offline'
                    ? '服务未连接'
                    : '未检测'
            }}
          </span>
        </div>
      </ElTooltip>

      <ElDropdown trigger="click" @command="handleCommand">
        <div class="navbar__user" role="button" tabindex="0">
          <ElAvatar :size="30" class="navbar__avatar">
            {{ userStore.displayName.charAt(0) }}
          </ElAvatar>
          <div class="navbar__user-info">
            <div class="navbar__user-name">{{ userStore.displayName }}</div>
            <div class="navbar__user-role">
              {{ userStore.isAdmin ? '系统管理员' : (userStore.roles[0] || '普通用户') }}
            </div>
          </div>
          <ElIcon :size="12"><ArrowDown /></ElIcon>
        </div>

        <template #dropdown>
          <ElDropdownMenu>
            <ElDropdownItem command="profile">
              <ElIcon><User /></ElIcon>
              <span>个人信息</span>
            </ElDropdownItem>
            <ElDropdownItem command="docs">
              <ElIcon><Document /></ElIcon>
              <span>在线接口文档</span>
            </ElDropdownItem>
            <ElDropdownItem divided command="logout">
              <ElIcon><SwitchButton /></ElIcon>
              <span>退出登录</span>
            </ElDropdownItem>
          </ElDropdownMenu>
        </template>
      </ElDropdown>
    </div>
  </header>
</template>

<style scoped lang="scss">
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--wms-header-height);
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;

  &__left,
  &__right {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 0;
  }

  &__icon-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: #606266;
    cursor: pointer;
    transition: background-color 0.2s;

    &:hover {
      background-color: #f2f3f5;
      color: var(--el-color-primary);
    }
  }

  &__breadcrumb {
    min-width: 0;
  }

  &__warehouse {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  &__status {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    border-radius: 12px;
    background: #f9fafb;
    cursor: pointer;
    font-size: 12px;
    user-select: none;
    transition: background-color 0.2s;

    &:hover {
      background: #f2f3f5;
    }

    &.is-online {
      color: var(--el-color-success);
    }

    &.is-offline {
      color: var(--el-color-danger);
    }

    &.is-unknown {
      color: #909399;
    }
  }

  &__status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: currentcolor;
    box-shadow: 0 0 0 3px rgb(0 0 0 / 4%);
  }

  &__user {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 8px;
    border-radius: 6px;
    cursor: pointer;
    outline: none;
    transition: background-color 0.2s;

    &:hover {
      background-color: #f2f3f5;
    }
  }

  &__avatar {
    background: var(--el-color-primary);
    color: #fff;
    font-size: 13px;
    flex-shrink: 0;
  }

  &__user-info {
    line-height: 1.25;
  }

  &__user-name {
    font-size: 13px;
    color: #303133;
    font-weight: 500;
  }

  &__user-role {
    font-size: 11px;
    color: #909399;
  }
}

@media (max-width: 900px) {
  .navbar__breadcrumb,
  .navbar__warehouse,
  .navbar__user-info {
    display: none;
  }
}
</style>
