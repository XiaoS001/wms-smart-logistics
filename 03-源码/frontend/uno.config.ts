/**
 * UnoCSS 配置：原子化 CSS。
 *
 * 设计取舍：只启用常用预设，不引入图标集合的远程加载，
 * 保持构建可离线完成。仓库平面图与路径图使用手写 SVG，不依赖图标包。
 */
import { defineConfig, presetUno, presetAttributify } from 'unocss'

export default defineConfig({
  presets: [
    presetUno(),
    presetAttributify()
  ],
  theme: {
    colors: {
      // 与 Element Plus 主色保持一致，避免两套配色
      primary: 'var(--el-color-primary)',
      success: 'var(--el-color-success)',
      warning: 'var(--el-color-warning)',
      danger: 'var(--el-color-danger)',
      info: 'var(--el-color-info)'
    }
  },
  shortcuts: {
    // 常用布局快捷类
    'flex-center': 'flex items-center justify-center',
    'flex-between': 'flex items-center justify-between',
    'flex-col-center': 'flex flex-col items-center justify-center',
    'page-container': 'p-4 bg-white rd-2',
    'card-title': 'text-4 font-bold mb-3 color-#303133'
  },
  rules: [
    // 允许 text-14 这类按像素设置字号的写法
    [/^text-(\d+)$/, ([, d]) => ({ 'font-size': `${d}px` })],
    [/^gap-(\d+)$/, ([, d]) => ({ gap: `${d}px` })],
    [/^size-(\d+)$/, ([, d]) => ({ width: `${d}px`, height: `${d}px` })]
  ]
})
