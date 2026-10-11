<script setup lang="ts">
/**
 * 商品分类（占位页面）
 *
 * 该页面尚未实现，此处先登记占位组件，保证路由完整、工程可启动。
 * 实现时直接用真实组件覆盖本文件即可，无需改动路由表。
 *
 * 规划使用的后端接口：
 *   - GET /api/categories/tree
 *   - POST /api/categories
 *   - PUT /api/categories/{id}
 *   - DELETE /api/categories/{id}
 */
</script>

<template>
  <div class="page-root">
    <div class="wms-card stub">
      <ElIcon class="stub__icon" :size="46"><Tools /></ElIcon>
      <h2 class="stub__title">商品分类</h2>
      <p class="stub__desc">该页面正在开发中，敬请期待。</p>

      <div class="stub__section">
        <div class="stub__section-title">规划使用的后端接口</div>
        <ul class="stub__list">
          <li>GET /api/categories/tree</li>
          <li>POST /api/categories</li>
          <li>PUT /api/categories/{id}</li>
          <li>DELETE /api/categories/{id}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.stub {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 20px;
  text-align: center;

  &__icon {
    color: var(--el-color-primary-light-5);
  }

  &__title {
    margin: 18px 0 0;
    font-size: 20px;
    font-weight: 600;
    color: #303133;
  }

  &__desc {
    margin: 8px 0 0;
    font-size: 13px;
    color: #909399;
  }

  &__section {
    margin-top: 30px;
    width: 100%;
    max-width: 520px;
    text-align: left;
  }

  &__section-title {
    font-size: 13px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 10px;
  }

  &__list {
    margin: 0;
    padding: 12px 16px;
    list-style: none;
    background: #f8fafc;
    border: 1px dashed #dcdfe6;
    border-radius: 6px;

    li {
      font-family: 'Consolas', monospace;
      font-size: 12px;
      color: #475569;
      line-height: 2;
    }
  }
}
</style>
