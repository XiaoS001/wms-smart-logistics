<script setup lang="ts">
/** 404 页面不存在 */
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="error-page">
    <div class="error-page__code">404</div>
    <h2 class="error-page__title">页面不存在</h2>
    <p class="error-page__desc">
      你访问的页面可能已被移除、重命名，或者地址输入有误。
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
    background: linear-gradient(135deg, var(--el-color-primary), #60a5fa);
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
