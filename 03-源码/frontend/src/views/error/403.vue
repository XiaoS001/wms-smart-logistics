<script setup lang="ts">
/** 403 无权访问 */
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="error-page">
    <div class="error-page__code">403</div>
    <h2 class="error-page__title">无权访问该页面</h2>
    <p class="error-page__desc">
      当前账号的角色不具备访问此功能所需的权限。
      <br />
      如需使用，请联系系统管理员为你的角色分配相应权限。
    </p>
    <div class="error-page__actions">
      <ElButton type="primary" @click="router.push('/dashboard')">返回首页</ElButton>
      <ElButton @click="router.back()">返回上一页</ElButton>
    </div>
  </div>
</template>

<style scoped lang="scss">
.error-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 60px 20px;
  text-align: center;

  &__code {
    font-size: 96px;
    font-weight: 800;
    line-height: 1;
    background: linear-gradient(135deg, var(--el-color-warning), var(--el-color-danger));
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
    letter-spacing: 4px;
  }

  &__title {
    margin: 20px 0 0;
    font-size: 20px;
    color: #303133;
    font-weight: 600;
  }

  &__desc {
    margin: 12px 0 0;
    font-size: 13px;
    line-height: 1.9;
    color: #909399;
  }

  &__actions {
    margin-top: 26px;
    display: flex;
    gap: 12px;
  }
}
</style>
