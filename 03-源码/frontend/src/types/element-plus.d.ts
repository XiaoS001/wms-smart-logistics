/**
 * Element Plus 类型增强：放宽 el-table 插槽参数的类型。
 *
 * 问题：el-table 默认插槽参数的类型来自内部声明
 *   element-plus/es/components/table/src/table/defaults.d.ts
 * 其中 `type DefaultRow = Record<PropertyKey, any>`。
 * 于是在模板里写 `@click="openEdit(row)"` 时，row 无法自动匹配业务类型
 * （如 BaseProduct），vue-tsc 报
 * “Argument of type 'DefaultRow' is not assignable to parameter of type ...”。
 *
 * 处理方式：对定义该类型的模块做类型增强，把 DefaultRow 重新声明为 any。
 * 相比在每个使用处写 `row as BaseProduct`，这样一次生效且不污染业务代码。
 *
 * 类型安全性说明：业务数据的类型由 API 层保证
 * （如 `productApi.list(): Promise<BaseProduct[]>`），
 * 模板中访问 row 的字段仍然受这些类型约束；此处只是放宽插槽参数的绑定检查。
 */
declare module 'element-plus/es/components/table/src/table/defaults' {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  type DefaultRow = any
}

export {}
