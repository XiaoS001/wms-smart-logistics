# 前端工程

面向智慧物流的仓储管理系统 —— 前端部分。

## 技术栈

| 类别 | 选型 |
|---|---|
| 框架 | Vue 3.5（组合式 API + `<script setup>`） |
| 语言 | TypeScript 5.6（strict 模式） |
| 构建 | Vite 6 |
| UI | Element Plus 2.8（按需引入） |
| 状态 | Pinia 2 + 持久化插件 |
| 路由 | Vue Router 4（懒加载 + 权限守卫） |
| 样式 | UnoCSS（原子化）+ SCSS（主题） |
| 图表 | ECharts 5 + vue-echarts |
| 请求 | Axios（统一拦截器） |
| 规范 | ESLint 9 + Prettier 3 + Husky + commitlint |

## 快速开始

```bash
# 1. 安装依赖（首次）
npm install

# 2. 启动开发服务器（默认 http://localhost:5173）
npm run dev

# 3. 生产构建
npm run build

# 4. 本地预览构建产物
npm run preview

# 5. 代码检查与格式化
npm run lint
npm run format
```

> **前提**：后端需已启动在 `http://127.0.0.1:8080`。开发服务器通过 Vite proxy
> 把 `/api` 转发到后端，因此前端无需配置跨域。
> 若后端未启动，页面右上角的状态指示会显示"服务未连接"。

## 演示账号

| 角色 | 账号 | 密码 | 权限范围 |
|---|---|---|---|
| 系统管理员 | `admin` | `123456` | 全部 17 项权限 |
| 仓管员 | `keeper` | `123456` | 11 项业务权限 |
| 拣货员 | `picker` | `123456` | 6 项拣货相关权限 |

登录页提供一键填充，便于展示不同角色的权限差异。

## 目录结构

```
src/
├── api/          接口定义，按业务模块拆分，与后端 68 个接口一一对应
├── assets/       静态资源（icons 下的 svg 会打成雪碧图）
├── components/   通用组件（布局组件在 layout/components 下）
├── composables/  组合式函数（useMenu 等）
├── layout/       主布局：侧边栏 + 顶栏 + 多标签页
├── router/       路由表与全局守卫
├── stores/       Pinia 状态（user / app）
├── styles/       全局样式与主题
├── types/        类型定义（common / auth / base / business / env）
├── utils/        工具（request 请求封装、format 格式化、constants 字典）
└── views/        页面，按业务模块分目录
```

## 工程约定

1. **请求封装**：`utils/request.ts` 自动注入令牌、统一解包 `Result`、
   401 跳登录、错误提示去重，调用方拿到的是业务 `data`，无需再解包。
2. **权限控制**：
   - 路由级：路由 `meta.perm` 指定权限编码，守卫无权限跳 403；
   - 菜单级：`useMenu` 按权限过滤路由表自动生成菜单；
   - 按钮级：使用 `userStore.hasPermission('product:add')` 判断。
3. **接口地址**：统一走 `import.meta.env.VITE_API_BASE_URL`（默认 `/api`），
   开发态由 proxy 转发，生产态由 Web 服务器反向代理。
4. **提交规范**：Husky + commitlint 强制 `feat/fix/docs/refactor` 等前缀。

## 页面实现状态

| 页面 | 路径 | 状态 |
|---|---|---|
| 登录 | `/login` | ✅ 已实现 |
| 403 / 404 | `/403` `/404` | ✅ 已实现 |
| 其余业务页面 | — | 占位中，由 `build/tools/gen_view_stubs.py` 生成 |

占位页面会列出该页规划使用的后端接口，实现后直接用真实组件覆盖同名文件即可，
无需改动路由表。
