<script setup lang="ts">
/**
 * 根组件。
 *
 * 仅负责渲染路由出口与全局配置提供者；
 * 具体布局由 layout/index.vue 承担，登录页与错误页不使用该布局。
 */
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <ElConfigProvider :locale="zhCn" :z-index="3000">
    <RouterView />
  </ElConfigProvider>
</template>
