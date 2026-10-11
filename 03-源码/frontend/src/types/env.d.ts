/// <reference types="vite/client" />

/**
 * 环境变量类型声明。
 * 与 .env.development / .env.production 中的键一一对应，
 * 这样在代码中读取 import.meta.env 时具备类型提示。
 */
interface ImportMetaEnv {
  /** 应用标题 */
  readonly VITE_APP_TITLE: string
  /** 接口基础路径 */
  readonly VITE_API_BASE_URL: string
  /** 开发态代理目标（仅开发环境存在） */
  readonly VITE_PROXY_TARGET?: string
  /** 开发服务器端口（仅开发环境存在） */
  readonly VITE_PORT?: string
  /** 静态资源基础路径 */
  readonly VITE_PUBLIC_PATH: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/** 允许导入 .vue 文件 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

/** 允许导入 SVG 雪碧图 */
declare module 'virtual:svg-icons-register' {
  const src: string
  export default src
}

declare module 'virtual:svg-icons-names' {
  const ids: string[]
  export default ids
}
