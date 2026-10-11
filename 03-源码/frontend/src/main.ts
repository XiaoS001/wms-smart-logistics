import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'

// 样式加载顺序：reset -> 第三方 -> 项目全局样式，确保项目样式可覆盖第三方
import 'virtual:svg-icons-register'
import 'element-plus/dist/index.css'
import '@/styles/index.scss'

const app = createApp(App)

// Pinia：统一状态管理，并启用持久化
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(pinia)

app.use(router)
// 中文语言包：日期选择器、分页等组件的中文显示
app.use(ElementPlus, { locale: zhCn })

// 全局注册 Element Plus 图标，模板中可直接使用 <el-icon><HomeFilled /></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 全局错误处理：避免单个组件异常导致整页白屏
app.config.errorHandler = (err, _instance, info) => {
  console.error('[全局异常]', err, info)
}

app.mount('#app')
