<script setup lang="ts">
/**
 * 面包屑：由当前路由的 matched 链推导，无需手工维护。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

interface Crumb {
  title: string
  path: string
  clickable: boolean
}

const crumbs = computed<Crumb[]>(() => {
  const list: Crumb[] = []
  route.matched.forEach((record) => {
    const meta = (record.meta || {}) as Record<string, unknown>
    const title = meta.title as string | undefined
    if (!title || meta.hidden) return
    // 父级分组路由通常没有独立页面，标记为不可点击
    const isGroup = !record.components?.default || record.path === '/'
    list.push({ title, path: record.path, clickable: !isGroup })
  })
  return list
})
</script>

<template>
  <ElBreadcrumb separator="/" class="app-breadcrumb">
    <ElBreadcrumbItem v-for="(crumb, index) in crumbs" :key="crumb.path + index">
      <span v-if="!crumb.clickable || index === crumbs.length - 1" class="is-current">
        {{ crumb.title }}
      </span>
      <RouterLink v-else :to="crumb.path" class="is-link">{{ crumb.title }}</RouterLink>
    </ElBreadcrumbItem>
  </ElBreadcrumb>
</template>

<style scoped lang="scss">
.app-breadcrumb {
  font-size: 13px;

  .is-current {
    color: #909399;
    font-weight: 400;
  }

  .is-link {
    color: #606266;
    text-decoration: none;

    &:hover {
      color: var(--el-color-primary);
    }
  }
}
</style>
