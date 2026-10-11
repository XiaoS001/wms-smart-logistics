<script setup lang="ts">
/**
 * 用户管理。
 *
 * 后端当前开放两个只读接口（用户列表、用户详情），因此本页面以查看为主，
 * 并在界面上明确标注"新增/编辑待后端接口就绪"，
 * 避免给出可点击但无法生效的按钮误导使用者。
 *
 * 页面同时展示角色与权限的对应关系，便于核对权限设计的正确性
 * （管理员 17 项、仓管员 11 项、拣货员 6 项）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { ENABLE_STATUS } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'
import type { SysUser } from '@/types/auth'

const loading = ref(false)
const users = ref<SysUser[]>([])
const keyword = ref('')

/** 详情抽屉 */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SysUser | null>(null)

/** 角色说明：与后端初始化脚本中的权限分配保持一致 */
const roleMatrix = [
  { code: 'ADMIN', name: '系统管理员', count: 17, desc: '全部权限，含系统管理与算法调优' },
  { code: 'KEEPER', name: '仓管员', count: 11, desc: '基础数据增改、出入库作业与库存管理' },
  { code: 'PICKER', name: '拣货员', count: 6, desc: '拣货相关的查看与路径规划' }
]

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return users.value
  return users.value.filter(
    (u) =>
      u.username.toLowerCase().includes(kw) ||
      (u.realName ?? '').toLowerCase().includes(kw) ||
      (u.phone ?? '').includes(kw)
  )
})

/** 统计概览 */
const overview = computed(() => ({
  total: users.value.length,
  enabled: users.value.filter((u) => u.status === 1).length,
  disabled: users.value.filter((u) => u.status !== 1).length
}))

async function load() {
  loading.value = true
  try {
    users.value = await userApi.list()
  } catch {
    users.value = []
  } finally {
    loading.value = false
  }
}

async function openDetail(row: SysUser) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await userApi.detail(row.id)
  } catch {
    detail.value = row
  } finally {
    detailLoading.value = false
  }
}

/** 未开放的功能给出明确提示，而不是静默无响应 */
function notImplemented(action: string) {
  ElMessage.info(`${action}功能需要后端提供对应的写接口，当前后端仅开放用户查询接口。`)
}

onMounted(load)
</script>

<template>
  <div class="page-root user-page">
    <!-- 概览与工具条 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <ElInput
          v-model="keyword"
          placeholder="搜索登录名、姓名或手机号"
          clearable
          style="width: 260px"
          :prefix-icon="'Search'"
        />
        <ElButton :icon="'Refresh'" :loading="loading" @click="load">刷新</ElButton>

        <div class="wms-toolbar__right">
          <ElTooltip content="后端暂未开放用户新增接口" placement="top">
            <span>
              <ElButton type="primary" :icon="'Plus'" disabled @click="notImplemented('新增用户')">
                新增用户
              </ElButton>
            </span>
          </ElTooltip>
        </div>
      </div>

      <div class="user-page__overview">
        <div class="user-page__overview-item">
          <span class="user-page__overview-label">用户总数</span>
          <span class="user-page__overview-value">{{ overview.total }}</span>
        </div>
        <div class="user-page__overview-item">
          <span class="user-page__overview-label">已启用</span>
          <span class="user-page__overview-value is-success">{{ overview.enabled }}</span>
        </div>
        <div class="user-page__overview-item">
          <span class="user-page__overview-label">已停用</span>
          <span class="user-page__overview-value is-muted">{{ overview.disabled }}</span>
        </div>
      </div>

      <ElAlert type="info" :closable="false" show-icon class="user-page__note">
        <template #title>当前仅提供查看功能</template>
        <div class="user-page__note-body">
          后端已实现用户查询接口（列表与详情），新增/编辑/删除需要对应的写接口，
          尚未开放，因此相关按钮不可点击。用户的口令在数据库中为加盐哈希存储，
          查询接口也不会返回口令字段。
        </div>
      </ElAlert>
    </div>

    <!-- 用户列表 -->
    <div class="wms-card">
      <div class="wms-card__title">
        用户列表
        <ElTag size="small" type="info" effect="plain">共 {{ filtered.length }} 条</ElTag>
      </div>

      <ElTable v-loading="loading" :data="filtered" stripe size="small">
        <ElTableColumn prop="id" label="ID" width="70" align="center" />
        <ElTableColumn prop="username" label="登录名" width="140">
          <template #default="{ row }">
            <span class="user-page__username">{{ row.username }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="realName" label="姓名" width="120">
          <template #default="{ row }">{{ row.realName || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="phone" label="手机号" width="140">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90" align="center">
          <template #default="{ row }">
            <ElTag :type="(ENABLE_STATUS[row.status]?.type as any) ?? 'info'" size="small">
              {{ ENABLE_STATUS[row.status]?.label ?? '未知' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="最后登录" width="160">
          <template #default="{ row }">
            <span class="user-page__time">
              {{ row.lastLoginTime ? formatDateTime(row.lastLoginTime) : '—' }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton link type="primary" size="small" @click="openDetail(row)">详情</ElButton>
          </template>
        </ElTableColumn>

        <template #empty>
          <EmptyState description="没有符合条件的用户" />
        </template>
      </ElTable>
    </div>

    <!-- 角色与权限对照 -->
    <div class="wms-card">
      <div class="wms-card__title">
        角色与权限对照
        <ElTooltip content="权限数量来自后端的角色权限分配，与登录接口返回的权限列表一致" placement="top">
          <ElIcon class="user-page__info"><QuestionFilled /></ElIcon>
        </ElTooltip>
      </div>

      <ElTable :data="roleMatrix" size="small" stripe>
        <ElTableColumn prop="code" label="角色编码" width="130">
          <template #default="{ row }">
            <ElTag size="small" effect="plain" type="primary">{{ row.code }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="name" label="角色名称" width="140" />
        <ElTableColumn label="权限数量" width="120" align="right">
          <template #default="{ row }">
            <span class="user-page__count">{{ row.count }}</span>
            <span class="user-page__count-unit">项</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="desc" label="权限范围说明" min-width="260" />
      </ElTable>

      <div class="user-page__role-note">
        权限采用「用户 — 角色 — 权限」三级模型：登录成功后后端返回该用户的权限编码列表，
        前端据此控制菜单显隐与按钮可用性；接口侧同样按权限编码校验，二者互为补充。
      </div>
    </div>

    <!-- 用户详情 -->
    <ElDrawer v-model="detailVisible" title="用户详情" size="520px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <ElDescriptions :column="1" border size="small">
            <ElDescriptionsItem label="用户 ID">{{ detail.id }}</ElDescriptionsItem>
            <ElDescriptionsItem label="登录名">
              <span class="user-page__username">{{ detail.username }}</span>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="姓名">{{ detail.realName || '—' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="手机号">{{ detail.phone || '—' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="邮箱">{{ detail.email || '—' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="状态">
              <ElTag :type="(ENABLE_STATUS[detail.status]?.type as any) ?? 'info'" size="small">
                {{ ENABLE_STATUS[detail.status]?.label ?? '未知' }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="最后登录">
              {{ detail.lastLoginTime ? formatDateTime(detail.lastLoginTime) : '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="创建时间">
              {{ detail.createTime ? formatDateTime(detail.createTime) : '—' }}
            </ElDescriptionsItem>
          </ElDescriptions>

          <div class="user-page__detail-note">
            <ElIcon><Lock /></ElIcon>
            <span>
              为保护账号安全，接口不会返回口令字段；数据库中口令以加盐迭代哈希（PBKDF2）存储，
              无法从数据反推原始口令。
            </span>
          </div>
        </template>

        <EmptyState v-else-if="!detailLoading" description="未获取到用户详情" />
      </div>

      <template #footer>
        <ElButton @click="detailVisible = false">关闭</ElButton>
      </template>
    </ElDrawer>
  </div>
</template>

<style scoped lang="scss">
.user-page {
  &__overview {
    display: flex;
    gap: 34px;
    padding: 4px 0 14px;
  }

  &__overview-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__overview-label {
    font-size: 12px;
    color: #909399;
  }

  &__overview-value {
    font-size: 24px;
    font-weight: 700;
    color: #1f2937;

    &.is-success {
      color: #10b981;
    }

    &.is-muted {
      color: #909399;
    }
  }

  &__note {
    margin-top: 2px;
  }

  &__note-body {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.75;
  }

  &__username {
    font-family: 'Consolas', monospace;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__time {
    font-size: 12px;
    color: #909399;
  }

  &__count {
    font-weight: 600;
    color: var(--el-color-primary);
    font-size: 15px;
  }

  &__count-unit {
    margin-left: 2px;
    font-size: 11px;
    color: #909399;
  }

  &__info {
    margin-left: 4px;
    color: #c0c4cc;
    cursor: help;
    font-size: 13px;
  }

  &__role-note {
    margin-top: 12px;
    padding: 9px 12px;
    border-radius: 6px;
    background: #f8fafc;
    font-size: 12px;
    line-height: 1.75;
    color: #606266;
  }

  &__detail-note {
    display: flex;
    align-items: flex-start;
    gap: 7px;
    margin-top: 14px;
    padding: 10px 12px;
    border-radius: 6px;
    background: #f0fdf4;
    font-size: 12px;
    line-height: 1.75;
    color: #475569;

    .el-icon {
      margin-top: 2px;
      color: #10b981;
      flex-shrink: 0;
    }
  }
}
</style>
