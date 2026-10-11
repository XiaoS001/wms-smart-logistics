<script setup lang="ts">
/**
 * 空状态提示组件。
 *
 * 统一各页面"暂无数据"的呈现，避免每个页面各写一套。
 */
interface Props {
  /** 提示文案 */
  description?: string
  /** 图标尺寸 */
  size?: number
}

withDefaults(defineProps<Props>(), {
  description: '暂无数据',
  size: 80
})
</script>

<template>
  <div class="empty-state">
    <svg class="empty-state__icon" :width="size" :height="size" viewBox="0 0 120 120" fill="none">
      <ellipse cx="60" cy="98" rx="38" ry="7" fill="#f2f3f5" />
      <rect x="28" y="34" width="64" height="48" rx="6" fill="#f8fafc" stroke="#e4e7ed" stroke-width="2" />
      <path d="M28 50h64M28 66h64" stroke="#e4e7ed" stroke-width="2" />
      <path d="M46 34v48M74 34v48" stroke="#e4e7ed" stroke-width="2" />
      <circle cx="88" cy="30" r="12" fill="#fff" stroke="#dcdfe6" stroke-width="2" />
      <path d="M83 30h10M88 25v10" stroke="#c0c4cc" stroke-width="2" stroke-linecap="round" />
    </svg>
    <p class="empty-state__text">{{ description }}</p>
    <div v-if="$slots.default" class="empty-state__extra">
      <slot />
    </div>
  </div>
</template>

<style scoped lang="scss">
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px 20px;

  &__icon {
    opacity: 0.9;
  }

  &__text {
    margin: 14px 0 0;
    font-size: 13px;
    color: #a8abb2;
  }

  &__extra {
    margin-top: 14px;
  }
}
</style>
