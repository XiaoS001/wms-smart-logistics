<script setup lang="ts">
/**
 * 优化方案历史。
 *
 * 每次货位优化保存的方案都在此归档，记录策略、参数、优化前后的目标函数值、
 * 降幅与是否已应用，形成可追溯的优化记录，也是论文中"优化效果"一节的数据来源。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { slottingApi } from '@/api/slotting'
import { useTable, confirmRemove } from '@/composables/useTable'
import { useAppStore } from '@/stores/app'
import { SLOTTING_STRATEGIES } from '@/utils/constants'
import { formatDateTime, formatQuantity } from '@/utils/format'
import ChartBox from '@/components/ChartBox.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { SlottingPlan } from '@/types/business'

const appStore = useAppStore()

const filters = reactive({ applied: 'all' as string })

const { loading, pageList, total, refresh } = useTable<SlottingPlan>({
  fetcher: () => slottingApi.listPlans({ warehouseId: appStore.currentWarehouseId, limit: 200 }),
  filter: (row) => (filters.applied === 'all' ? true : row.applied === Number(filters.applied))
})

/* ------------------------------ 策略效果统计 ------------------------------ */
const statsLoading = ref(false)
const stats = ref<Array<Record<string, unknown>>>([])

const statsOption = computed<EChartsOption>(() => {
  const rows = stats.value
  if (!rows.length) return {}
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['平均降幅', '最佳降幅'], right: 0, top: 0, icon: 'roundRect' },
    grid: { left: 8, right: 20, bottom: 4, top: 34, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map(
        (r) =>
          SLOTTING_STRATEGIES.find((s) => s.value === r.strategy)?.label ?? String(r.strategy)
      ),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      name: '降幅 %',
      nameTextStyle: { color: '#909399', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    series: [
      {
        name: '平均降幅',
        type: 'bar',
        barMaxWidth: 26,
        data: rows.map((r) => Number(r.avgImprovement ?? 0)),
        itemStyle: { color: '#2563eb', borderRadius: [3, 3, 0, 0] }
      },
      {
        name: '最佳降幅',
        type: 'bar',
        barMaxWidth: 26,
        data: rows.map((r) => Number(r.bestImprovement ?? 0)),
        itemStyle: { color: '#10b981', borderRadius: [3, 3, 0, 0] }
      }
    ]
  }
})

async function loadStats() {
  statsLoading.value = true
  try {
    stats.value = await slottingApi.planStatistics()
  } catch {
    stats.value = []
  } finally {
    statsLoading.value = false
  }
}

/* ------------------------------ 详情 ------------------------------ */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<(SlottingPlan & { seed?: number; slotOfProduct?: number[] }) | null>(null)

async function openDetail(row: SlottingPlan) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await slottingApi.planDetail(row.id)
  } finally {
    detailLoading.value = false
  }
}

/* ------------------------------ 应用与删除 ------------------------------ */
const applyingId = ref<number | null>(null)

async function applyPlan(row: SlottingPlan) {
  try {
    await ElMessageBox.confirm(
      `应用方案「${row.planNo}」会把商品库存搬迁到优化后的货位。\n预计搬迁 ${row.productCount} 种商品对应的库存记录，是否继续？`,
      '应用优化方案',
      { type: 'warning', confirmButtonText: '确认应用', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  applyingId.value = row.id
  try {
    const res = await slottingApi.applyPlan(row.id)
    ElMessage.success(
      `方案已应用：搬迁 ${res.movedRecords} 条库存记录，当前占用 ${res.occupiedLocations} 个货位`
    )
    await refresh()
    await loadStats()
  } finally {
    applyingId.value = null
  }
}

async function removePlan(row: SlottingPlan) {
  await confirmRemove(`方案 ${row.planNo}`, () => slottingApi.deletePlan(row.id), async () => {
    await refresh()
    await loadStats()
  })
}

/** 汇总：方案总数、已应用数、最佳降幅 */
const overview = computed(() => {
  const list = pageList.value
  void list
  return null
})
void overview

const summary = computed(() => {
  const all = total.value
  const applied = pageList.value.filter((p) => p.applied === 1).length
  const best = pageList.value.reduce(
    (max, p) => Math.max(max, Number(p.improvementPercent ?? 0)),
    0
  )
  return { all, applied, best }
})

/** 分配明细数组：模板中直接用该计算属性，避免在 v-for 里访问可空对象 */
const detailSlots = computed<number[]>(() => detail.value?.slotOfProduct ?? [])

/**
 * 策略编码转显示名称。
 *
 * 单独抽成函数而不在模板里内联 find：
 * 内联写法会把 detail.strategy 放进 arrow 函数体内，
 * TypeScript 无法把外层的 v-if 收窄带进函数体，会报"可能为 null"。
 */
function strategyLabelOf(code?: string): string {
  if (!code) return '—'
  return SLOTTING_STRATEGIES.find((s) => s.value === code)?.label ?? code
}

onMounted(loadStats)
</script>

<template>
  <div class="page-root plan-page">
    <!-- 概览 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <ElSelect v-model="filters.applied" placeholder="应用状态" style="width: 150px">
          <ElOption label="全部方案" value="all" />
          <ElOption label="已应用" value="1" />
          <ElOption label="未应用" value="0" />
        </ElSelect>
        <ElButton :icon="'Refresh'" :loading="loading" @click="refresh">刷新</ElButton>

        <div class="wms-toolbar__right">
          <ElButton :icon="'Aim'" @click="$router.push('/algo/slotting')">去生成新方案</ElButton>
        </div>
      </div>

      <div class="plan-page__overview">
        <div class="plan-page__overview-item">
          <span class="plan-page__overview-label">方案总数</span>
          <span class="plan-page__overview-value">{{ summary.all }}</span>
        </div>
        <div class="plan-page__overview-item">
          <span class="plan-page__overview-label">已应用</span>
          <span class="plan-page__overview-value is-success">{{ summary.applied }}</span>
        </div>
        <div class="plan-page__overview-item">
          <span class="plan-page__overview-label">最佳降幅</span>
          <span class="plan-page__overview-value is-primary">{{ summary.best }}%</span>
        </div>
      </div>

      <ElAlert type="info" :closable="false" show-icon class="plan-page__note">
        <template #title>关于优化记录</template>
        <div class="plan-page__note-body">
          已应用的方案不能删除，需保留作为优化记录；未应用的方案可随时删除。
          每次应用都会在事务中同步重算货位占用状态，避免出现"库存已搬但货位状态未更新"的不一致。
        </div>
      </ElAlert>
    </div>

    <!-- 策略效果统计 -->
    <div v-if="stats.length" class="wms-card">
      <div class="wms-card__title">
        各策略历史优化效果
        <ElTag size="small" type="info" effect="plain">按平均降幅排序</ElTag>
      </div>
      <ChartBox v-loading="statsLoading" :option="statsOption" height="240px" />
      <ElTable :data="stats" size="small" stripe class="plan-page__stats-table">
        <ElTableColumn label="策略" min-width="150">
          <template #default="{ row }">
            {{ SLOTTING_STRATEGIES.find((s) => s.value === row.strategy)?.label ?? row.strategy }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="planCount" label="方案数" width="100" align="right" />
        <ElTableColumn label="平均降幅" width="120" align="right">
          <template #default="{ row }">
            <span class="plan-page__improve">{{ row.avgImprovement }}%</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="最佳降幅" width="120" align="right">
          <template #default="{ row }">
            <span class="plan-page__improve is-best">{{ row.bestImprovement }}%</span>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>

    <!-- 方案列表 -->
    <div class="wms-card">
      <div class="wms-card__title">
        优化方案记录
        <ElTag size="small" type="info" effect="plain">共 {{ total }} 条</ElTag>
      </div>

      <ElTable v-loading="loading" :data="pageList" stripe size="small">
        <ElTableColumn prop="planNo" label="方案编号" width="156" fixed>
          <template #default="{ row }">
            <span class="plan-page__no">{{ row.planNo }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="策略" width="140">
          <template #default="{ row }">
            {{ SLOTTING_STRATEGIES.find((s) => s.value === row.strategy)?.label ?? row.strategy }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="productCount" label="商品数" width="84" align="right" />
        <ElTableColumn prop="slotCount" label="货位数" width="84" align="right" />
        <ElTableColumn label="优化前（米·次）" width="140" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatQuantity(row.currentObjective) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="优化后（米·次）" width="140" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatQuantity(row.optimizedObjective) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="降幅" width="96" align="right" sortable :sort-method="(a: any, b: any) => (a.improvementPercent ?? 0) - (b.improvementPercent ?? 0)">
          <template #default="{ row }">
            <span class="plan-page__improve">{{ row.improvementPercent }}%</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="算法耗时" width="100" align="right">
          <template #default="{ row }">
            <span class="num">{{ row.elapsedMillis ?? '—' }} ms</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="应用状态" width="112" align="center">
          <template #default="{ row }">
            <ElTag v-if="row.applied === 1" type="success" size="small">已应用</ElTag>
            <ElTag v-else type="info" size="small">未应用</ElTag>
            <div v-if="row.applied === 1 && row.movedRecords" class="plan-page__moved">
              搬迁 {{ row.movedRecords }} 条
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="生成时间" width="156">
          <template #default="{ row }">
            <span class="plan-page__time">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton link type="primary" size="small" @click="openDetail(row)">详情</ElButton>
            <ElButton
              v-if="row.applied !== 1"
              link
              type="success"
              size="small"
              :loading="applyingId === row.id"
              @click="applyPlan(row)"
            >
              应用
            </ElButton>
            <ElButton
              v-if="row.applied !== 1"
              link
              type="danger"
              size="small"
              @click="removePlan(row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>

        <template #empty>
          <EmptyState description="暂无优化方案，请先在「货位优化」页生成并保存方案" />
        </template>
      </ElTable>
    </div>

    <!-- 详情 -->
    <ElDrawer v-model="detailVisible" title="方案详情" size="640px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <ElDescriptions :column="2" border size="small">
            <ElDescriptionsItem label="方案编号">
              <span class="plan-page__no">{{ detail.planNo }}</span>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="优化策略">
              {{ strategyLabelOf(detail.strategy) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="随机种子">{{ detail.seed ?? '—' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="参与商品数">{{ detail.productCount }}</ElDescriptionsItem>
            <ElDescriptionsItem label="参与货位数">{{ detail.slotCount }}</ElDescriptionsItem>
            <ElDescriptionsItem label="算法耗时">{{ detail.elapsedMillis }} ms</ElDescriptionsItem>
            <ElDescriptionsItem label="优化前目标函数">
              {{ formatQuantity(detail.currentObjective) }} 米·次
            </ElDescriptionsItem>
            <ElDescriptionsItem label="优化后目标函数">
              {{ formatQuantity(detail.optimizedObjective) }} 米·次
            </ElDescriptionsItem>
            <ElDescriptionsItem label="降幅">
              <span class="plan-page__improve">{{ detail.improvementPercent }}%</span>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="应用状态">
              <ElTag :type="detail.applied === 1 ? 'success' : 'info'" size="small">
                {{ detail.applied === 1 ? '已应用' : '未应用' }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="生成时间">
              {{ formatDateTime(detail.createTime) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="应用时间">
              {{ detail.appliedTime ? formatDateTime(detail.appliedTime) : '—' }}
            </ElDescriptionsItem>
          </ElDescriptions>

          <div v-if="detail?.remark" class="plan-page__remark">{{ detail.remark }}</div>

          <div class="plan-page__section-title">
            分配明细（商品下标 → 货位编号，编号为按距离升序的第 k 个货位）
          </div>
          <div v-if="detailSlots.length" class="plan-page__slots">
            <span
              v-for="(slot, index) in detailSlots"
              :key="index"
              class="plan-page__slot"
              :title="`商品下标 ${index} → 货位编号 ${slot}`"
            >
              {{ index }}<span class="plan-page__slot-arrow">→</span>{{ slot }}
            </span>
          </div>
          <EmptyState v-else description="该方案未保存分配明细" />
        </template>
      </div>

      <template #footer>
        <ElButton @click="detailVisible = false">关闭</ElButton>
      </template>
    </ElDrawer>
  </div>
</template>

<style scoped lang="scss">
.plan-page {
  &__overview {
    display: flex;
    gap: 34px;
    padding: 4px 0 14px;
  }

  &__overview-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__overview-label {
    font-size: 12px;
    color: #909399;
  }

  &__overview-value {
    font-size: 24px;
    font-weight: 700;
    color: #1f2937;

    &.is-success {
      color: #10b981;
    }

    &.is-primary {
      color: var(--el-color-primary);
    }
  }

  &__note {
    margin-top: 2px;
  }

  &__note-body {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.75;
  }

  &__stats-table {
    margin-top: 10px;
  }

  &__no {
    font-family: 'Consolas', monospace;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__improve {
    font-weight: 600;
    color: #10b981;

    &.is-best {
      color: var(--el-color-primary);
    }
  }

  &__moved {
    margin-top: 2px;
    font-size: 10px;
    color: #a8abb2;
  }

  &__time {
    font-size: 12px;
    color: #909399;
  }

  &__remark {
    margin-top: 12px;
    padding: 9px 12px;
    border-radius: 6px;
    background: #f8fafc;
    font-size: 12px;
    color: #606266;
    line-height: 1.7;
  }

  &__section-title {
    margin: 16px 0 8px;
    font-size: 13px;
    font-weight: 600;
    color: #4b5563;
  }

  &__slots {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    max-height: 220px;
    overflow-y: auto;
    padding: 10px;
    border-radius: 6px;
    background: #f8fafc;
  }

  &__slot {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding: 2px 7px;
    border-radius: 4px;
    background: #fff;
    border: 1px solid #e5e7eb;
    font-family: 'Consolas', monospace;
    font-size: 11px;
    color: #475569;
  }

  &__slot-arrow {
    color: #c0c4cc;
  }
}
</style>
