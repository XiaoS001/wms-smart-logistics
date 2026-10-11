<script setup lang="ts">
/**
 * 货位优化。
 *
 * 本页面是课题核心能力的展示窗口，按「预览 → 对比 → 保存 → 应用」四步组织：
 *   1. 选定策略后生成方案预览，给出当前实际布局与优化后布局的目标函数值；
 *   2. 用对比图与搬迁清单说明"哪些商品要从哪里搬到哪里、能省多少距离"；
 *   3. 保存方案形成可追溯的优化记录；
 *   4. 应用方案会把库存记录真正搬到目标货位。
 *
 * 目标函数口径：加权拣货行走距离 F = Σ(出库频次 × 货位到出入口距离)，单位「米·次」。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { slottingApi } from '@/api/slotting'
import { warehouseApi } from '@/api/warehouse'
import { useAppStore } from '@/stores/app'
import { ABC_COLORS, SLOTTING_STRATEGIES } from '@/utils/constants'
import { formatDistance, formatQuantity } from '@/utils/format'
import ChartBox from '@/components/ChartBox.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { BaseWarehouse } from '@/types/base'
import type { SlottingPlanPreview } from '@/types/business'

const appStore = useAppStore()

const loading = ref(false)
const saving = ref(false)
const applying = ref(false)
const strategy = ref('frequency-greedy')
const previewData = ref<SlottingPlanPreview | null>(null)
const warehouse = ref<BaseWarehouse | null>(null)

/** 指标卡 */
const metrics = computed(() => {
  const s = previewData.value?.summary
  if (!s) return []
  return [
    {
      label: '当前加权行走距离',
      value: formatQuantity(s.currentObjective),
      unit: '米·次',
      color: '#ef4444',
      hint: `平均每次拣货 ${s.currentAverageDistance} 米`
    },
    {
      label: '优化后加权行走距离',
      value: formatQuantity(s.optimizedObjective),
      unit: '米·次',
      color: '#10b981',
      hint: `平均每次拣货 ${s.optimizedAverageDistance} 米`
    },
    {
      label: '降幅',
      value: s.improvementPercent,
      unit: '%',
      color: '#2563eb',
      hint: '优化后相对当前实际布局'
    },
    {
      label: '需搬迁商品',
      value: s.movesNeeded,
      unit: '个',
      color: '#f59e0b',
      hint: `已在正确货位 ${s.alreadyInPlace} 个 · 无库存 ${s.productsWithoutStock} 个`
    }
  ]
})

/** 目标函数对比图：当前 vs 优化后 */
const compareOption = computed<EChartsOption>(() => {
  const s = previewData.value?.summary
  if (!s) return {}
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 30, bottom: 4, top: 20, containLabel: true },
    xAxis: {
      type: 'value',
      name: '米·次',
      nameTextStyle: { color: '#909399', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: ['优化后', '当前布局'],
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#606266', fontSize: 12 }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 26,
        data: [
          { value: s.optimizedObjective, itemStyle: { color: '#10b981', borderRadius: [0, 4, 4, 0] } },
          { value: s.currentObjective, itemStyle: { color: '#ef4444', borderRadius: [0, 4, 4, 0] } }
        ],
        label: {
          show: true,
          position: 'right',
          // ECharts 的回调参数类型较宽（value 可能是数组或对象），
          // 这里不标注参数类型，交给 ECharts 自身的签名推断。
          formatter: (p: { value?: unknown }) => formatQuantity(Number(p.value)),
          color: '#606266',
          fontSize: 12
        }
      }
    ]
  }
})

/** 搬迁清单按节省距离降序：最值得搬的排在最前 */
const moves = computed(() =>
  [...(previewData.value?.moves ?? [])].sort(
    (a, b) => (b.distanceSaving ?? 0) - (a.distanceSaving ?? 0)
  )
)

/** 分配明细按目标货位距离升序 */
const assignments = computed(() =>
  [...(previewData.value?.assignments ?? [])].sort(
    (a, b) => (a.targetDistance ?? 0) - (b.targetDistance ?? 0)
  )
)

async function loadPreview() {
  loading.value = true
  try {
    previewData.value = await slottingApi.preview(
      appStore.currentWarehouseId,
      strategy.value
    )
  } catch {
    previewData.value = null
  } finally {
    loading.value = false
  }
}

async function savePlan() {
  saving.value = true
  try {
    const res = await slottingApi.savePlan(
      appStore.currentWarehouseId,
      strategy.value,
      20261011,
      `由货位优化页面生成（策略：${SLOTTING_STRATEGIES.find((s) => s.value === strategy.value)?.label}）`
    )
    ElMessage.success(`优化方案 ${res.planNo} 已保存，可在「优化方案」页查看与应用`)
    previewData.value = res
  } finally {
    saving.value = false
  }
}

async function applyPlan() {
  const planId = previewData.value?.planId
  if (!planId) {
    ElMessage.warning('请先保存方案，再执行应用')
    return
  }
  try {
    await ElMessageBox.confirm(
      `应用方案会把 ${previewData.value?.summary.movesNeeded ?? 0} 个商品的库存记录搬迁到优化后的货位。\n` +
        '整个过程在同一事务中完成，货位占用状态会同步重算。是否继续？',
      '应用优化方案',
      { type: 'warning', confirmButtonText: '确认应用', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  applying.value = true
  try {
    const res = await slottingApi.applyPlan(planId)
    ElMessage.success(
      `方案已应用：搬迁 ${res.movedRecords} 条库存记录，当前占用 ${res.occupiedLocations} 个货位`
    )
    await loadPreview()
  } finally {
    applying.value = false
  }
}

onMounted(async () => {
  try {
    warehouse.value = await warehouseApi.detail(appStore.currentWarehouseId)
  } catch {
    warehouse.value = null
  }
  await loadPreview()
})
</script>

<template>
  <div class="page-root slotting-page">
    <!-- 操作区 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <span class="slotting-page__field-label">优化策略</span>
        <ElSelect v-model="strategy" style="width: 220px" @change="loadPreview">
          <ElOption v-for="s in SLOTTING_STRATEGIES" :key="s.value" :value="s.value" :label="s.label">
            <div class="slotting-page__option">
              <span>{{ s.label }}</span>
              <span class="slotting-page__option-desc">{{ s.desc }}</span>
            </div>
          </ElOption>
        </ElSelect>

        <ElButton :icon="'Refresh'" :loading="loading" @click="loadPreview">重新计算</ElButton>

        <div class="wms-toolbar__right">
          <ElButton :icon="'DocumentAdd'" :loading="saving" @click="savePlan">保存方案</ElButton>
          <ElButton
            type="primary"
            :icon="'Check'"
            :loading="applying"
            :disabled="!previewData?.planId"
            @click="applyPlan"
          >
            应用方案
          </ElButton>
        </div>
      </div>

      <ElAlert type="info" :closable="false" show-icon class="slotting-page__intro">
        <template #title>目标函数：加权拣货行走距离 F = Σ(出库频次 × 货位到出入口距离)</template>
        <div class="slotting-page__intro-body">
          「当前加权行走距离」按库存的<b>实际所在货位</b>计算，「优化后」按算法求解结果计算，
          两者口径一致因此可直接比较。降幅即算法带来的实际改善空间。
          需要先保存方案才能执行应用。
        </div>
      </ElAlert>
    </div>

    <!-- 指标 -->
    <div v-loading="loading" class="slotting-page__metrics">
      <div v-for="m in metrics" :key="m.label" class="metric-card">
        <div class="metric-card__label">{{ m.label }}</div>
        <div class="metric-card__value" :style="{ color: m.color }">
          <span class="num">{{ m.value }}</span>
          <span class="metric-card__unit">{{ m.unit }}</span>
        </div>
        <div class="metric-card__hint">{{ m.hint }}</div>
      </div>
      <div v-if="!metrics.length" class="slotting-page__metrics-empty">
        <EmptyState description="暂无预览数据，请确认仓库已有商品与货位" />
      </div>
    </div>

    <!-- 对比图 -->
    <ElRow v-if="previewData" :gutter="12">
      <ElCol :xs="24" :lg="10">
        <div class="wms-card">
          <div class="wms-card__title">优化前后目标函数对比</div>
          <ChartBox :option="compareOption" height="220px" />
          <div class="slotting-page__chart-note">
            最远货位 {{ formatDistance(warehouse ? Number(warehouse.aisleGap) * warehouse.aisleCount + Number(warehouse.rowGap) * warehouse.rowCount : 0) }}
            · 共 {{ previewData.summary.locationCount }} 个货位 ·
            参与优化商品 {{ previewData.summary.productCount }} 种
          </div>
        </div>
      </ElCol>

      <ElCol :xs="24" :lg="14">
        <div class="wms-card">
          <div class="wms-card__title">
            搬迁清单
            <ElTag size="small" type="warning" effect="plain">
              按节省距离降序 · 共 {{ moves.length }} 项
            </ElTag>
          </div>

          <ElTable v-if="moves.length" :data="moves" size="small" stripe height="260">
            <ElTableColumn label="商品" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="slotting-page__product">{{ row.productCode }}</span>
                <span v-if="row.pickFreq" class="slotting-page__freq">频次 {{ row.pickFreq }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="从" width="130" align="center">
              <template #default="{ row }">
                <span class="slotting-page__loc-from">{{ row.fromLocationCode || '未在库' }}</span>
                <span v-if="row.fromDistance" class="slotting-page__dist">({{ row.fromDistance }}m)</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="到" width="130" align="center">
              <template #default="{ row }">
                <span class="slotting-page__loc-to">{{ row.toLocationCode }}</span>
                <span class="slotting-page__dist">({{ row.toDistance }}m)</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="节省距离" min-width="100" align="right">
              <template #default="{ row }">
                <span v-if="row.distanceSaving > 0" class="slotting-page__saving">
                  −{{ row.distanceSaving }} 米
                </span>
                <span v-else class="slotting-page__muted">—</span>
              </template>
            </ElTableColumn>
          </ElTable>

          <EmptyState
            v-else
            description="当前布局已接近最优，无需搬迁；或仓库尚无库存"
          />
        </div>
      </ElCol>
    </ElRow>

    <!-- 分配明细 -->
    <div v-if="previewData" class="wms-card">
      <div class="wms-card__title">
        商品到货位的分配方案
        <ElTag size="small" type="info" effect="plain">按目标货位距离升序</ElTag>
      </div>

      <ElTable :data="assignments" size="small" stripe height="360">
        <ElTableColumn prop="productCode" label="商品编码" width="106" fixed />
        <ElTableColumn prop="productName" label="商品名称" min-width="140" show-overflow-tooltip />
        <ElTableColumn label="出库频次" width="100" align="right" sortable :sort-method="(a: any, b: any) => (a.pickFreq ?? 0) - (b.pickFreq ?? 0)">
          <template #default="{ row }">
            <span class="num slotting-page__freq-cell">{{ formatQuantity(row.pickFreq) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="ABC" width="76" align="center">
          <template #default="{ row }">
            <ElTag :color="ABC_COLORS[row.abcClass]" effect="dark" size="small">
              {{ row.abcClass }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="当前货位" width="150" align="center">
          <template #default="{ row }">
            <span v-if="row.currentLocationCode" class="slotting-page__loc-from">
              {{ row.currentLocationCode }}
              <span class="slotting-page__dist">({{ row.currentDistance }}m)</span>
            </span>
            <span v-else class="slotting-page__muted">无库存</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="目标货位" width="150" align="center">
          <template #default="{ row }">
            <ElTag type="success" size="small" effect="plain">
              {{ row.targetLocationCode }}
            </ElTag>
            <span class="slotting-page__dist">({{ row.targetDistance }}m)</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="是否已就位" min-width="110" align="center">
          <template #default="{ row }">
            <ElTag
              v-if="row.currentLocationId && row.currentLocationId === row.targetLocationId"
              type="success"
              size="small"
            >
              已就位
            </ElTag>
            <ElTag v-else-if="row.currentLocationId" type="warning" size="small">需搬迁</ElTag>
            <span v-else class="slotting-page__muted">无库存</span>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </div>
</template>

<style scoped lang="scss">
.slotting-page {
  &__field-label {
    font-size: 13px;
    color: #606266;
  }

  &__option {
    display: flex;
    align-items: baseline;
    gap: 8px;
  }

  &__option-desc {
    font-size: 11px;
    color: #a8abb2;
  }

  &__intro {
    margin-top: 4px;
  }

  &__intro-body {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.75;

    b {
      color: var(--el-color-primary);
    }
  }

  &__metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    margin: 12px 0;
    min-height: 96px;
  }

  &__metrics-empty {
    grid-column: 1 / -1;
    background: #fff;
    border-radius: var(--wms-card-radius);
  }

  &__chart-note {
    margin-top: 8px;
    font-size: 11px;
    color: #a8abb2;
    text-align: center;
  }

  &__product {
    font-family: 'Consolas', monospace;
    font-weight: 600;
    color: #303133;
  }

  &__freq {
    margin-left: 6px;
    font-size: 11px;
    color: #a8abb2;
  }

  &__freq-cell {
    font-weight: 600;
    color: #1f2937;
  }

  &__loc-from {
    font-family: 'Consolas', monospace;
    color: #909399;
    font-size: 12px;
  }

  &__loc-to {
    font-family: 'Consolas', monospace;
    color: #10b981;
    font-weight: 600;
    font-size: 12px;
  }

  &__dist {
    margin-left: 3px;
    font-size: 11px;
    color: #a8abb2;
  }

  &__saving {
    font-weight: 600;
    color: #10b981;
  }

  &__muted {
    color: #c0c4cc;
    font-size: 12px;
  }
}

.metric-card {
  padding: 16px;
  background: #fff;
  border-radius: var(--wms-card-radius);
  box-shadow: var(--wms-shadow-sm);

  &__label {
    font-size: 12px;
    color: #909399;
  }

  &__value {
    margin-top: 6px;
    font-size: 26px;
    font-weight: 700;
    line-height: 1.2;
  }

  &__unit {
    margin-left: 4px;
    font-size: 12px;
    font-weight: 400;
    color: #909399;
  }

  &__hint {
    margin-top: 4px;
    font-size: 11px;
    color: #a8abb2;
  }
}

@media (max-width: 1200px) {
  .slotting-page__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
