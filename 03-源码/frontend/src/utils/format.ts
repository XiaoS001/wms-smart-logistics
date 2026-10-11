import dayjs from 'dayjs'

/**
 * 通用格式化工具。
 */

/** 格式化日期时间 */
export function formatDateTime(value?: string | number | Date, pattern = 'YYYY-MM-DD HH:mm:ss') {
  if (!value) return '-'
  return dayjs(value).format(pattern)
}

/** 格式化日期 */
export function formatDate(value?: string | number | Date) {
  return formatDateTime(value, 'YYYY-MM-DD')
}

/** 格式化数量：去掉无意义的小数尾零 */
export function formatQuantity(value?: number | string | null) {
  if (value === null || value === undefined || value === '') return '-'
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return Number.isInteger(num) ? String(num) : num.toFixed(2).replace(/\.?0+$/, '')
}

/** 格式化距离，保留两位小数 */
export function formatDistance(value?: number | null, unit = '米') {
  if (value === null || value === undefined) return '-'
  return `${Number(value).toFixed(2)} ${unit}`
}

/** 格式化百分比 */
export function formatPercent(value?: number | null, digits = 2) {
  if (value === null || value === undefined) return '-'
  return `${Number(value).toFixed(digits)}%`
}

/** 千分位分隔 */
export function formatThousands(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return Number(value).toLocaleString('zh-CN')
}

/** 字节数转可读文本（用于展示文件大小） */
export function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}
