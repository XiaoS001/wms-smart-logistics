<script setup lang="ts">
/**
 * 库存与作业统计。
 *
 * 从「作业量」「库存结构」「频次结构」三个角度描述仓库运行状况：
 *  1. 作业量趋势：按日的出入库单量与数量，用于观察作业节奏；
 *  2. 库存分布：按库区的货位占用与库存量、按 ABC 分类的商品与库存；
 *  3. 频次贡献：出库频次的累计占比，用数据说明 ABC 分类的划分依据。
 */
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { reportApi } from '@/api/report'
import { useAppStore } from '@/stores/app'
import { ABC_COLORS } from '@/utils/constants'
import { formatQuantity } from '@/utils/format'
import ChartBox from '@/components/ChartBox.vue'
import EmptyState from '@/components/EmptyState.vue'
import type {
  FrequencyContributionResult,
  OperationTrendResult,
  StockDistributionResult
} from '@/types/business'

const appStore = useAppStore()

const loading = ref(false)
const days = ref(30)
const trend = ref<OperationTrendResult | null>(null)
const distribution = ref<StockDistributionResult | null>(null)
const frequency = ref<FrequencyContributionResult | null>(null)

/** 作业量趋势：柱状（数量）+ 折线（单量）双轴 */
const trendOption = computed<EChartsOption>(() => {
  const rows = trend.value?.trend ?? []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: {
      data: ['入库数量', '出库数量', '入库单数', '出库单数'],
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { fontSize: 11 }
    },
    grid: { left: 8, right: 8, bottom: 4, top: 40, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.statDate.slice(5)),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: [
      {
        type: 'value',
        name: '数量',
        nameTextStyle: { color: '#909399', fontSize: 11 },
        splitLine: { lineStyle: { color: '#f2f3f5' } },
        axisLabel: { color: '#909399', fontSize: 11 }
      },
      {
        type: 'value',
        name: '单数',
        nameTextStyle: { color: '#909399', fontSize: 11 },
        splitLine: { show: false },
        axisLabel: { color: '#909399', fontSize: 11 }
      }
    ],
    series: [
      {
        name: '入库数量',
        type: 'bar',
        data: rows.map((r) => r.inboundQuantity),
        barMaxWidth: 16,
        itemStyle: { color: '#2563eb', borderRadius: [3, 3, 0, 0] }
      },
      {
        name: '出库数量',
        type: 'bar',
        data: rows.map((r) => r.outboundQuantity),
        barMaxWidth: 16,
        itemStyle: { color: '#10b981', borderRadius: [3, 3, 0, 0] }
      },
      {
        name: '入库单数',
        type: 'line',
        yAxisIndex: 1,
        data: rows.map((r) => r.inboundOrders),
        smooth: true,
        symbolSize: 5,
        lineStyle: { color: '#60a5fa', width: 2, type: 'dashed' },
        itemStyle: { color: '#60a5fa' }
      },
      {
        name: '出库单数',
        type: 'line',
        yAxisIndex: 1,
        data: rows.map((r) => r.outboundOrders),
        smooth: true,
        symbolSize: 5,
        lineStyle: { color: '#34d399', width: 2, type: 'dashed' },
        itemStyle: { color: '#34d399' }
      }
    ]
  }
})

/** 库区分布：货位占用（堆叠柱）+ 库存量（折线） */
const zoneOption = computed<EChartsOption>(() => {
  const rows = distribution.value?.byZone ?? []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: {
      data: ['已占用货位', '空闲货位', '库存数量'],
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { fontSize: 11 }
    },
    grid: { left: 8, right: 8, bottom: 4, top: 40, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.zoneName),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: [
      {
        type: 'value',
        name: '货位数',
        nameTextStyle: { color: '#909399', fontSize: 11 },
        splitLine: { lineStyle: { color: '#f2f3f5' } },
        axisLabel: { color: '#909399', fontSize: 11 }
      },
      {
        type: 'value',
        name: '库存量',
        nameTextStyle: { color: '#909399', fontSize: 11 },
        splitLine: { show: false },
        axisLabel: { color: '#909399', fontSize: 11 }
      }
    ],
    series: [
      {
        name: '已占用货位',
        type: 'bar',
        stack: 'loc',
        data: rows.map((r) => r.usedLocations),
        barMaxWidth: 34,
        itemStyle: { color: '#f59e0b' }
      },
      {
        name: '空闲货位',
        type: 'bar',
        stack: 'loc',
        data: rows.map((r) => r.freeLocations),
        barMaxWidth: 34,
        itemStyle: { color: '#e5e7eb', borderRadius: [3, 3, 0, 0] }
      },
      {
        name: '库存数量',
        type: 'line',
        yAxisIndex: 1,
        data: rows.map((r) => r.stockQuantity),
        smooth: true,
        symbolSize: 6,
        lineStyle: { color: '#2563eb', width: 2 },
        itemStyle: { color: '#2563eb' }
      }
    ]
  }
})

/** ABC 分类分布：商品数量与库存量 */
const abcOption = computed<EChartsOption>(() => {
  const rows = distribution.value?.byAbcClass ?? []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: {
      data: ['商品种类', '库存数量'],
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { fontSize: 11 }
    },
    grid: { left: 8, right: 8, bottom: 4, top: 40, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => `${r.abcClass} 类`),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#606266', fontSize: 12 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    series: [
      {
        name: '商品种类',
        type: 'bar',
        data: rows.map((r) => ({
          value: r.productKinds,
          itemStyle: {
            color: ABC_COLORS[r.abcClass] ?? '#93b4f5',
            borderRadius: [3, 3, 0, 0]
          }
        })),
        barMaxWidth: 30
      },
      {
        name: '库存数量',
        type: 'bar',
        data: rows.map((r) => r.stockQuantity),
        barMaxWidth: 30,
        itemStyle: { color: '#93b4f5', borderRadius: [3, 3, 0, 0] }
      }
    ]
  }
})

/** 频次贡献：累计占比折线 + 阈值参考线 */
const frequencyOption = computed<EChartsOption>(() => {
  const items = frequency.value?.items ?? []
  return {
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['出库频次', '累计占比'],
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { fontSize: 11 }
    },
    grid: { left: 8, right: 8, bottom: 4, top: 40, containLabel: true },
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
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
          color: (p: { dataIndex: number }) =>
            ABC_COLORS[items[p.dataIndex]?.abcClass ?? 'C'] ?? '#93b4f5'
        }
      },
      {
        name: '累计占比',
        type: 'line',
        yAxisIndex: 1,
        data: items.map((i) => i.cumulativeRatio),
        smooth: true,
        symbolSize: 5,
        lineStyle: { color: '#f59e0b', width: 2 },
        itemStyle: { color: '#f59e0b' },
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

/** 作业量汇总卡 */
const trendSummary = computed(() => {
  const s = trend.value?.summary
  if (!s) return []
  return [
    { label: '统计天数', value: String(s.days), unit: '天', note: `有作业记录 ${s.activeDays} 天` },
    { label: '入库单数', value: String(s.inboundOrders), unit: '单', note: `数量 ${formatQuantity(s.inboundQuantity)}` },
    { label: '出库单数', value: String(s.outboundOrders), unit: '单', note: `数量 ${formatQuantity(s.outboundQuantity)}` },
    {
      label: '出入库净差',
      value: String(s.inboundQuantity - s.outboundQuantity),
      unit: '件',
      note: '正数表示库存净增'
    }
  ]
})

/** ABC 汇总表 */
const abcRows = computed(() => {
  const rows = distribution.value?.byAbcClass ?? []
  const totalKinds = rows.reduce((s, r) => s + (r.productKinds ?? 0), 0)
  const totalStock = rows.reduce((s, r) => s + (r.stockQuantity ?? 0), 0)
  return rows.map((r) => ({
    ...r,
    kindRatio: totalKinds ? ((r.productKinds / totalKinds) * 100).toFixed(1) : '0.0',
    stockRatio: totalStock ? ((r.stockQuantity / totalStock) * 100).toFixed(1) : '0.0',
    avgStock: r.productKinds ? (r.stockQuantity / r.productKinds).toFixed(1) : '0.0'
  }))
})

async function load() {
  loading.value = true
  const wid = appStore.currentWarehouseId
  try {
    await Promise.all([
      reportApi
        .operationTrend(days.value)
        .then((d) => (trend.value = d))
        .catch(() => (trend.value = null)),
      reportApi
        .stockDistribution(wid)
        .then((d) => (distribution.value = d))
        .catch(() => (distribution.value = null)),
      reportApi
        .frequencyContribution(15)
        .then((d) => (frequency.value = d))
        .catch(() => (frequency.value = null))
    ])
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-root stock-report">
    <!-- 工具条 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <span class="stock-report__label">统计区间</span>
        <ElRadioGroup v-model="days" @change="load">
          <ElRadioButton :value="7">近 7 天</ElRadioButton>
          <ElRadioButton :value="14">近 14 天</ElRadioButton>
          <ElRadioButton :value="30">近 30 天</ElRadioButton>
          <ElRadioButton :value="90">近 90 天</ElRadioButton>
        </ElRadioGroup>
        <ElButton :icon="'Refresh'" :loading="loading" @click="load">刷新</ElButton>
      </div>
    </div>

    <!-- 作业量汇总 -->
    <div v-if="trendSummary.length" class="stock-report__summary">
      <div v-for="s in trendSummary" :key="s.label" class="summary-card">
        <div class="summary-card__label">{{ s.label }}</div>
        <div class="summary-card__value">
          <span class="num">{{ s.value }}</span>
          <span class="summary-card__unit">{{ s.unit }}</span>
        </div>
        <div class="summary-card__note">{{ s.note }}</div>
      </div>
    </div>

    <!-- 作业量趋势 -->
    <div class="wms-card">
      <div class="wms-card__title">
        作业量趋势
        <ElTag size="small" type="info" effect="plain">按日统计，无作业的日期补 0</ElTag>
      </div>
      <ChartBox v-if="trend?.trend?.length" :option="trendOption" height="320px" />
      <EmptyState v-else-if="!loading" description="统计区间内暂无出入库作业记录" />
    </div>

    <!-- 库存分布 -->
    <ElRow :gutter="12">
      <ElCol :xs="24" :lg="14">
        <div class="wms-card">
          <div class="wms-card__title">库存按库区分布</div>
          <ChartBox
            v-if="distribution?.byZone?.length"
            :option="zoneOption"
            height="300px"
          />
          <EmptyState v-else-if="!loading" description="暂无库区数据" />
        </div>
      </ElCol>
      <ElCol :xs="24" :lg="10">
        <div class="wms-card">
          <div class="wms-card__title">库存按 ABC 分类分布</div>
          <ChartBox
            v-if="distribution?.byAbcClass?.length"
            :option="abcOption"
            height="300px"
          />
          <EmptyState v-else-if="!loading" description="暂无分类数据" />
        </div>
      </ElCol>
    </ElRow>

    <!-- 库区与 ABC 明细表 -->
    <ElRow :gutter="12">
      <ElCol :xs="24" :lg="14">
        <div class="wms-card">
          <div class="wms-card__title">库区占用明细</div>
          <ElTable
            v-if="distribution?.byZone?.length"
            :data="distribution.byZone"
            size="small"
            stripe
          >
            <ElTableColumn prop="zoneName" label="库区" min-width="130" />
            <ElTableColumn prop="zoneType" label="类型" width="80" align="center" />
            <ElTableColumn prop="locationCount" label="货位总数" width="100" align="right" />
            <ElTableColumn prop="usedLocations" label="已占用" width="90" align="right" />
            <ElTableColumn prop="freeLocations" label="空闲" width="80" align="right" />
            <ElTableColumn label="占用率" width="150">
              <template #default="{ row }">
                <ElProgress
                  :percentage="Number(row.usageRate)"
                  :stroke-width="10"
                  :color="Number(row.usageRate) > 80 ? '#f59e0b' : '#10b981'"
                />
              </template>
            </ElTableColumn>
            <ElTableColumn label="库存量" width="100" align="right">
              <template #default="{ row }">
                <span class="num">{{ formatQuantity(row.stockQuantity) }}</span>
              </template>
            </ElTableColumn>
          </ElTable>
          <EmptyState v-else description="暂无库区数据" />
        </div>
      </ElCol>

      <ElCol :xs="24" :lg="10">
        <div class="wms-card">
          <div class="wms-card__title">
            ABC 分类明细
            <ElTooltip content="A 类商品种类通常最少但库存与出库频次占比最高" placement="top">
              <ElIcon class="stock-report__info"><QuestionFilled /></ElIcon>
            </ElTooltip>
          </div>
          <ElTable v-if="abcRows.length" :data="abcRows" size="small" stripe>
            <ElTableColumn label="分类" width="72" align="center">
              <template #default="{ row }">
                <ElTag :color="ABC_COLORS[row.abcClass]" effect="dark" size="small">
                  {{ row.abcClass }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="productKinds" label="商品种类" width="90" align="right" />
            <ElTableColumn label="种类占比" width="94" align="right">
              <template #default="{ row }">{{ row.kindRatio }}%</template>
            </ElTableColumn>
            <ElTableColumn label="库存量" width="94" align="right">
              <template #default="{ row }">
                <span class="num">{{ formatQuantity(row.stockQuantity) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="库存占比" width="94" align="right">
              <template #default="{ row }">{{ row.stockRatio }}%</template>
            </ElTableColumn>
            <ElTableColumn label="户均库存" min-width="94" align="right">
              <template #default="{ row }">{{ row.avgStock }}</template>
            </ElTableColumn>
          </ElTable>
          <EmptyState v-else description="暂无分类数据" />
        </div>
      </ElCol>
    </ElRow>

    <!-- 频次贡献 -->
    <div class="wms-card">
      <div class="wms-card__title">
        出库频次贡献结构
        <ElTooltip
          content="按出库频次降序排列，虚线为 ABC 分类的 70% / 90% 阈值；柱色表示 ABC 分类"
          placement="top"
        >
          <ElIcon class="stock-report__info"><QuestionFilled /></ElIcon>
        </ElTooltip>
      </div>

      <ChartBox v-if="frequency?.items?.length" :option="frequencyOption" height="300px" />

      <div v-if="frequency" class="stock-report__freq-note">
        <ElIcon><InfoFilled /></ElIcon>
        <span>
          共统计前 <b>{{ frequency.topCount }}</b> 种商品，出库频次合计
          <b>{{ formatQuantity(frequency.frequencySum) }}</b>；
          前 <b>{{ frequency.productsFor70Percent ?? '—' }}</b> 种商品即贡献了 70% 的出库频次，
          这正是 ABC 分类把 A 类安排在近端货位的依据。
        </span>
      </div>

      <EmptyState v-else-if="!loading" description="暂无出库频次数据" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.stock-report {
  &__label {
    font-size: 13px;
    color: #606266;
  }

  &__summary {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    margin: 12px 0;
  }

  &__info {
    margin-left: 4px;
    color: #c0c4cc;
    cursor: help;
    font-size: 13px;
  }

  &__freq-note {
    display: flex;
    align-items: flex-start;
    gap: 7px;
    margin-top: 10px;
    padding: 10px 12px;
    border-radius: 6px;
    background: #f8fafc;
    font-size: 12px;
    line-height: 1.75;
    color: #475569;

    .el-icon {
      margin-top: 2px;
      color: var(--el-color-primary);
      flex-shrink: 0;
    }

    b {
      color: var(--el-color-primary);
    }
  }
}

.summary-card {
  padding: 14px 16px;
  background: #fff;
  border-radius: var(--wms-card-radius);
  box-shadow: var(--wms-shadow-sm);

  &__label {
    font-size: 12px;
    color: #909399;
  }

  &__value {
    margin-top: 4px;
    font-size: 22px;
    font-weight: 700;
    color: #1f2937;
    line-height: 1.2;
  }

  &__unit {
    margin-left: 3px;
    font-size: 12px;
    font-weight: 400;
    color: #909399;
  }

  &__note {
    margin-top: 3px;
    font-size: 11px;
    color: #a8abb2;
  }
}

@media (max-width: 1200px) {
  .stock-report__summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
