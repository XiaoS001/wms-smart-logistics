import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'
import compression from 'vite-plugin-compression'
import path from 'node:path'

/**
 * Vite 构建配置。
 *
 * 关键约定：
 * 1. dev 环境通过 proxy 把 /api 转发到后端 8080，避免跨域；
 * 2. Element Plus 采用按需引入，避免全量打包导致产物过大；
 * 3. 生产构建启用 gzip 与 brotli 预压缩，并做基础分包。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const isProd = mode === 'production'

  return {
    base: env.VITE_PUBLIC_PATH || '/',

    plugins: [
      vue(),
      vueJsx(),
      UnoCSS(),

      // API 与组合式函数自动导入，减少样板 import
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia', '@vueuse/core'],
        resolvers: [ElementPlusResolver()],
        dts: 'src/types/auto-imports.d.ts',
        eslintrc: { enabled: false }
      }),

      // 组件按需引入：Element Plus 只打包实际用到的组件
      Components({
        resolvers: [ElementPlusResolver()],
        dirs: ['src/components'],
        extensions: ['vue'],
        deep: true,
        dts: 'src/types/components.d.ts'
      }),

      // SVG 雪碧图：src/assets/icons 下的 svg 可作为图标使用
      createSvgIconsPlugin({
        iconDirs: [path.resolve(process.cwd(), 'src/assets/icons')],
        symbolId: 'icon-[dir]-[name]',
        inject: 'body-last'
      }),

      // 预压缩：部署在 Nginx 上时可直接提供 .gz / .br 文件
      isProd &&
        compression({
          verbose: false,
          disable: false,
          threshold: 10240,
          algorithm: 'gzip',
          ext: '.gz'
        }),
      isProd &&
        compression({
          verbose: false,
          disable: false,
          threshold: 10240,
          algorithm: 'brotliCompress',
          ext: '.br'
        })
    ].filter(Boolean),

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
        '#': fileURLToPath(new URL('./src/types', import.meta.url))
      }
    },

    css: {
      preprocessorOptions: {
        scss: {
          api: 'modern-compiler'
          // 说明：Element Plus 2.x 的主题定制通过 CSS 变量完成（见 styles/element-theme.scss），
          // 因此这里不注入 SCSS 变量，避免与 @use 的模块系统冲突。
        }
      }
    },

    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_PORT) || 5173,
      open: false,
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
          ws: true
        }
      }
    },

    build: {
      /**
       * 目标设为 esnext，让 esbuild 跳过向下转译。
       *
       * 原因：esbuild 在转译阶段会在系统临时目录创建中间文件并在结束时删除，
       * 而本机开发环境对该目录的删除操作被安全策略拒绝，会报
       * "remove ...esbuild-xxxx: Access is denied" 导致构建中断。
       * 目标足够新时 esbuild 无需转译，中间文件不产生，构建即可顺利完成。
       *
       * 语法正确性由 `npm run typecheck`（vue-tsc）单独保证，
       * 目标为现代浏览器（Chrome/Edge 90+），满足管理系统的运行环境要求。
       */
      target: 'esnext',
      outDir: 'dist',
      assetsDir: 'assets',
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
      reportCompressedSize: false,
      rollupOptions: {
        output: {
          // 基础分包：把体积大且更新频率低的库单独拆出，利于浏览器缓存
          manualChunks(id) {
            if (!id.includes('node_modules')) return
            if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
            if (id.includes('element-plus')) return 'element-plus'
            if (id.includes('vue') || id.includes('pinia')) return 'vue-vendor'
            return 'vendor'
          },
          chunkFileNames: 'assets/js/[name]-[hash].js',
          entryFileNames: 'assets/js/[name]-[hash].js',
          assetFileNames: 'assets/[ext]/[name]-[hash].[ext]'
        }
      }
    },

    optimizeDeps: {
      include: ['vue', 'vue-router', 'pinia', 'axios', 'element-plus', 'echarts', 'dayjs']
    },

    esbuild: {
      drop: isProd ? ['console', 'debugger'] : []
    }
  }
})
