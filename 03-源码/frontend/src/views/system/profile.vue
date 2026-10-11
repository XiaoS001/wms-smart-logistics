<script setup lang="ts">
/**
 * 个人信息。
 *
 * 展示当前登录者的身份、角色与其实际拥有的权限清单。
 * 把权限逐项列出来是刻意的设计：答辩演示时可以直接对照说明
 * "不同角色登录后能看到的菜单与按钮为何不同"。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { UserProfile } from '@/types/auth'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const profile = ref<UserProfile | null>(null)

/** 权限编码 → 可读名称（与后端初始化脚本中的权限点一致） */
const PERM_NAMES: Record<string, string> = {
  'system:view': '系统管理查看',
  'warehouse:view': '仓库管理查看',
  'location:view': '库位管理查看',
  'product:view': '商品管理查看',
  'product:add': '新增商品',
  'product:edit': '编辑商品',
  'product:delete': '删除商品',
  'inbound:view': '入库管理查看',
  'inbound:add': '新建入库单',
  'outbound:view': '出库管理查看',
  'outbound:add': '新建出库单',
  'stock:view': '库存管理查看',
  'slotting:view': '货位优化查看',
  'slotting:run': '执行货位优化',
  'picking:view': '拣货路径查看',
  'picking:run': '执行路径规划',
  'report:view': '报表统计查看'
}

/** 按模块分组展示权限，便于阅读 */
const groupedPermissions = computed(() => {
  const list = profile.value?.permissions ?? []
  const groups = new Map<string, Array<{ code: string; name: string }>>()
  const moduleOf = (code: string) => {
    const prefix = code.split(':')[0]
    const map: Record<string, string> = {
      system: '系统管理',
      warehouse: '仓库与库位',
      location: '仓库与库位',
      product: '商品管理',
      inbound: '入库作业',
      outbound: '出库作业',
      stock: '库存管理',
      slotting: '货位优化',
      picking: '拣货路径',
      report: '报表统计'
    }
    return map[prefix] ?? '其他'
  }
  list.forEach((code) => {
    const module = moduleOf(code)
    if (!groups.has(module)) groups.set(module, [])
    groups.get(module)!.push({ code, name: PERM_NAMES[code] ?? code })
  })
  return Array.from(groups.entries()).map(([module, items]) => ({ module, items }))
})

/** 角色显示名 */
const roleNames = computed(() => {
  const map: Record<string, string> = {
    ADMIN: '系统管理员',
    KEEPER: '仓管员',
    PICKER: '拣货员'
  }
  return (profile.value?.roles ?? []).map((r) => map[r] ?? r)
})

/** 头像首字母 */
const avatarText = computed(() => {
  const name = profile.value?.realName || profile.value?.username || '?'
  return name.charAt(0).toUpperCase()
})

async function load() {
  loading.value = true
  try {
    profile.value = await authApi.currentUser()
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

async function handleLogout() {
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

onMounted(load)
</script>

<template>
  <div class="page-root profile-page">
    <ElRow :gutter="12">
      <!-- 身份卡 -->
      <ElCol :xs="24" :lg="8">
        <div v-loading="loading" class="wms-card profile-page__card">
          <div class="profile-page__avatar">{{ avatarText }}</div>
          <div class="profile-page__name">{{ profile?.realName || profile?.username || '未登录' }}</div>
          <div class="profile-page__roles">
            <ElTag v-for="r in roleNames" :key="r" type="primary" effect="dark" size="small">
              {{ r }}
            </ElTag>
            <ElTag v-if="!roleNames.length" type="info" size="small">无角色</ElTag>
          </div>

          <ElDivider class="profile-page__divider" />

          <div class="profile-page__meta">
            <div class="profile-page__meta-row">
              <span class="profile-page__meta-label">登录名</span>
              <span class="profile-page__meta-value">{{ profile?.username || '—' }}</span>
            </div>
            <div class="profile-page__meta-row">
              <span class="profile-page__meta-label">用户 ID</span>
              <span class="profile-page__meta-value">{{ profile?.userId ?? '—' }}</span>
            </div>
            <div class="profile-page__meta-row">
              <span class="profile-page__meta-label">权限数量</span>
              <span class="profile-page__meta-value is-primary">
                {{ profile?.permissions?.length ?? 0 }} 项
              </span>
            </div>
            <div class="profile-page__meta-row">
              <span class="profile-page__meta-label">是否管理员</span>
              <span class="profile-page__meta-value">
                <ElTag :type="userStore.isAdmin ? 'success' : 'info'" size="small">
                  {{ userStore.isAdmin ? '是' : '否' }}
                </ElTag>
              </span>
            </div>
          </div>

          <ElButton class="profile-page__logout" :icon="'SwitchButton'" @click="handleLogout">
            退出登录
          </ElButton>
        </div>
      </ElCol>

      <!-- 权限清单 -->
      <ElCol :xs="24" :lg="16">
        <div class="wms-card">
          <div class="wms-card__title">
            我的权限清单
            <ElTooltip
              content="登录时由后端返回权限编码列表，前端据此控制菜单显隐与按钮可用性；接口侧也按同一套权限编码校验"
              placement="top"
            >
              <ElIcon class="profile-page__info"><QuestionFilled /></ElIcon>
            </ElTooltip>
          </div>

          <template v-if="groupedPermissions.length">
            <div
              v-for="group in groupedPermissions"
              :key="group.module"
              class="profile-page__group"
            >
              <div class="profile-page__group-title">
                <ElIcon><Folder /></ElIcon>
                <span>{{ group.module }}</span>
                <ElTag size="small" type="info" effect="plain">{{ group.items.length }} 项</ElTag>
              </div>
              <div class="profile-page__perms">
                <div v-for="p in group.items" :key="p.code" class="profile-page__perm">
                  <ElIcon class="profile-page__perm-icon"><Select /></ElIcon>
                  <span class="profile-page__perm-name">{{ p.name }}</span>
                  <span class="profile-page__perm-code">{{ p.code }}</span>
                </div>
              </div>
            </div>
          </template>

          <EmptyState v-else-if="!loading" description="未获取到权限信息" />
        </div>

        <!-- 说明 -->
        <div class="wms-card">
          <div class="wms-card__title">权限模型说明</div>
          <div class="profile-page__explain">
            <p>
              系统采用<b>用户 — 角色 — 权限</b>三级模型：用户通过角色获得权限，
              角色与权限之间可以灵活调整，无需逐个用户配置。
            </p>
            <p>
              演示账号对应三种角色，权限数量分别为：
              <b>admin</b>（系统管理员）17 项、<b>keeper</b>（仓管员）11 项、
              <b>picker</b>（拣货员）6 项。用不同账号登录可以看到菜单与按钮的差异。
            </p>
            <p>
              口令在数据库中以<b>加盐迭代哈希（PBKDF2）</b>存储，
              同一口令两次加密结果不同，无法从数据反推原始口令。
            </p>
          </div>
        </div>
      </ElCol>
    </ElRow>
  </div>
</template>

<style scoped lang="scss">
.profile-page {
  &__card {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 28px 20px;
  }

  &__avatar {
    width: 72px;
    height: 72px;
    border-radius: 50%;
    background: linear-gradient(135deg, var(--el-color-primary), #60a5fa);
    color: #fff;
    font-size: 30px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 14px rgb(37 99 235 / 26%);
  }

  &__name {
    margin-top: 14px;
    font-size: 18px;
    font-weight: 600;
    color: #1f2937;
  }

  &__roles {
    margin-top: 8px;
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    justify-content: center;
  }

  &__divider {
    width: 100%;
    margin: 18px 0 12px;
  }

  &__meta {
    width: 100%;
  }

  &__meta-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 7px 0;
    border-bottom: 1px dashed #f2f3f5;

    &:last-child {
      border-bottom: none;
    }
  }

  &__meta-label {
    font-size: 12px;
    color: #909399;
  }

  &__meta-value {
    font-size: 13px;
    color: #303133;
    font-family: 'Consolas', monospace;

    &.is-primary {
      color: var(--el-color-primary);
      font-weight: 600;
    }
  }

  &__logout {
    margin-top: 20px;
    width: 100%;
  }

  &__info {
    margin-left: 4px;
    color: #c0c4cc;
    cursor: help;
    font-size: 13px;
  }

  &__group {
    & + & {
      margin-top: 16px;
      padding-top: 14px;
      border-top: 1px dashed #ebeef5;
    }
  }

  &__group-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 600;
    color: #4b5563;

    .el-icon {
      color: var(--el-color-primary);
    }
  }

  &__perms {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px;
  }

  &__perm {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    border-radius: 6px;
    background: #f8fafc;
    font-size: 12px;
  }

  &__perm-icon {
    color: #10b981;
    flex-shrink: 0;
  }

  &__perm-name {
    color: #303133;
  }

  &__perm-code {
    margin-left: auto;
    font-family: 'Consolas', monospace;
    font-size: 11px;
    color: #a8abb2;
  }

  &__explain {
    font-size: 12.5px;
    line-height: 1.9;
    color: #606266;

    p {
      margin: 0 0 10px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    b {
      color: var(--el-color-primary);
    }
  }
}

@media (max-width: 900px) {
  .profile-page__perms {
    grid-template-columns: 1fr;
  }
}
</style>
