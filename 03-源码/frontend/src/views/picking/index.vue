<script setup lang="ts">
/**
 * 拣货路径规划。
 *
 * 本页面是课题核心能力的可视化入口：
 * 选择一张出库单 → 选定算法策略 → 规划路径 → 在库位平面图上展示行走顺序，
 * 并给出全部策略的横向对比，直观体现算法带来的路径缩短效果。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { outboundApi } from '@/api/outbound'
import { warehouseApi } from '@/api/warehouse'
import { useAppStore } from '@/stores/app'
import { PICKING_STRATEGIES } from '@/utils/constants'
import { formatDateTime, formatDistance, formatQuantity } from '@/utils/format'
import ChartBox from '@/components/ChartBox.vue'
import EmptyState from '@/components/EmptyState.vue'
import PickPathMap from '@/components/warehouse/PickPathMap.vue'
import type { BizOutboundItem, BizOutboundOrder, PlanRouteResult } from '@/types/business'
import type { BaseWarehouse } from '@/types/base'
import { ORDER_STATUS } from '@/utils/constants'

const appStore = useAppStore()

const loading = ref(false)
const planning = ref(false)
const orders = ref<BizOutboundOrder[]>([])
const currentOrderId = ref<number | null>(null)
const strategy = ref('ant-colony')
const items = ref<BizOutboundItem[]>([])
const planResult = ref<PlanRouteResult | null>(null)
const warehouse = ref<BaseWarehouse | null>(null)

/** 可选订单：只展示未完成与拣货中的单据 */
const availableOrders = computed(() =>
  orders.value.filter((o) => o.status === 0 || o.status === 1)
)

const currentOrder = computed(() =>
  orders.value.find((o) => o.id === currentOrderId.value) ?? null
)

/** 图例上的拣货点：来自当前单据明细 */
const pickPoints = computed(() =>
  items.value
    .filter((i) => i.aisleNo && i.rowNo)
    .map((i) => ({ aisleNo: i.aisleNo, rowNo: i.rowNo, label: i.productCode }))
)

/** 路径坐标：优先用规划结果，其次用明细上的拣货顺序反推 */
const routePoints = computed(() => {
  if (planResult.value?.route?.length) {
    return planResult.value.route
  }
  return []
})

/** 策略对比图：距离越短越好 */
const compareOption = computed<EChartsOption>(() => {
  const comparison = planResult.value?.comparison ?? {}
  const codes = Object.keys(comparison)
  const labels = codes.map(
    (c) => PICKING_STRATEGIES.find((s) => s.value === c)?.label ?? c
  )
  const values = codes.map((c) => comparison[c])
  const min = Math.min(...values)

  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 24, bottom: 4, top: 20, containLabel: true },
    xAxis: {
      type: 'value',
      name: '路径长度（米）',
      nameTextStyle: { color: '#909399', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#606266', fontSize: 12 }
    },
    series: [
      {
        type: 'bar',
        data: values.map((v) => ({
          value: v,
          // 最优策略高亮，其余用浅色
          itemStyle: {
            color: v === min ? '#10b981' : '#93b4f5',
            borderRadius: [0, 4, 4, 0]
          }
        })),
        barMaxWidth: 22,
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

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await outboundApi.list({
      warehouseId: appStore.currentWarehouseId,
      limit: 100
    })
    if (!currentOrderId.value && availableOrders.value.length) {
      await selectOrder(availableOrders.value[0].id)
    }
  } finally {
    loading.value = false
  }
}

async function loadWarehouse() {
  try {
    warehouse.value = await warehouseApi.detail(appStore.currentWarehouseId)
  } catch {
    warehouse.value = null
  }
}

async function selectOrder(id: number) {
  currentOrderId.value = id
  planResult.value = null
  try {
    const detail = await outboundApi.detail(id)
    items.value = detail.items ?? []
    // 若单据已有规划结果，直接还原展示
    if (detail.order?.routeAlgo && detail.order?.routeDistance) {
      strategy.value = detail.order.routeAlgo
      planResult.value = {
        orderId: id,
        orderNo: detail.order.orderNo,
        strategy: detail.order.routeAlgo,
        strategyLabel:
          PICKING_STRATEGIES.find((s) => s.value === detail.order.routeAlgo)?.label ?? '',
        pickPointCount: items.value.length,
        distance: Number(detail.order.routeDistance),
        elapsedMillis: 0,
        updatedItems: items.value.length,
        route: parseRouteDetail(detail.order.routeDetail),
        comparison: {}
      }
    }
  } catch {
    items.value = []
  }
}

/** 解析后端保存的路径 JSON（[[x,y],...] 形式） */
function parseRouteDetail(text?: string): Array<{ aisleNo: number; rowNo: number }> {
  if (!text) return []
  try {
    const arr = JSON.parse(text) as number[][]
    return arr.map((p) => ({ aisleNo: p[0], rowNo: p[1] }))
  } catch {
    return []
  }
}

async function planRoute() {
  if (!currentOrderId.value) {
    ElMessage.warning('请先选择一张出库单')
    return
  }
  planning.value = true
  try {
    const res = await outboundApi.planRoute(currentOrderId.value, strategy.value)
    planResult.value = res
    ElMessage.success(
      `${res.strategyLabel} 规划完成，路径长度 ${res.distance} 米`
    )
    // 重新拉取明细以刷新拣货顺序
    const detail = await outboundApi.detail(currentOrderId.value)
    items.value = detail.items ?? []
    await loadOrders()
  } finally {
    planning.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadWarehouse(), loadOrders()])
})
</script>

<template>
  <div class="page-root picking">
    <!-- 操作区 -->
    <div class="wms-card">
      <div class="wms-card__title">拣货路径规划</div>

      <div class="picking__form">
        <div class="picking__field">
          <label>选择出库单</label>
          <ElSelect
            v-model="currentOrderId"
            placeholder="请选择待拣货的出库单"
            class="picking__select"
            filterable
            @change="selectOrder"
          >
            <ElOption
              v-for="o in availableOrders"
              :key="o.id"
              :value="o.id"
              :label="`${o.orderNo}（${formatQuantity(o.totalQuantity)} 件 · ${ORDER_STATUS.OUTBOUND[o.status as 0 | 1 | 2 | 9]?.label ?? ''}）`"
            />
          </ElSelect>
        </div>

        <div class="picking__field">
          <label>路径规划算法</label>
          <ElSelect v-model="strategy" class="picking__select">
            <ElOption
              v-for="s in PICKING_STRATEGIES"
              :key="s.value"
              :value="s.value"
              :label="s.label"
            >
              <div class="picking__option">
                <span class="picking__option-label">{{ s.label }}</span>
                <span class="picking__option-desc">{{ s.desc }}</span>
              </div>
            </ElOption>
          </ElSelect>
        </div>

        <ElButton type="primary" :loading="planning" :icon="'Guide'" @click="planRoute">
          规划路径
        </ElButton>
        <ElButton :icon="'Refresh'" @click="loadOrders">刷新单据</ElButton>
      </div>

      <div v-if="currentOrder" class="picking__summary">
        <span>单据：<b>{{ currentOrder.orderNo }}</b></span>
        <span>客户：{{ currentOrder.customer || '—' }}</span>
        <span>商品行数：{{ items.length }}</span>
        <span>总数量：{{ formatQuantity(currentOrder.totalQuantity) }}</span>
        <span>创建时间：{{ formatDateTime(currentOrder.createTime) }}</span>
      </div>
    </div>

    <!-- 规划结果 -->
    <div v-if="planResult" class="picking__result">
      <div class="picking__metric">
        <div class="picking__metric-label">路径长度</div>
        <div class="picking__metric-value">
          {{ planResult.distance }}<span class="picking__metric-unit">米</span>
        </div>
      </div>
      <div class="picking__metric">
        <div class="picking__metric-label">拣货点数</div>
        <div class="picking__metric-value">
          {{ planResult.pickPointCount }}<span class="picking__metric-unit">个</span>
        </div>
      </div>
      <div class="picking__metric">
        <div class="picking__metric-label">使用算法</div>
        <div class="picking__metric-text">{{ planResult.strategyLabel }}</div>
      </div>
      <div class="picking__metric">
        <div class="picking__metric-label">算法耗时</div>
        <div class="picking__metric-text">
          {{ planResult.elapsedMillis ? planResult.elapsedMillis + ' ms' : '—' }}
        </div>
      </div>
      <div v-if="currentOrder?.routeDistance" class="picking__metric">
        <div class="picking__metric-label">单据记录距离</div>
        <div class="picking__metric-text">
          {{ formatDistance(Number(currentOrder.routeDistance)) }}
        </div>
      </div>
    </div>

    <!-- 库位平面图与路径 -->
    <div class="wms-card">
      <div class="wms-card__title">
        库位平面图与拣货路径
        <ElTag v-if="warehouse" size="small" effect="plain" type="info">
          {{ warehouse.aisleCount }} 巷道 × {{ warehouse.rowCount }} 纵深
        </ElTag>
      </div>

      <PickPathMap
        v-if="warehouse"
        :aisles="warehouse.aisleCount"
        :rows="warehouse.rowCount"
        :route="routePoints"
        :picks="pickPoints"
        :distance="planResult?.distance ?? 0"
      />
      <EmptyState v-else description="未获取到仓库布局信息" />
    </div>

    <!-- 策略对比 -->
    <div v-if="planResult && Object.keys(planResult.comparison ?? {}).length" class="wms-card">
      <div class="wms-card__title">
        各策略路径长度对比
        <ElTooltip content="柱状越短表示路径越优，绿色为本次规划的最优策略" placement="top">
          <ElIcon class="picking__info"><QuestionFilled /></ElIcon>
        </ElTooltip>
      </div>
      <ChartBox :option="compareOption" height="220px" />
    </div>

    <!-- 拣货明细 -->
    <div class="wms-card">
      <div class="wms-card__title">拣货顺序明细</div>

      <!-- 明细由后端按拣货顺序返回，无需前端默认排序；列上保留可点击排序 -->
      <ElTable v-if="items.length" :data="items" stripe size="small">
        <ElTableColumn prop="pickSeq" label="顺序" width="76" align="center" sortable>
          <template #default="{ row }">
            <span v-if="row.pickSeq" class="picking__seq">{{ row.pickSeq }}</span>
            <span v-else class="picking__seq-empty">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="productCode" label="商品编码" width="110" />
        <ElTableColumn prop="productName" label="商品名称" min-width="150" show-overflow-tooltip />
        <ElTableColumn prop="locationCode" label="货位" width="100" />
        <ElTableColumn label="坐标" width="100" align="center">
          <template #default="{ row }">
            <span class="num">({{ row.aisleNo }}, {{ row.rowNo }})</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="距出入口" width="110" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatDistance(row.distance) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="数量" width="90" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatQuantity(row.quantity) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="拣货状态" width="96" align="center">
          <template #default="{ row }">
            <ElTag :type="row.picked === 1 ? 'success' : 'info'" size="small">
              {{ row.picked === 1 ? '已拣' : '待拣' }}
            </ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
      <EmptyState v-else description="请选择一张出库单以查看拣货明细" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.picking {
  &__form {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 12px;
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: 5px;

    label {
      font-size: 12px;
      color: #909399;
    }
  }

  &__select {
    width: 280px;
  }

  &__option {
    display: flex;
    align-items: baseline;
    gap: 8px;
  }

  &__option-label {
    font-size: 13px;
  }

  &__option-desc {
    font-size: 11px;
    color: #a8abb2;
  }

  &__summary {
    display: flex;
    flex-wrap: wrap;
    gap: 18px;
    margin-top: 14px;
    padding-top: 12px;
    border-top: 1px dashed #ebeef5;
    font-size: 12px;
    color: #606266;

    b {
      color: var(--el-color-primary);
    }
  }

  &__result {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 12px;
    margin: 12px 0;
  }

  &__metric {
    padding: 14px 16px;
    background: #fff;
    border-radius: var(--wms-card-radius);
    box-shadow: var(--wms-shadow-sm);
  }

  &__metric-label {
    font-size: 12px;
    color: #909399;
  }

  &__metric-value {
    margin-top: 4px;
    font-size: 24px;
    font-weight: 700;
    color: var(--el-color-primary);
    line-height: 1.2;
  }

  &__metric-unit {
    margin-left: 3px;
    font-size: 12px;
    font-weight: 400;
    color: #909399;
  }

  &__metric-text {
    margin-top: 6px;
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  &__info {
    margin-left: 4px;
    color: #c0c4cc;
    cursor: help;
    font-size: 13px;
  }

  &__seq {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 22px;
    height: 22px;
    padding: 0 5px;
    border-radius: 11px;
    background: var(--el-color-primary);
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    font-family: 'Consolas', monospace;
  }

  &__seq-empty {
    color: #c0c4cc;
  }
}

@media (max-width: 1200px) {
  .picking__result {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .picking__result {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .picking__select {
    width: 100%;
  }
}
</style>
