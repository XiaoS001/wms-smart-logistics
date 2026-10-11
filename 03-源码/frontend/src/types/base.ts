/**
 * 基础数据与业务单据类型定义。
 *
 * 字段与后端实体、接口返回保持一致；
 * 金额与数量类字段统一用 number（后端返回的是 JSON 数值）。
 */

/* ============================ 仓库与库位 ============================ */

/** 仓库 */
export interface BaseWarehouse {
  id: number
  warehouseCode: string
  warehouseName: string
  /** 巷道数 X */
  aisleCount: number
  /** 纵深数 Y */
  rowCount: number
  /** 相邻巷道间距（米） */
  aisleGap: number
  /** 相邻货位纵深间距（米） */
  rowGap: number
  status: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 库区 */
export interface BaseZone {
  id: number
  warehouseId: number
  zoneCode: string
  zoneName: string
  /** A / B / C / NORMAL */
  zoneType: string
  status: number
}

/** 货位状态常量 */
export const LOCATION_FREE = 1
export const LOCATION_USED = 2

/** 货位 */
export interface BaseLocation {
  id: number
  warehouseId: number
  zoneId: number
  locationCode: string
  /** 巷道号 x */
  aisleNo: number
  /** 纵深号 y */
  rowNo: number
  /** 到出入口距离（米） */
  distance: number
  capacity: number
  /** 1 空闲 / 2 占用 / 0 停用 */
  status: number
  createTime?: string
}

/** 库区货位分布统计 */
export interface LocationSummary {
  zoneId: number
  locationCount: number
  minDistance: number
  maxDistance: number
}

/** 批量生成货位请求 */
export interface GenerateLocationsRequest {
  aisleCount?: number
  rowCount?: number
  /** A 区最大距离（米） */
  aisleDistance?: number
  /** B 区最大距离（米） */
  bDistance?: number
  capacity?: number
  overwrite?: boolean
}

/** 批量生成货位结果 */
export interface GenerateLocationsResult {
  warehouseId: number
  created: number
  skipped: number
  removed: number
  total: number
  zoneCounts: Record<string, number>
  minDistance: number
  maxDistance: number
}

/* ============================ 商品与分类 ============================ */

/** 商品分类树节点 */
export interface CategoryTreeNode {
  id: number
  parentId: number
  categoryName: string
  sort: number
  productCount: number
  children: CategoryTreeNode[]
}

/** 商品分类（平铺） */
export interface BaseCategory {
  id: number
  parentId: number
  categoryName: string
  sort: number
}

/** 商品 */
export interface BaseProduct {
  id: number
  productCode: string
  productName: string
  categoryId: number
  spec?: string
  unit?: string
  weight?: number
  volume?: number
  /** 累计出库频次（货位分配算法的输入） */
  pickFreq: number
  /** ABC 分类 */
  abcClass: string
  safetyStock: number
  status: number
  createTime?: string
  updateTime?: string
}

/** ABC 分类计算结果 */
export interface AbcClassifyResult {
  totalProducts: number
  totalFrequency: number
  updatedCount: number
  classCounts: Record<string, number>
  classFrequencies: Record<string, number>
  classFrequencyRatio: Record<string, number>
}
