/**
 * 业务字典与常量。
 *
 * 所有枚举值都与后端保持一致，避免在页面中散落魔法数字。
 */

/** 单据状态 */
export const ORDER_STATUS = {
  INBOUND: {
    0: { label: '待入库', type: 'warning' },
    1: { label: '部分入库', type: 'warning' },
    2: { label: '已完成', type: 'success' },
    9: { label: '已取消', type: 'info' }
  },
  OUTBOUND: {
    0: { label: '待拣货', type: 'warning' },
    1: { label: '拣货中', type: 'primary' },
    2: { label: '已完成', type: 'success' },
    9: { label: '已取消', type: 'info' }
  },
  STOCKTAKE: {
    0: { label: '待盘点', type: 'warning' },
    1: { label: '已盘点', type: 'primary' },
    2: { label: '已调整', type: 'success' }
  }
} as const

/** 货位状态 */
export const LOCATION_STATUS: Record<number, { label: string; type: string }> = {
  1: { label: '空闲', type: 'success' },
  2: { label: '占用', type: 'warning' },
  0: { label: '停用', type: 'info' }
}

/** 启用状态 */
export const ENABLE_STATUS: Record<number, { label: string; type: string }> = {
  1: { label: '启用', type: 'success' },
  0: { label: '停用', type: 'info' }
}

/** ABC 分类配色：A 类近端高频、C 类远端低频 */
export const ABC_COLORS: Record<string, string> = {
  A: '#f56c6c',
  B: '#e6a23c',
  C: '#409eff'
}

/** 库区类型 */
export const ZONE_TYPES: Array<{ label: string; value: string }> = [
  { label: 'A 类近端区', value: 'A' },
  { label: 'B 类中区', value: 'B' },
  { label: 'C 类远端区', value: 'C' },
  { label: '普通库区', value: 'NORMAL' }
]

/** 货位分配策略 */
export const SLOTTING_STRATEGIES: Array<{ label: string; value: string; desc: string }> = [
  {
    label: '频次贪心分配',
    value: 'frequency-greedy',
    desc: '商品按出库频次降序、货位按距离升序逐一配对，工程上最常用'
  },
  { label: 'ABC 分区贪心', value: 'abc-greedy', desc: '先按 ABC 分类划分近中远分区，再在类内按频次配对' },
  { label: '遗传算法分配', value: 'genetic', desc: '整数编码 + 锦标赛选择 + 均匀交叉 + 变异 + 精英保留' },
  { label: '随机分配', value: 'random', desc: '作为效果对照基线，不利用频次信息' }
]

/** 拣货路径策略 */
export const PICKING_STRATEGIES: Array<{ label: string; value: string; desc: string }> = [
  { label: '蚁群算法', value: 'ant-colony', desc: '信息素 + 启发式信息构造解，路径通常最短' },
  { label: '最近邻策略', value: 'nearest-neighbor', desc: '每次选择最近的未访问拣货点，O(n²)' },
  { label: 'S 型穿越策略', value: 's-shape', desc: '传统仓储作业默认做法，作为对比基线' },
  { label: '最大间隙策略', value: 'largest-gap', desc: '在巷道内最大间隙处折返，减少无效穿越' }
]

/** 权限编码常量：避免页面中硬编码字符串 */
export const PERM = {
  SYSTEM_VIEW: 'system:view',
  WAREHOUSE_VIEW: 'warehouse:view',
  LOCATION_VIEW: 'location:view',
  PRODUCT_VIEW: 'product:view',
  PRODUCT_ADD: 'product:add',
  PRODUCT_EDIT: 'product:edit',
  PRODUCT_DELETE: 'product:delete',
  INBOUND_VIEW: 'inbound:view',
  INBOUND_ADD: 'inbound:add',
  OUTBOUND_VIEW: 'outbound:view',
  OUTBOUND_ADD: 'outbound:add',
  STOCK_VIEW: 'stock:view',
  SLOTTING_VIEW: 'slotting:view',
  SLOTTING_RUN: 'slotting:run',
  PICKING_VIEW: 'picking:view',
  PICKING_RUN: 'picking:run',
  REPORT_VIEW: 'report:view'
} as const

/** 默认仓库 ID：当前系统为单仓库场景 */
export const DEFAULT_WAREHOUSE_ID = 1
