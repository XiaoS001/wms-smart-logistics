<script setup lang="ts">
/**
 * 登录页。
 *
 * 设计考虑：
 * 1. 左侧品牌区展示课题名称与系统定位，右侧为登录表单，宽屏下视觉更完整；
 * 2. 提供演示账号一键填充，便于答辩现场演示不同角色的权限差异；
 * 3. 表单校验与后端提示分工：前端校验必填与长度，后端校验账号与口令。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useMenu } from '@/composables/useMenu'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
// 登录成功后按角色落到第一个可访问的页面
const { firstAccessiblePath } = useMenu()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const capsLock = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入登录名', trigger: 'blur' },
    { min: 2, max: 32, message: '登录名长度为 2 到 32 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6 到 32 个字符', trigger: 'blur' }
  ]
}

/** 演示账号：三个角色的权限范围不同，便于展示权限控制效果 */
const demoAccounts = [
  { label: '系统管理员', username: 'admin', desc: '全部 17 项权限' },
  { label: '仓管员', username: 'keeper', desc: '11 项业务权限' },
  { label: '拣货员', username: 'picker', desc: '6 项拣货权限' }
]

function fillAccount(username: string) {
  form.username = username
  form.password = '123456'
}

/** 检测大写锁定：密码输入最常见的困惑来源 */
function checkCapsLock(event: KeyboardEvent) {
  capsLock.value = event.getModifierState?.('CapsLock') ?? false
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await userStore.login({ username: form.username.trim(), password: form.password })
    ElMessage.success(`欢迎回来，${userStore.displayName}`)
    // 落点按角色决定：不同角色可访问的第一个页面不同，
    // 固定跳 /dashboard 会让没有 report:view 权限的角色（如拣货员）直接看到 403。
    const redirect = (route.query.redirect as string) || firstAccessiblePath.value || '/dashboard'
    router.replace(redirect)
  } catch {
    // 失败提示已由请求拦截器统一处理，这里只需恢复按钮状态
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // 若已登录则直接进入系统，避免重复登录
  if (userStore.isLogin) {
    router.replace(firstAccessiblePath.value || '/dashboard')
  }
})
</script>

<template>
  <div class="login">
    <!-- 左侧品牌区 -->
    <section class="login__brand">
      <div class="login__brand-inner">
        <div class="login__logo">
          <span class="login__logo-text">WMS</span>
        </div>
        <h1 class="login__brand-title">智慧仓储管理系统</h1>
        <p class="login__brand-sub">
          面向智慧物流的仓储管理系统设计与实现
        </p>
        <p class="login__brand-desc">以货位分配与拣货路径优化为核心</p>

        <ul class="login__features">
          <li>
            <ElIcon><Aim /></ElIcon>
            <span>货位分配优化：按出库频次智能匹配货位</span>
          </li>
          <li>
            <ElIcon><Guide /></ElIcon>
            <span>拣货路径规划：蚁群算法缩短行走距离</span>
          </li>
          <li>
            <ElIcon><DataAnalysis /></ElIcon>
            <span>效果量化对比：优化前后数据可追溯</span>
          </li>
        </ul>

        <div class="login__brand-footer">
          <span>软件工程 2306 班</span>
          <span class="login__brand-sep">·</span>
          <span>李俊杰</span>
          <span class="login__brand-sep">·</span>
          <span>252300604</span>
        </div>
      </div>
    </section>

    <!-- 右侧表单区 -->
    <section class="login__form-area">
      <div class="login__form-card">
        <div class="login__form-header">
          <h2>账号登录</h2>
          <p>请输入账号信息以进入系统</p>
        </div>

        <ElForm
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          label-position="top"
          @submit.prevent="handleLogin"
        >
          <ElFormItem label="登录名" prop="username">
            <ElInput
              v-model="form.username"
              placeholder="请输入登录名"
              clearable
              :prefix-icon="'User'"
              autocomplete="username"
            />
          </ElFormItem>

          <ElFormItem label="密码" prop="password">
            <ElInput
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              show-password
              :prefix-icon="'Lock'"
              autocomplete="current-password"
              @keyup="checkCapsLock"
              @keyup.enter="handleLogin"
            />
            <div v-if="capsLock" class="login__caps">大写锁定已开启</div>
          </ElFormItem>

          <ElButton
            type="primary"
            size="large"
            class="login__submit"
            :loading="submitting"
            @click="handleLogin"
          >
            {{ submitting ? '登录中…' : '登 录' }}
          </ElButton>
        </ElForm>

        <ElDivider class="login__divider">
          <span class="login__divider-text">演示账号（点击填充，密码均为 123456）</span>
        </ElDivider>

        <div class="login__demo">
          <button
            v-for="account in demoAccounts"
            :key="account.username"
            type="button"
            class="login__demo-item"
            @click="fillAccount(account.username)"
          >
            <span class="login__demo-label">{{ account.label }}</span>
            <span class="login__demo-user">{{ account.username }}</span>
            <span class="login__demo-desc">{{ account.desc }}</span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.login {
  display: flex;
  width: 100%;
  height: 100%;
  background: #fff;

  /* ------------------------ 左侧品牌区 ------------------------ */
  &__brand {
    flex: 1.1;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px;
    background:
      radial-gradient(circle at 20% 20%, rgb(96 165 250 / 25%), transparent 45%),
      radial-gradient(circle at 80% 70%, rgb(37 99 235 / 30%), transparent 50%),
      linear-gradient(135deg, #111827 0%, #1e3a8a 60%, #1d4ed8 100%);
    color: #fff;
    position: relative;
    overflow: hidden;

    /* 背景网格纹理：呼应仓储货架的网格化布局 */
    &::after {
      content: '';
      position: absolute;
      inset: 0;
      background-image:
        linear-gradient(rgb(255 255 255 / 5%) 1px, transparent 1px),
        linear-gradient(90deg, rgb(255 255 255 / 5%) 1px, transparent 1px);
      background-size: 48px 48px;
      pointer-events: none;
    }
  }

  &__brand-inner {
    position: relative;
    z-index: 1;
    max-width: 460px;
  }

  &__logo {
    width: 56px;
    height: 56px;
    border-radius: 14px;
    background: rgb(255 255 255 / 14%);
    border: 1px solid rgb(255 255 255 / 22%);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 22px;
  }

  &__logo-text {
    font-size: 18px;
    font-weight: 700;
    letter-spacing: 1px;
  }

  &__brand-title {
    margin: 0;
    font-size: 30px;
    font-weight: 700;
    letter-spacing: 1px;
  }

  &__brand-sub {
    margin: 12px 0 0;
    font-size: 14px;
    color: rgb(255 255 255 / 78%);
  }

  &__brand-desc {
    margin: 6px 0 0;
    font-size: 13px;
    color: rgb(255 255 255 / 60%);
  }

  &__features {
    list-style: none;
    margin: 34px 0 0;
    padding: 0;

    li {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 14px;
      font-size: 13px;
      color: rgb(255 255 255 / 82%);
    }

    .el-icon {
      width: 26px;
      height: 26px;
      border-radius: 6px;
      background: rgb(255 255 255 / 12%);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
  }

  &__brand-footer {
    margin-top: 40px;
    font-size: 12px;
    color: rgb(255 255 255 / 45%);
  }

  &__brand-sep {
    margin: 0 8px;
  }

  /* ------------------------ 右侧表单区 ------------------------ */
  &__form-area {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px;
    background: #fff;
  }

  &__form-card {
    width: 100%;
    max-width: 380px;
  }

  &__form-header {
    margin-bottom: 26px;

    h2 {
      margin: 0;
      font-size: 22px;
      font-weight: 600;
      color: #1f2937;
    }

    p {
      margin: 6px 0 0;
      font-size: 13px;
      color: #909399;
    }
  }

  &__submit {
    width: 100%;
    margin-top: 6px;
    letter-spacing: 4px;
    font-weight: 500;
  }

  &__caps {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-color-warning);
  }

  &__divider {
    margin: 26px 0 16px;
  }

  &__divider-text {
    font-size: 11px;
    color: #a8abb2;
  }

  &__demo {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__demo-item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 8px 12px;
    border: 1px dashed #dcdfe6;
    border-radius: 6px;
    background: #fafafa;
    cursor: pointer;
    text-align: left;
    transition: all 0.2s;

    &:hover {
      border-color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);

      .login__demo-user {
        color: var(--el-color-primary);
      }
    }
  }

  &__demo-label {
    flex-shrink: 0;
    font-size: 12px;
    color: #303133;
    font-weight: 500;
    width: 76px;
  }

  &__demo-user {
    font-size: 12px;
    color: #606266;
    font-family: 'Consolas', monospace;
    width: 62px;
  }

  &__demo-desc {
    margin-left: auto;
    font-size: 11px;
    color: #a8abb2;
  }
}

@media (max-width: 900px) {
  .login__brand {
    display: none;
  }
}
</style>
