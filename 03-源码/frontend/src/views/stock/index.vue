<script setup lang="ts">
/**
 * 库存管理。
 *
 * 三个视角回答库存问题：
 *  1. 概览：货位占用率、库存总量、商品种类，一眼看出仓库水位；
 *  2. 预警：缺货与临期两类风险，指导补货与处理；
 *  3. 明细：按货位到出入口距离升序，直观体现"近端货位放什么"。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { stockApi } from '@/api/stock'
import { useAppStore } from '@/stores/app'
import { ABC_COLORS } from '@/utils/constants'
import { formatDateTime, formatDistance, formatPercent, formatQuantity } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'
import LocationHeatMap from '@/components/warehouse/LocationHeatMap.vue'
import { locationApi, warehouseApi } from '@/api/warehouse'
import type { BaseLocation, BaseWarehouse } from '@/types/base'
import type { StockDetail, StockSummary, WarehouseStockSummary } from '@/types/business'

const appStore = useAppStore()

const loading = ref(false)
const tab = ref('detail')

const details = ref<StockDetail[]>([])
const summaryList = ref<StockSummary[]>([])
const overview = ref<WarehouseStockSummary | null>(null)
const alerts = ref<{
  nearExpiryDays: number
  lowStockCount: number
  nearExpiryCount: number
  lowStock: StockSummary[]
  nearExpiry: Array<Record<string, unknown>>
} | null>(null)

/** 用于热力图：把库存所在货位标记为占用 */
const locations = ref<BaseLocation[]>([])
const warehouse = ref<BaseWarehouse | null>(null)
const nearestFree = ref<BaseLocation[]>([])

const filters = reactive({
  keyword: '',
  abcClass: 'all' as string,
  onlyLow: false
})

/** 明细筛选项 */
const filteredDetails = computed(() =>
  details.value.filter((d) => {
    const kw = filters.keyword.trim().toLowerCase()
    if (kw) {
      const hit =
        d.productCode.toLowerCase().includes(kw) ||
        d.productName.toLowerCase().includes(kw) ||
        d.locationCode.toLowerCase().includes(kw)
      if (!hit) return false
    }
    if (filters.abcClass !== 'all' && d.abcClass !== filters.abcClass) return false
    return true
  })
)

/** 商品汇总筛选 */
const filteredSummary = computed(() =>
  summaryList.value.filter((s) => {
    const kw = filters.keyword.trim().toLowerCase()
    if (kw && !(s.productCode.toLowerCase().includes(kw) || s.productName.toLowerCase().includes(kw))) {
      return false
    }
    if (filters.abcClass !== 'all' && s.abcClass !== filters.abcClass) return false
    if (filters.onlyLow && s.lowStock !== 1) return false
    return true
  })
)

async function load() {
  loading.value = true
  try {
    const wid = appStore.currentWarehouseId
    const [d, s, o, a, locs, w] = await Promise.all([
      stockApi.list({ warehouseId: wid, limit: 5000 }).catch(() => []),
      stockApi.summary(wid).catch(() => []),
      stockApi.warehouseSummary(wid).catch(() => null),
      stockApi.alerts(wid).catch(() => null),
      locationApi.list({ warehouseId: wid, limit: 5000 }).catch(() => []),
      warehouseApi.detail(wid).catch(() => null)
    ])
    details.value = d
    summaryList.value = s
    overview.value = o
    alerts.value = a
    locations.value = locs
    warehouse.value = w
    nearestFree.value = await locationApi.nearestFree(wid, 10).catch(() => [])
  } finally {
    loading.value = false
  }
}

const statCards = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    {
      label: '库存总量',
      value: formatQuantity(o.totalQuantity),
      unit: '件',
      icon: 'Box',
      color: '#2563eb'
    },
    {
      label: '货位占用率',
      value: formatPercent(o.usageRate),
      unit: '',
      icon: 'Grid',
      color: '#10b981'
    },
    {
      label: '占用货位',
      value: `${o.locationUsed} / ${o.locationRows}`,
      unit: '',
      icon: 'Coordinate',
      color: '#f59e0b'
    },
    {
      label: '在库商品种类',
      value: String(o.productKinds),
      unit: '种',
      icon: 'Goods',
      color: '#8b5cf6'
    },
    {
      label: '缺货商品',
      value: String(alerts.value?.lowStockCount ?? 0),
      unit: '种',
      icon: 'WarningFilled',
      color: (alerts.value?.lowStockCount ?? 0) > 0 ? '#ef4444' : '#6b7280'
    },
    {
      label: '临期库存',
      value: String(alerts.value?.nearExpiryCount ?? 0),
      unit: '项',
      icon: 'Clock',
      color: (alerts.value?.nearExpiryCount ?? 0) > 0 ? '#f59e0b' : '#6b7280'
    }
  ]
})

onMounted(load)
</script>

<template>
  <div class="page-root stock-page">
    <!-- 概览 -->
    <div v-loading="loading" class="stock-page__stats">
      <div v-for="c in statCards" :key="c.label" class="stat-card">
        <div class="stat-card__icon" :style="{ background: c.color + '16', color: c.color }">
          <ElIcon :size="20"><component :is="c.icon" /></ElIcon>
        </div>
        <div>
          <div class="stat-card__label">{{ c.label }}</div>
          <div class="stat-card__value">
            <span class="num">{{ c.value }}</span>
            <span v-if="c.unit" class="stat-card__unit">{{ c.unit }}</span>
          </div>
        </div>
      </div>
      <div v-if="!statCards.length" class="stock-page__stats-empty">
        <EmptyState description="暂无库存数据，请先通过入库管理补充库存" />
      </div>
    </div>

    <!-- 工具条 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <ElInput
          v-model="filters.keyword"
          placeholder="搜索商品编码、名称或货位"
          clearable
          style="width: 240px"
          :prefix-icon="'Search'"
        />
        <ElSelect v-model="filters.abcClass" placeholder="ABC 分类" style="width: 140px">
          <ElOption label="全部 ABC" value="all" />
          <ElOption label="A 类" value="A" />
          <ElOption label="B 类" value="B" />
          <ElOption label="C 类" value="C" />
        </ElSelect>
        <ElCheckbox v-model="filters.onlyLow">仅看缺货商品</ElCheckbox>
        <ElButton :icon="'Refresh'" :loading="loading" @click="load">刷新</ElButton>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="wms-card">
      <ElTabs v-model="tab">
        <!-- 库存明细 -->
        <ElTabPane label="库存明细" name="detail">
          <ElTable :data="filteredDetails" size="small" stripe height="420">
            <ElTableColumn prop="locationCode" label="货位" width="100" fixed>
              <template #default="{ row }">
                <ElTag type="primary" size="small" effect="plain">{{ row.locationCode }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="坐标" width="90" align="center">
              <template #default="{ row }">
                <span class="num">({{ row.aisleNo }}, {{ row.rowNo }})</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="距出入口" width="106" align="right" sortable :sort-method="(a: StockDetail, b: StockDetail) => a.distance - b.distance">
              <template #default="{ row }">
                <span class="num stock-page__distance">{{ formatDistance(row.distance) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="productCode" label="商品编码" width="106" />
            <ElTableColumn prop="productName" label="商品名称" min-width="140" show-overflow-tooltip />
            <ElTableColumn label="ABC" width="74" align="center">
              <template #default="{ row }">
                <ElTag :color="ABC_COLORS[row.abcClass]" effect="dark" size="small">
                  {{ row.abcClass }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="库存数量" width="106" align="right">
              <template #default="{ row }">
                <span class="num stock-page__qty">{{ formatQuantity(row.quantity) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="batchNo" label="批次" width="120">
              <template #default="{ row }">{{ row.batchNo || '—' }}</template>
            </ElTableColumn>
            <ElTableColumn label="更新时间" min-width="150">
              <template #default="{ row }">
                <span class="stock-page__time">{{ formatDateTime(row.updateTime) }}</span>
              </template>
            </ElTableColumn>

            <template #empty>
              <EmptyState description="暂无库存明细" />
            </template>
          </ElTable>
        </ElTabPane>

        <!-- 商品汇总 -->
        <ElTabPane label="商品汇总" name="summary">
          <ElTable :data="filteredSummary" size="small" stripe height="420">
            <ElTableColumn prop="productCode" label="商品编码" width="110" fixed />
            <ElTableColumn prop="productName" label="商品名称" min-width="150" show-overflow-tooltip />
            <ElTableColumn label="ABC" width="76" align="center">
              <template #default="{ row }">
                <ElTag :color="ABC_COLORS[row.abcClass]" effect="dark" size="small">
                  {{ row.abcClass }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="库存总量" width="110" align="right" sortable :sort-method="(a: StockSummary, b: StockSummary) => a.totalQuantity - b.totalQuantity">
              <template #default="{ row }">
                <span class="num stock-page__qty">{{ formatQuantity(row.totalQuantity) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="安全库存" width="106" align="right">
              <template #default="{ row }">
                <span class="num">{{ formatQuantity(row.safetyStock) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="locationCount" label="占用货位" width="100" align="right" />
            <ElTableColumn label="库存水位" min-width="150">
              <template #default="{ row }">
                <ElProgress
                  v-if="row.safetyStock > 0"
                  :percentage="Math.min(100, Math.round((row.totalQuantity / row.safetyStock) * 100))"
                  :status="row.lowStock === 1 ? 'exception' : 'success'"
                  :stroke-width="10"
                />
                <span v-else class="stock-page__muted">未设安全库存</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" width="96" align="center">
              <template #default="{ row }">
                <ElTag v-if="row.totalQuantity <= 0" type="danger" size="small">缺货</ElTag>
                <ElTag v-else-if="row.lowStock === 1" type="warning" size="small">偏低</ElTag>
                <ElTag v-else type="success" size="small">正常</ElTag>
              </template>
            </ElTableColumn>

            <template #empty>
              <EmptyState description="暂无商品库存汇总" />
            </template>
          </ElTable>
        </ElTabPane>

        <!-- 库存预警 -->
        <ElTabPane name="alert">
          <template #label>
            <span>库存预警</span>
            <ElBadge
              v-if="(alerts?.lowStockCount ?? 0) + (alerts?.nearExpiryCount ?? 0) > 0"
              :value="(alerts?.lowStockCount ?? 0) + (alerts?.nearExpiryCount ?? 0)"
              type="danger"
              class="stock-page__badge"
            />
          </template>

          <div class="stock-page__alert-section">
            <div class="stock-page__alert-title">
              <ElIcon class="is-danger"><WarningFilled /></ElIcon>
              缺货预警（{{ alerts?.lowStockCount ?? 0 }} 种）
              <span class="stock-page__alert-note">库存低于安全库存，建议及时补货</span>
            </div>
            <ElTable
              v-if="alerts?.lowStock?.length"
              :data="alerts.lowStock"
              size="small"
              stripe
              max-height="240"
            >
              <ElTableColumn prop="productCode" label="商品编码" width="110" />
              <ElTableColumn prop="productName" label="商品名称" min-width="150" show-overflow-tooltip />
              <ElTableColumn label="ABC" width="76" align="center">
                <template #default="{ row }">
                  <ElTag :color="ABC_COLORS[row.abcClass]" effect="dark" size="small">
                    {{ row.abcClass }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn label="当前库存" width="110" align="right">
                <template #default="{ row }">
                  <span class="num">{{ formatQuantity(row.totalQuantity) }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="安全库存" width="110" align="right">
                <template #default="{ row }">
                  <span class="num">{{ formatQuantity(row.safetyStock) }}</span>
                </template>
              </ElTableColumn>
            </ElTable>
            <EmptyState v-else description="无缺货商品" />
          </div>

          <div class="stock-page__alert-section">
            <div class="stock-page__alert-title">
              <ElIcon class="is-warning"><Clock /></ElIcon>
              临期预警（{{ alerts?.nearExpiryCount ?? 0 }} 项）
              <span class="stock-page__alert-note">
                有效期在 {{ alerts?.nearExpiryDays ?? 30 }} 天内
              </span>
            </div>
            <ElTable
              v-if="alerts?.nearExpiry?.length"
              :data="alerts.nearExpiry"
              size="small"
              stripe
              max-height="240"
            >
              <ElTableColumn prop="productCode" label="商品编码" width="110" />
              <ElTableColumn prop="productName" label="商品名称" min-width="140" show-overflow-tooltip />
              <ElTableColumn prop="locationCode" label="货位" width="96" align="center" />
              <ElTableColumn label="数量" width="96" align="right">
                <template #default="{ row }">
                  <span class="num">{{ formatQuantity(row.quantity) }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="batchNo" label="批次" width="110" />
              <ElTableColumn prop="expiryDate" label="有效期至" width="120" />
              <ElTableColumn label="剩余天数" min-width="100" align="right">
                <template #default="{ row }">
                  <ElTag :type="Number(row.daysLeft) <= 7 ? 'danger' : 'warning'" size="small">
                    {{ row.daysLeft }} 天
                  </ElTag>
                </template>
              </ElTableColumn>
            </ElTable>
            <EmptyState v-else description="无临期库存（当前演示数据的批次未设置有效期）" />
          </div>
        </ElTabPane>

        <!-- 货位占用分布 -->
        <ElTabPane label="货位占用分布" name="heatmap">
          <div v-if="warehouse && locations.length" class="stock-page__heatmap">
            <div class="stock-page__nearest">
              <span class="stock-page__nearest-label">最近的空闲货位（算法优先分配）：</span>
              <ElTag
                v-for="loc in nearestFree.slice(0, 6)"
                :key="loc.id"
                size="small"
                effect="plain"
                type="success"
                class="stock-page__nearest-tag"
              >
                {{ loc.locationCode }}（{{ formatDistance(loc.distance) }}）
              </ElTag>
            </div>
            <LocationHeatMap
              :locations="locations"
              :aisles="warehouse.aisleCount"
              :rows="warehouse.rowCount"
            />
          </div>
          <EmptyState v-else description="未获取到仓库布局或货位数据" />
        </ElTabPane>
      </ElTabs>
    </div>
  </div>
</template>

<style scoped lang="scss">
.stock-page {
  &__stats {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 12px;
    min-height: 84px;
  }

  &__stats-empty {
    grid-column: 1 / -1;
    background: #fff;
    border-radius: var(--wms-card-radius);
  }

  &__distance {
    color: var(--el-color-primary);
    font-weight: 500;
  }

  &__qty {
    font-weight: 600;
    color: #1f2937;
  }

  &__time {
    font-size: 12px;
    color: #909399;
  }

  &__muted {
    color: #c0c4cc;
    font-size: 12px;
  }

  &__badge {
    margin-left: 6px;
  }

  &__alert-section {
    & + & {
      margin-top: 20px;
      padding-top: 16px;
      border-top: 1px dashed #ebeef5;
    }
  }

  &__alert-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 10px;
    font-size: 13px;
    font-weight: 600;
    color: #4b5563;

    .is-danger {
      color: var(--el-color-danger);
    }

    .is-warning {
      color: var(--el-color-warning);
    }
  }

  &__alert-note {
    font-size: 11px;
    font-weight: 400;
    color: #a8abb2;
  }

  &__heatmap {
    padding-top: 4px;
  }

  &__nearest {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 12px;
    padding: 9px 12px;
    border-radius: 6px;
    background: #f0fdf4;
    font-size: 12px;
    color: #475569;
  }

  &__nearest-label {
    font-weight: 600;
  }

  &__nearest-tag {
    font-family: 'Consolas', monospace;
  }
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  background: #fff;
  border-radius: var(--wms-card-radius);
  box-shadow: var(--wms-shadow-sm);

  &__icon {
    flex-shrink: 0;
    width: 38px;
    height: 38px;
    border-radius: 9px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__label {
    font-size: 12px;
    color: #909399;
  }

  &__value {
    margin-top: 2px;
    font-size: 18px;
    font-weight: 600;
    color: #1f2937;
    line-height: 1.25;
  }

  &__unit {
    margin-left: 3px;
    font-size: 11px;
    font-weight: 400;
    color: #909399;
  }
}

@media (max-width: 1400px) {
  .stock-page__stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .stock-page__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
