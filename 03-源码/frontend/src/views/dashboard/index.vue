<script setup lang="ts">
/**
 * 首页概览。
 *
 * 作为系统的门面与演示入口，突出三类信息：
 * 1. 关键指标：库存总量、货位占用率、缺货预警、待办单据；
 * 2. 运营趋势与库存结构：出入库走势、出库频次贡献结构；
 * 3. 优化效果速览：直接展示货位分配相对随机分配、拣货路径相对 S 型的改善幅度，
 *    让"以算法为核心"这一课题定位在第一屏就能被看见。
 */
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { reportApi } from '@/api/report'
import { stockApi } from '@/api/stock'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { ABC_COLORS } from '@/utils/constants'
import { formatPercent, formatQuantity, formatThousands, formatDistance } from '@/utils/format'
import ChartBox from '@/components/ChartBox.vue'
import EmptyState from '@/components/EmptyState.vue'
import type {
  DashboardData,
  FrequencyContributionResult,
  OperationTrendResult,
  PickingEffectResult,
  SlottingEffectResult,
  StockSummary
} from '@/types/business'

const appStore = useAppStore()
const userStore = useUserStore()

const loading = ref(false)
const dashboard = ref<DashboardData | null>(null)
const trend = ref<OperationTrendResult | null>(null)
const frequency = ref<FrequencyContributionResult | null>(null)
const lowStockList = ref<StockSummary[]>([])
const slottingEffect = ref<SlottingEffectResult | null>(null)
const pickingEffect = ref<PickingEffectResult | null>(null)

/** 指标卡数据 */
const statCards = computed(() => {
  const d = dashboard.value
  if (!d) return []
  return [
    {
      key: 'stock',
      label: '库存总量',
      value: formatThousands(d.stockQuantity),
      unit: '件',
      icon: 'Box',
      color: '#2563eb',
      hint: `覆盖 ${d.productCount} 种商品`
    },
    {
      key: 'location',
      label: '货位占用率',
      value: formatPercent(d.occupancyRate),
      unit: '',
      icon: 'Grid',
      color: '#10b981',
      hint: `已用 ${d.occupiedLocationCount} · 空闲 ${d.freeLocationCount}`
    },
    {
      key: 'doneInbound',
      label: '已完成入库',
      value: formatThousands(d.doneInboundCount),
      unit: '单',
      icon: 'Download',
      color: '#06b6d4',
      hint: `待入库 ${d.pendingInboundCount} 单`
    },
    {
      key: 'doneOutbound',
      label: '已完成出库',
      value: formatThousands(d.doneOutboundCount),
      unit: '单',
      icon: 'Upload',
      color: '#8b5cf6',
      hint: `待拣货 ${d.pendingOutboundCount} 单`
    },
    {
      key: 'productKinds',
      label: '在库商品种类',
      value: formatThousands(d.productCount),
      unit: '种',
      icon: 'Goods',
      color: '#0ea5e9',
      hint: '含全部启用商品'
    },
    {
      key: 'lowStock',
      label: '缺货预警',
      value: formatThousands(d.lowStockCount),
      unit: '种',
      icon: 'WarningFilled',
      color: d.lowStockCount > 0 ? '#f59e0b' : '#6b7280',
      hint: d.lowStockCount > 0 ? '低于安全库存，建议补货' : '库存水位正常'
    }
  ]
})

/** 作业量趋势图 */
const trendOption = computed<EChartsOption>(() => {
  const rows = trend.value?.trend ?? []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['入库数量', '出库数量'], right: 0, top: 0, icon: 'roundRect' },
    grid: { left: 8, right: 12, bottom: 4, top: 38, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.statDate.slice(5)),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    series: [
      {
        name: '入库数量',
        type: 'bar',
        data: rows.map((r) => r.inboundQuantity),
        barMaxWidth: 18,
        itemStyle: { color: '#2563eb', borderRadius: [3, 3, 0, 0] }
      },
      {
        name: '出库数量',
        type: 'bar',
        data: rows.map((r) => r.outboundQuantity),
        barMaxWidth: 18,
        itemStyle: { color: '#10b981', borderRadius: [3, 3, 0, 0] }
      }
    ]
  }
})

/** 频次累计占比图：说明 ABC 分类的划分依据 */
const frequencyOption = computed<EChartsOption>(() => {
  const items = (frequency.value?.items ?? []).slice(0, 12)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['出库频次', '累计占比'], right: 0, top: 0, icon: 'roundRect' },
    grid: { left: 8, right: 12, bottom: 4, top: 38, containLabel: true },
    xAxis: {
      type: 'category',
      data: items.map((i) => i.productCode),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#909399', fontSize: 10, rotate: 34 }
    },
    yAxis: [
      {
        type: 'value',
        name: '频次',
        nameTextStyle: { color: '#909399', fontSize: 11 },
        splitLine: { lineStyle: { color: '#f2f3f5' } },
        axisLabel: { color: '#909399', fontSize: 11 }
      },
      {
        type: 'value',
        name: '累计',
        max: 100,
        nameTextStyle: { color: '#909399', fontSize: 11 },
        splitLine: { show: false },
        axisLabel: { color: '#909399', fontSize: 11, formatter: '{value}%' }
      }
    ],
    series: [
      {
        name: '出库频次',
        type: 'bar',
        data: items.map((i) => i.pickFreq),
        barMaxWidth: 18,
        // 按 ABC 分类着色，直观体现"少数高频商品"
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
          color: (params: { dataIndex: number }) => {
            const cls = items[params.dataIndex]?.abcClass ?? 'C'
            return ABC_COLORS[cls] ?? '#409eff'
          }
        }
      },
      {
        name: '累计占比',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 5,
        data: items.map((i) => i.cumulativeRatio),
        lineStyle: { color: '#f59e0b', width: 2 },
        itemStyle: { color: '#f59e0b' },
        // 70% / 90% 两条参考线即 ABC 分类的阈值
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { type: 'dashed', color: '#c0c4cc' },
          label: { formatter: '{b}', color: '#909399', fontSize: 10 },
          data: [
            { yAxis: 70, name: 'A 类 70%' },
            { yAxis: 90, name: 'B 类 90%' }
          ]
        }
      }
    ]
  }
})

/** 最优路径策略：取改善幅度最大者 */
const bestPicking = computed(() => {
  const p = pickingEffect.value
  if (!p) return null
  const entries = Object.entries(p.improvementVsSShapePercent ?? {})
  if (!entries.length) return null
  const best = entries.reduce((a, b) => (b[1] > a[1] ? b : a))
  return {
    strategy: p.labels?.[best[0]] ?? best[0],
    improvement: best[1],
    distance: p.distances?.[best[0]] ?? 0,
    pickPointCount: p.pickPointCount
  }
})

async function loadAll() {
  loading.value = true
  const warehouseId = appStore.currentWarehouseId

  // 各请求独立失败不影响其它区块展示
  await Promise.all([
    reportApi
      .dashboard()
      .then((d) => (dashboard.value = d))
      .catch(() => undefined),
    reportApi
      .operationTrend(14)
      .then((t) => (trend.value = t))
      .catch(() => undefined),
    reportApi
      .frequencyContribution(12)
      .then((f) => (frequency.value = f))
      .catch(() => undefined),
    stockApi
      .summary(warehouseId)
      .then((list) => (lowStockList.value = list.filter((i) => i.lowStock === 1).slice(0, 6)))
      .catch(() => undefined),
    reportApi
      .slottingEffect(warehouseId)
      .then((s) => (slottingEffect.value = s))
      .catch(() => undefined),
    reportApi
      .pickingEffect(warehouseId)
      .then((p) => (pickingEffect.value = p))
      .catch(() => undefined)
  ])

  loading.value = false
}

onMounted(loadAll)
</script>

<template>
  <div class="page-root dashboard">
    <!-- 欢迎区 -->
    <div class="dashboard__hero">
      <div>
        <h1 class="dashboard__greeting">{{ userStore.displayName }}，欢迎回来</h1>
        <p class="dashboard__sub">
          <span>{{ appStore.currentWarehouseName }} · 面向智慧物流的仓储管理系统</span>
          <span class="dashboard__sub-tag">以货位分配与拣货路径优化为核心</span>
        </p>
      </div>
      <ElButton :icon="'Refresh'" :loading="loading" @click="loadAll">刷新数据</ElButton>
    </div>

    <!-- 关键指标 -->
    <div v-loading="loading" class="dashboard__stats">
      <div v-for="card in statCards" :key="card.key" class="stat-card">
        <div class="stat-card__icon" :style="{ background: card.color + '16', color: card.color }">
          <ElIcon :size="22"><component :is="card.icon" /></ElIcon>
        </div>
        <div class="stat-card__body">
          <div class="stat-card__label">{{ card.label }}</div>
          <div class="stat-card__value">
            <span class="num">{{ card.value }}</span>
            <span v-if="card.unit" class="stat-card__unit">{{ card.unit }}</span>
          </div>
          <div class="stat-card__hint">{{ card.hint }}</div>
        </div>
      </div>
      <div v-if="!statCards.length" class="dashboard__stats-empty">
        <EmptyState description="暂无数据，请确认后端服务已启动" />
      </div>
    </div>

    <!-- 优化效果速览：突出课题核心价值 -->
    <div class="dashboard__effects">
      <div class="effect-card">
        <div class="effect-card__head">
          <ElIcon class="effect-card__icon"><Aim /></ElIcon>
          <span>货位分配优化效果</span>
        </div>
        <template v-if="slottingEffect">
          <div class="effect-card__value">
            <span class="num">{{ slottingEffect.greedyVsRandomPercent ?? 0 }}</span>
            <span class="effect-card__unit">%</span>
          </div>
          <div class="effect-card__desc">频次贪心分配相对随机分配的加权拣货行走距离降幅</div>
          <div class="effect-card__meta">
            随机 {{ formatQuantity(slottingEffect.objectives?.random) }} →
            贪心 {{ formatQuantity(slottingEffect.objectives?.['frequency-greedy']) }}
            （单位：米·次）
          </div>
        </template>
        <div v-else class="effect-card__empty">暂无数据</div>
      </div>

      <div class="effect-card">
        <div class="effect-card__head">
          <ElIcon class="effect-card__icon"><Guide /></ElIcon>
          <span>拣货路径优化效果</span>
        </div>
        <template v-if="bestPicking">
          <div class="effect-card__value">
            <span class="num">{{ bestPicking.improvement }}</span>
            <span class="effect-card__unit">%</span>
          </div>
          <div class="effect-card__desc">
            {{ bestPicking.strategy }}相对 S 型穿越策略的路径长度降幅
          </div>
          <div class="effect-card__meta">
            {{ bestPicking.pickPointCount }} 个拣货点 · 路径
            {{ formatDistance(bestPicking.distance) }}
          </div>
        </template>
        <div v-else class="effect-card__empty">暂无数据（需仓库已有库存）</div>
      </div>
    </div>

    <!-- 图表区 -->
    <ElRow :gutter="12" class="dashboard__charts">
      <ElCol :xs="24" :lg="14">
        <div class="wms-card">
          <div class="wms-card__title">近 14 天出入库作业趋势</div>
          <ChartBox :option="trendOption" height="300px" />
        </div>
      </ElCol>
      <ElCol :xs="24" :lg="10">
        <div class="wms-card">
          <div class="wms-card__title">
            出库频次贡献结构
            <ElTooltip content="按出库频次降序排列，虚线为 ABC 分类的 70% / 90% 阈值" placement="top">
              <ElIcon class="dashboard__info"><QuestionFilled /></ElIcon>
            </ElTooltip>
          </div>
          <ChartBox :option="frequencyOption" height="272px" />
          <div v-if="frequency?.productsFor70Percent" class="dashboard__note">
            前 <b>{{ frequency.productsFor70Percent }}</b> 种商品即贡献了 70% 的出库频次，
            这正是 ABC 分类把高频商品安排在近端货位的依据。
          </div>
        </div>
      </ElCol>
    </ElRow>

    <!-- 缺货预警 -->
    <div class="wms-card">
      <div class="wms-card__title">
        缺货预警
        <ElTag v-if="lowStockList.length" type="warning" size="small" effect="plain">
          {{ lowStockList.length }} 种商品需关注
        </ElTag>
      </div>

      <ElTable v-if="lowStockList.length" :data="lowStockList" stripe size="small">
        <ElTableColumn prop="productCode" label="商品编码" width="110" />
        <ElTableColumn prop="productName" label="商品名称" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="abcClass" label="ABC" width="82" align="center">
          <template #default="{ row }">
            <ElTag :color="ABC_COLORS[row.abcClass]" effect="dark" size="small">
              {{ row.abcClass }} 类
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
        <ElTableColumn prop="locationCount" label="占用货位" width="100" align="right" />
        <ElTableColumn label="状态" width="96" align="center">
          <template #default="{ row }">
            <ElTag :type="row.totalQuantity <= 0 ? 'danger' : 'warning'" size="small">
              {{ row.totalQuantity <= 0 ? '缺货' : '偏低' }}
            </ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
      <EmptyState v-else description="暂无缺货预警，库存水位正常" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  &__hero {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding: 18px 20px;
    margin-bottom: 12px;
    border-radius: var(--wms-card-radius);
    background:
      radial-gradient(circle at 88% 30%, rgb(96 165 250 / 22%), transparent 42%),
      linear-gradient(120deg, #1e3a8a 0%, #2563eb 55%, #3b82f6 100%);
    color: #fff;
  }

  &__greeting {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
  }

  &__sub {
    margin: 8px 0 0;
    font-size: 13px;
    color: rgb(255 255 255 / 84%);
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  &__sub-tag {
    padding: 2px 8px;
    border-radius: 10px;
    background: rgb(255 255 255 / 18%);
    font-size: 12px;
  }

  &__stats {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 12px;
    min-height: 96px;
  }

  &__stats-empty {
    grid-column: 1 / -1;
    background: #fff;
    border-radius: var(--wms-card-radius);
  }

  &__effects {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 12px;
  }

  &__charts {
    margin-bottom: 12px;
  }

  &__info {
    margin-left: 4px;
    color: #c0c4cc;
    cursor: help;
    font-size: 13px;
  }

  &__note {
    margin-top: 8px;
    padding: 8px 10px;
    border-radius: 6px;
    background: #f8fafc;
    font-size: 12px;
    color: #606266;
    line-height: 1.7;

    b {
      color: var(--el-color-primary);
    }
  }
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: #fff;
  border-radius: var(--wms-card-radius);
  box-shadow: var(--wms-shadow-sm);
  transition:
    box-shadow 0.2s,
    transform 0.2s;

  &:hover {
    box-shadow: var(--wms-shadow-md);
    transform: translateY(-1px);
  }

  &__icon {
    flex-shrink: 0;
    width: 42px;
    height: 42px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__body {
    min-width: 0;
  }

  &__label {
    font-size: 12px;
    color: #909399;
  }

  &__value {
    margin-top: 2px;
    font-size: 21px;
    font-weight: 600;
    color: #1f2937;
    line-height: 1.25;
  }

  &__unit {
    margin-left: 3px;
    font-size: 12px;
    font-weight: 400;
    color: #909399;
  }

  &__hint {
    margin-top: 2px;
    font-size: 11px;
    color: #a8abb2;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.effect-card {
  padding: 18px 20px;
  background: #fff;
  border-radius: var(--wms-card-radius);
  box-shadow: var(--wms-shadow-sm);
  border-left: 3px solid var(--el-color-primary);

  &__head {
    display: flex;
    align-items: center;
    gap: 7px;
    font-size: 13px;
    font-weight: 600;
    color: #4b5563;
  }

  &__icon {
    color: var(--el-color-primary);
  }

  &__value {
    margin-top: 8px;
    font-size: 34px;
    font-weight: 700;
    line-height: 1.1;
    color: var(--el-color-primary);
  }

  &__unit {
    margin-left: 2px;
    font-size: 16px;
    font-weight: 500;
  }

  &__desc {
    margin-top: 6px;
    font-size: 12px;
    color: #606266;
  }

  &__meta {
    margin-top: 4px;
    font-size: 11px;
    color: #a8abb2;
  }

  &__empty {
    margin-top: 14px;
    font-size: 13px;
    color: #c0c4cc;
  }
}

@media (max-width: 1400px) {
  .dashboard__stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .dashboard__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard__effects {
    grid-template-columns: 1fr;
  }
}
</style>
