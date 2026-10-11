/**
 * 业务单据与算法相关类型定义。
 */

/* ============================ 入库 ============================ */

/** 入库单 */
export interface BizInboundOrder {
  id: number
  orderNo: string
  warehouseId: number
  supplier?: string
  totalQuantity: number
  totalAmount: number
  /** 0 待入库 / 2 已完成 / 9 已取消 */
  status: number
  useSlottingSuggest: number
  inboundTime?: string
  remark?: string
  createTime?: string
}

/** 入库单明细（带出商品与货位信息） */
export interface BizInboundItem {
  id: number
  orderId: number
  productId: number
  productCode: string
  productName: string
  pickFreq: number
  abcClass: string
  locationId: number
  locationCode: string
  distance: number
  planQuantity: number
  actualQuantity: number
  unitPrice?: number
  batchNo?: string
  /** 算法给出的推荐理由 */
  suggestReason?: string
}

/** 入库单创建请求 */
export interface InboundOrderRequest {
  warehouseId: number
  supplier?: string
  useSlottingSuggest?: boolean
  strategy?: string
  remark?: string
  items: Array<{
    productId: number
    planQuantity: number
    unitPrice?: number
    batchNo?: string
    locationId?: number | null
  }>
}

/* ============================ 出库 ============================ */

/** 出库单 */
export interface BizOutboundOrder {
  id: number
  orderNo: string
  warehouseId: number
  customer?: string
  totalQuantity: number
  /** 0 待拣货 / 1 拣货中 / 2 已完成 / 9 已取消 */
  status: number
  /** 拣货路径总长度（米） */
  routeDistance?: number
  /** 路径明细 JSON 文本 */
  routeDetail?: string
  /** 所用路径算法编码 */
  routeAlgo?: string
  outboundTime?: string
  remark?: string
  createTime?: string
}

/** 出库单明细 */
export interface BizOutboundItem {
  id: number
  orderId: number
  productId: number
  productCode: string
  productName: string
  unit?: string
  locationId: number
  locationCode: string
  /** 货位坐标，用于绘制平面图与路径 */
  aisleNo: number
  rowNo: number
  distance: number
  quantity: number
  /** 拣货顺序，0 表示尚未规划 */
  pickSeq: number
  picked: number
}

/** 出库单创建请求 */
export interface OutboundOrderRequest {
  warehouseId: number
  customer?: string
  remark?: string
  items: Array<{ productId: number; quantity: number }>
}

/** 坐标点 */
export interface RoutePoint {
  aisleNo: number
  rowNo: number
}

/** 拣货路径规划结果 */
export interface PlanRouteResult {
  orderId: number
  orderNo: string
  strategy: string
  strategyLabel: string
  pickPointCount: number
  distance: number
  elapsedMillis: number
  updatedItems: number
  /** 行走顺序，含起终点出入口 (0,0) */
  route: RoutePoint[]
  /** 各策略的距离对比 */
  comparison: Record<string, number>
}

/* ============================ 库存 ============================ */

/** 库存明细 */
export interface StockDetail {
  id: number
  warehouseId: number
  locationId: number
  locationCode: string
  aisleNo: number
  rowNo: number
  distance: number
  locationStatus: number
  productId: number
  productCode: string
  productName: string
  unit?: string
  abcClass: string
  safetyStock: number
  quantity: number
  lockedQuantity: number
  batchNo?: string
  productionDate?: string
  expiryDate?: string
  updateTime?: string
}

/** 商品库存汇总 */
export interface StockSummary {
  productId: number
  productCode: string
  productName: string
  unit?: string
  abcClass: string
  safetyStock: number
  totalQuantity: number
  locationCount: number
  /** 1 表示低于安全库存或缺货 */
  lowStock: number
}

/** 仓库库存概览 */
export interface WarehouseStockSummary {
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  locationTotal: number
  locationRows: number
  locationUsed: number
  locationFree: number
  usageRate: number
  productKinds: number
  totalQuantity: number
}

/** 盘点记录 */
export interface BizStocktake {
  id: number
  stocktakeNo: string
  warehouseId: number
  locationId: number
  locationCode: string
  distance: number
  productId: number
  productCode: string
  productName: string
  unit?: string
  bookQuantity: number
  actualQuantity: number
  diffQuantity: number
  /** 0 待盘点 / 1 已盘点 / 2 已调整 */
  status: number
  stocktakeTime?: string
  remark?: string
}

/* ============================ 算法 ============================ */

/** 货位推荐单条结果 */
export interface SlottingRecommendation {
  productId: number
  productCode: string
  productName: string
  pickFreq: number
  abcClass: string
  locationId: number
  locationCode: string
  distance: number
  reason: string
}

/** 货位推荐结果 */
export interface SlottingRecommendResult {
  strategy: string
  strategyLabel: string
  objective: number
  averageDistance: number
  itemCount: number
  slotCount: number
  elapsedMillis: number
  freeSlotCount: number
  recommendations: SlottingRecommendation[]
}

/** 优化方案预览中的搬迁项 */
export interface SlottingMove {
  productId?: number
  productCode: string
  pickFreq?: number
  quantity?: number
  fromLocationCode?: string
  fromDistance?: number
  toLocationCode?: string
  toDistance?: number
  distanceSaving?: number
}

/** 优化方案分配明细 */
export interface SlottingAssignment {
  productId: number
  productCode: string
  productName: string
  pickFreq: number
  abcClass: string
  targetLocationId: number
  targetLocationCode: string
  targetDistance: number
  currentLocationId?: number
  currentLocationCode?: string
  currentDistance?: number
}

/** 优化方案摘要 */
export interface SlottingPlanSummary {
  productCount: number
  locationCount: number
  stockedProducts: number
  productsWithoutStock: number
  alreadyInPlace: number
  movesNeeded: number
  currentObjective: number
  optimizedObjective: number
  currentAverageDistance: number
  optimizedAverageDistance: number
  improvementPercent: number
  elapsedMillis: number
}

/** 优化方案预览结果 */
export interface SlottingPlanPreview {
  warehouseId: number
  strategy: string
  strategyLabel: string
  seed: number
  summary: SlottingPlanSummary
  moves: SlottingMove[]
  assignments: SlottingAssignment[]
  note?: string
  /** 保存后返回 */
  planId?: number
  planNo?: string
}

/** 优化方案历史记录 */
export interface SlottingPlan {
  id: number
  planNo: string
  warehouseId: number
  strategy: string
  strategyLabel: string
  productCount: number
  slotCount: number
  currentObjective: number
  optimizedObjective: number
  improvementPercent: number
  elapsedMillis: number
  applied: number
  appliedTime?: string
  movedRecords?: number
  remark?: string
  createTime: string
}

/* ============================ 报表 ============================ */

/** 首页概览 */
export interface DashboardData {
  productCount: number
  locationCount: number
  occupiedLocationCount: number
  freeLocationCount: number
  occupancyRate: number
  stockQuantity: number
  lowStockCount: number
  pendingInboundCount: number
  pendingOutboundCount: number
  doneInboundCount: number
  doneOutboundCount: number
}

/** 作业量趋势单日数据 */
export interface OperationTrendRow {
  statDate: string
  inboundOrders: number
  inboundQuantity: number
  outboundOrders: number
  outboundQuantity: number
}

/** 作业量趋势结果 */
export interface OperationTrendResult {
  summary: {
    days: number
    activeDays: number
    inboundOrders: number
    inboundQuantity: number
    outboundOrders: number
    outboundQuantity: number
  }
  trend: OperationTrendRow[]
}

/** 库区库存分布 */
export interface ZoneStockDistribution {
  zoneId: number
  zoneName: string
  zoneType: string
  locationCount: number
  usedLocations: number
  freeLocations: number
  usageRate: number
  stockQuantity: number
}

/** 库存分布结果 */
export interface StockDistributionResult {
  byZone: ZoneStockDistribution[]
  byAbcClass: Array<{
    abcClass: string
    productKinds: number
    stockQuantity: number
    pickFreqSum: number
  }>
}

/** 货位分配效果统计 */
export interface SlottingEffectResult {
  warehouseId: number
  productCount: number
  slotCount: number
  objectives: Record<string, number>
  elapsedMillis: Record<string, number>
  improvementPercent: Record<string, number>
  greedyVsRandomPercent: number | null
  note?: string
}

/** 拣货路径效果统计 */
export interface PickingEffectResult {
  warehouseId: number
  pickPointCount: number
  labels: Record<string, string>
  distances: Record<string, number>
  elapsedMillis: Record<string, number>
  improvementVsSShapePercent: Record<string, number>
  note?: string
}

/** 频次贡献结构 */
export interface FrequencyContributionResult {
  topCount: number
  frequencySum: number
  productsFor70Percent: number | null
  note?: string
  items: Array<{
    productCode: string
    productName: string
    pickFreq: number
    abcClass: string
    cumulativeRatio: number
  }>
}
