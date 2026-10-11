<script setup lang="ts">
/**
 * 算法效果统计。
 *
 * 本页面把两个算法的优化效果集中呈现，是论文"实验结果与分析"一节的系统侧证据：
 *  1. 货位分配：四种策略在同一算例下的目标函数值对比，以及相对随机分配的降幅；
 *  2. 拣货路径：四种策略在同一拣货点集合下的路径长度对比，以及相对 S 型基线的降幅；
 *  3. 真实单据表现：按出库单实际使用的算法统计单据数与平均路径长度。
 *
 * 所有数据都来自后端用系统内真实数据实时计算的结果，与算法实验脚本口径一致。
 */
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { reportApi } from '@/api/report'
import { useAppStore } from '@/stores/app'
import { SLOTTING_STRATEGIES, PICKING_STRATEGIES } from '@/utils/constants'
import { formatQuantity } from '@/utils/format'
import ChartBox from '@/components/ChartBox.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { PickingEffectResult, SlottingEffectResult } from '@/types/business'

const appStore = useAppStore()

const loading = ref(false)
const slotting = ref<SlottingEffectResult | null>(null)
const picking = ref<PickingEffectResult | null>(null)
const routeStats = ref<Array<{ routeAlgo: string; orderCount: number; avgDistance: number }>>([])
/** 拣货效果可能因仓库无库存而失败，单独记录以便给出针对性提示 */
const pickingError = ref('')

/** 策略编码 → 中文名 */
function slottingLabel(code: string) {
  return SLOTTING_STRATEGIES.find((s) => s.value === code)?.label ?? code
}
function pickingLabel(code: string) {
  return PICKING_STRATEGIES.find((s) => s.value === code)?.label ?? code
}

/** 货位分配：目标函数对比（越低越好） */
const slottingOption = computed<EChartsOption>(() => {
  const objectives = slotting.value?.objectives ?? {}
  const codes = Object.keys(objectives)
  if (!codes.length) return {}
  const values = codes.map((c) => objectives[c])
  const min = Math.min(...values)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 40, bottom: 4, top: 16, containLabel: true },
    xAxis: {
      type: 'value',
      name: '米·次',
      nameTextStyle: { color: '#909399', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: codes.map(slottingLabel),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#606266', fontSize: 12 }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 24,
        data: values.map((v) => ({
          value: v,
          // 最优策略高亮为绿色，其余为浅蓝
          itemStyle: { color: v === min ? '#10b981' : '#93b4f5', borderRadius: [0, 4, 4, 0] }
        })),
        label: {
          show: true,
          position: 'right',
          formatter: (p: { value?: unknown }) => formatQuantity(Number(p.value)),
          color: '#606266',
          fontSize: 11
        }
      }
    ]
  }
})

/** 货位分配：相对随机分配的降幅 */
const slottingImproveOption = computed<EChartsOption>(() => {
  const improve = slotting.value?.improvementPercent ?? {}
  const codes = Object.keys(improve)
  if (!codes.length) return {}
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (v: unknown) => `${v}%`
    },
    grid: { left: 8, right: 40, bottom: 4, top: 16, containLabel: true },
    xAxis: {
      type: 'value',
      name: '降幅 %',
      nameTextStyle: { color: '#909399', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: codes.map(slottingLabel),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#606266', fontSize: 12 }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 24,
        data: codes.map((c) => improve[c]),
        itemStyle: { color: '#2563eb', borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', formatter: '{c}%', color: '#606266', fontSize: 11 }
      }
    ]
  }
})

/** 拣货路径：路径长度对比（越低越好） */
const pickingOption = computed<EChartsOption>(() => {
  const distances = picking.value?.distances ?? {}
  const codes = Object.keys(distances)
  if (!codes.length) return {}
  const values = codes.map((c) => distances[c])
  const min = Math.min(...values)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 46, bottom: 4, top: 16, containLabel: true },
    xAxis: {
      type: 'value',
      name: '米',
      nameTextStyle: { color: '#909399', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: codes.map(pickingLabel),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#606266', fontSize: 12 }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 24,
        data: values.map((v) => ({
          value: v,
          itemStyle: { color: v === min ? '#10b981' : '#93b4f5', borderRadius: [0, 4, 4, 0] }
        })),
        label: {
          show: true,
          position: 'right',
          formatter: '{c} 米',
          color: '#606266',
          fontSize: 11
        }
      }
    ]
  }
})

/** 拣货路径：相对 S 型基线的降幅（S 型自身不参与比较） */
const pickingImproveOption = computed<EChartsOption>(() => {
  const improve = picking.value?.improvementVsSShapePercent ?? {}
  const codes = Object.keys(improve).filter((c) => c !== 's-shape')
  if (!codes.length) return {}
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (v: unknown) => `${v}%`
    },
    grid: { left: 8, right: 40, bottom: 4, top: 16, containLabel: true },
    xAxis: {
      type: 'value',
      name: '降幅 %',
      nameTextStyle: { color: '#909399', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: codes.map(pickingLabel),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#606266', fontSize: 12 }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 24,
        data: codes.map((c) => improve[c]),
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          // 有改善的策略用绿色，无改善（0 或负）用灰色，避免误读
          color: (p: { dataIndex: number }) =>
            Number(improve[codes[p.dataIndex]]) > 0 ? '#10b981' : '#c0c4cc'
        },
        label: { show: true, position: 'right', formatter: '{c}%', color: '#606266', fontSize: 11 }
      }
    ]
  }
})

/** 关键结论卡：把最重要的数字直接摆出来 */
const conclusions = computed(() => {
  const list: Array<{ label: string; value: string; note: string; color: string }> = []

  if (slotting.value) {
    const s = slotting.value
    const best = Object.entries(s.objectives).reduce((a, b) => (b[1] < a[1] ? b : a))
    list.push({
      label: '货位分配最优策略',
      value: slottingLabel(best[0]),
      note: `目标函数 ${formatQuantity(best[1])} 米·次`,
      color: '#2563eb'
    })
    if (s.greedyVsRandomPercent !== null && s.greedyVsRandomPercent !== undefined) {
      list.push({
        label: '相对随机分配降幅',
        value: `${s.greedyVsRandomPercent}%`,
        note: '任务书指标 ≥ 15%',
        color: '#10b981'
      })
    }
    list.push({
      label: '参与优化规模',
      value: `${s.productCount} / ${s.slotCount}`,
      note: `商品数 / 货位数（仓库 ${s.warehouseId}）`,
      color: '#8b5cf6'
    })
  }

  if (picking.value) {
    const p = picking.value
    const best = Object.entries(p.distances).reduce((a, b) => (b[1] < a[1] ? b : a))
    list.push({
      label: '拣货路径最优策略',
      value: pickingLabel(best[0]),
      note: `路径 ${best[1]} 米 · ${p.pickPointCount} 个拣货点`,
      color: '#2563eb'
    })
    list.push({
      label: '相对 S 型降幅',
      value: `${p.improvementVsSShapePercent?.[best[0]] ?? 0}%`,
      note: '任务书指标 ≥ 10%',
      color: '#10b981'
    })
  }

  return list
})

/** 表格数据：显式构造，避免在模板里写复杂表达式 */
const slottingRows = computed(() => {
  const s = slotting.value
  if (!s) return []
  return Object.keys(s.objectives).map((k) => ({
    strategy: k,
    label: slottingLabel(k),
    objective: s.objectives[k],
    improvement: s.improvementPercent?.[k],
    elapsed: s.elapsedMillis?.[k]
  }))
})

const pickingRows = computed(() => {
  const p = picking.value
  if (!p) return []
  return Object.keys(p.distances).map((k) => ({
    strategy: k,
    label: p.labels?.[k] ?? pickingLabel(k),
    distance: p.distances[k],
    improvement: p.improvementVsSShapePercent?.[k],
    elapsed: p.elapsedMillis?.[k]
  }))
})

async function load() {
  loading.value = true
  pickingError.value = ''
  const wid = appStore.currentWarehouseId
  try {
    await Promise.all([
      reportApi
        .slottingEffect(wid)
        .then((d) => (slotting.value = d))
        .catch(() => (slotting.value = null)),
      reportApi
        .pickingEffect(wid)
        .then((d) => (picking.value = d))
        .catch(() => {
          picking.value = null
          pickingError.value = '该仓库暂无库存，无法统计拣货路径效果。请先通过入库管理补充库存。'
        }),
      reportApi
        .routeAlgorithmStatistics()
        .then((d) => (routeStats.value = d ?? []))
        .catch(() => (routeStats.value = []))
    ])
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-root algo-report">
    <!-- 工具条 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <span class="algo-report__label">统计口径</span>
        <ElTag type="info" effect="plain" size="small">使用系统内真实数据实时计算</ElTag>
        <ElButton :icon="'Refresh'" :loading="loading" @click="load">重新计算</ElButton>
        <div class="wms-toolbar__right">
          <ElButton :icon="'Aim'" @click="$router.push('/algo/slotting')">前往货位优化</ElButton>
        </div>
      </div>

      <ElAlert type="info" :closable="false" show-icon class="algo-report__intro">
        <template #title>两个算法共用同一套空间模型</template>
        <div class="algo-report__intro-body">
          货位分配的目标函数是<b>加权拣货行走距离</b>
          <code>F = Σ(出库频次 × 货位到出入口距离)</code>，单位「米·次」；
          拣货路径的度量是<b>行走总距离</b>，单位「米」。
          两者都按曼哈顿距离计算，与算法内核及论文实验脚本口径一致。
        </div>
      </ElAlert>
    </div>

    <!-- 关键结论 -->
    <div v-if="conclusions.length" class="algo-report__conclusions">
      <div v-for="c in conclusions" :key="c.label" class="conclusion-card">
        <div class="conclusion-card__label">{{ c.label }}</div>
        <div class="conclusion-card__value" :style="{ color: c.color }">{{ c.value }}</div>
        <div class="conclusion-card__note">{{ c.note }}</div>
      </div>
    </div>

    <!-- 货位分配效果 -->
    <div class="wms-card">
      <div class="wms-card__title">
        货位分配优化效果
        <ElTag v-if="slotting" size="small" type="info" effect="plain">
          {{ slotting.productCount }} 种商品 / {{ slotting.slotCount }} 个货位
        </ElTag>
      </div>

      <ElRow v-if="slotting" :gutter="12">
        <ElCol :xs="24" :lg="12">
          <div class="algo-report__chart-title">
            <span>各策略目标函数对比</span>
            <span class="algo-report__chart-note">数值越低越优，绿色为最优</span>
          </div>
          <ChartBox :option="slottingOption" height="240px" />
        </ElCol>
        <ElCol :xs="24" :lg="12">
          <div class="algo-report__chart-title">
            <span>相对随机分配的降幅</span>
            <span class="algo-report__chart-note">任务书指标 ≥ 15%</span>
          </div>
          <ChartBox :option="slottingImproveOption" height="240px" />
        </ElCol>
      </ElRow>

      <ElTable v-if="slottingRows.length" :data="slottingRows" size="small" stripe class="algo-report__table">
        <ElTableColumn prop="label" label="分配策略" min-width="150" />
        <ElTableColumn label="目标函数（米·次）" width="170" align="right">
          <template #default="{ row }">
            <span class="num algo-report__num">{{ formatQuantity(row.objective) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="相对随机降幅" width="150" align="right">
          <template #default="{ row }">
            <span v-if="row.improvement !== undefined" class="algo-report__improve">
              {{ row.improvement }}%
            </span>
            <span v-else class="algo-report__muted">基线</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="算法耗时" min-width="120" align="right">
          <template #default="{ row }">
            <span class="num">{{ row.elapsed ?? '—' }} ms</span>
          </template>
        </ElTableColumn>
      </ElTable>

      <EmptyState v-else-if="!loading" description="暂无商品或货位数据，无法统计货位分配效果" />
    </div>

    <!-- 拣货路径效果 -->
    <div class="wms-card">
      <div class="wms-card__title">
        拣货路径规划效果
        <ElTag v-if="picking" size="small" type="info" effect="plain">
          {{ picking.pickPointCount }} 个拣货点
        </ElTag>
      </div>

      <ElAlert v-if="pickingError" type="warning" :closable="false" show-icon>
        {{ pickingError }}
      </ElAlert>

      <ElRow v-if="picking" :gutter="12">
        <ElCol :xs="24" :lg="12">
          <div class="algo-report__chart-title">
            <span>各策略路径长度对比</span>
            <span class="algo-report__chart-note">以 S 型穿越为传统基线</span>
          </div>
          <ChartBox :option="pickingOption" height="240px" />
        </ElCol>
        <ElCol :xs="24" :lg="12">
          <div class="algo-report__chart-title">
            <span>相对 S 型基线的降幅</span>
            <span class="algo-report__chart-note">任务书指标 ≥ 10%</span>
          </div>
          <ChartBox :option="pickingImproveOption" height="240px" />
        </ElCol>
      </ElRow>

      <ElTable v-if="pickingRows.length" :data="pickingRows" size="small" stripe class="algo-report__table">
        <ElTableColumn prop="label" label="路径策略" min-width="150" />
        <ElTableColumn label="路径长度（米）" width="150" align="right">
          <template #default="{ row }">
            <span class="num algo-report__num">{{ row.distance }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="相对 S 型降幅" width="150" align="right">
          <template #default="{ row }">
            <span
              class="algo-report__improve"
              :class="{ 'is-negative': Number(row.improvement) < 0 }"
            >
              {{ row.improvement }}%
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="算法耗时" min-width="120" align="right">
          <template #default="{ row }">
            <span class="num">{{ row.elapsed ?? '—' }} ms</span>
          </template>
        </ElTableColumn>
      </ElTable>

      <EmptyState v-else-if="!loading && !pickingError" description="暂无拣货点数据" />
    </div>

    <!-- 真实单据表现 -->
    <div class="wms-card">
      <div class="wms-card__title">
        真实出库单上的算法表现
        <ElTooltip content="按出库单实际使用的路径算法统计，反映算法在真实业务数据上的效果" placement="top">
          <ElIcon class="algo-report__info"><QuestionFilled /></ElIcon>
        </ElTooltip>
      </div>

      <ElTable v-if="routeStats.length" :data="routeStats" size="small" stripe>
        <ElTableColumn label="路径算法" min-width="160">
          <template #default="{ row }">{{ pickingLabel(row.routeAlgo) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="orderCount" label="单据数" width="120" align="right" />
        <ElTableColumn label="平均路径长度（米）" min-width="160" align="right">
          <template #default="{ row }">
            <span class="num algo-report__num">{{ row.avgDistance }}</span>
          </template>
        </ElTableColumn>
      </ElTable>

      <EmptyState
        v-else
        description="尚无已规划路径的出库单；请到「拣货路径规划」页规划路径后再查看"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.algo-report {
  &__label {
    font-size: 13px;
    color: #606266;
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

    code {
      padding: 1px 5px;
      border-radius: 3px;
      background: #f2f3f5;
      font-family: 'Consolas', monospace;
      font-size: 11px;
      color: #c7254e;
    }
  }

  &__conclusions {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    margin: 12px 0;
  }

  &__chart-title {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 4px;
    font-size: 13px;
    font-weight: 600;
    color: #4b5563;
  }

  &__chart-note {
    font-size: 11px;
    font-weight: 400;
    color: #a8abb2;
  }

  &__table {
    margin-top: 12px;
  }

  &__num {
    font-weight: 600;
    color: #1f2937;
  }

  &__improve {
    font-weight: 600;
    color: #10b981;

    &.is-negative {
      color: #ef4444;
    }
  }

  &__muted {
    color: #c0c4cc;
    font-size: 12px;
  }

  &__info {
    margin-left: 4px;
    color: #c0c4cc;
    cursor: help;
    font-size: 13px;
  }
}

.conclusion-card {
  padding: 16px;
  background: #fff;
  border-radius: var(--wms-card-radius);
  box-shadow: var(--wms-shadow-sm);
  border-left: 3px solid var(--el-color-primary);

  &__label {
    font-size: 12px;
    color: #909399;
  }

  &__value {
    margin-top: 6px;
    font-size: 22px;
    font-weight: 700;
    line-height: 1.25;
  }

  &__note {
    margin-top: 4px;
    font-size: 11px;
    color: #a8abb2;
  }
}

@media (max-width: 1200px) {
  .algo-report__conclusions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
