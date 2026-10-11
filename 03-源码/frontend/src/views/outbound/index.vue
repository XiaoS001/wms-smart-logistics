<script setup lang="ts">
/**
 * 出库管理。
 *
 * 覆盖出库的完整链路：
 *   创建出库单（后端按「就近出库」原则从最近货位分配库存）
 *   → 规划拣货路径（可在此页直接触发，也可到拣货路径规划页做可视化对比）
 *   → 出库确认（扣减库存、释放空货位、累加商品出库频次）。
 *
 * 页面会明确提示"库存不足时的具体差额"，以及"出库频次回写"这一闭环，
 * 因为频次正是货位分配算法的输入，出库会反过来影响后续的货位优化。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { outboundApi } from '@/api/outbound'
import { productApi } from '@/api/product'
import { stockApi } from '@/api/stock'
import { useTable } from '@/composables/useTable'
import { useAppStore } from '@/stores/app'
import { ORDER_STATUS, PICKING_STRATEGIES } from '@/utils/constants'
import { formatDateTime, formatDistance, formatQuantity } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'
import PickPathMap from '@/components/warehouse/PickPathMap.vue'
import type { BaseProduct, BaseWarehouse } from '@/types/base'
import type { BizOutboundItem, BizOutboundOrder, PlanRouteResult, StockSummary } from '@/types/business'
import { warehouseApi } from '@/api/warehouse'

const appStore = useAppStore()

/* ------------------------------ 列表 ------------------------------ */
const filters = reactive({ status: 'all' as string })

const { loading, pageList, total, currentPage, pageSize, refresh } = useTable<BizOutboundOrder>({
  fetcher: () => outboundApi.list({ warehouseId: appStore.currentWarehouseId, limit: 500 }),
  filter: (row) => (filters.status === 'all' ? true : row.status === Number(filters.status))
})

function statusOf(status: number) {
  return ORDER_STATUS.OUTBOUND[status as 0 | 1 | 2 | 9] ?? { label: '未知', type: 'info' }
}

/* ------------------------------ 新建出库单 ------------------------------ */
const createVisible = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()
const products = ref<BaseProduct[]>([])
const stockSummary = ref<StockSummary[]>([])
const warehouse = ref<BaseWarehouse | null>(null)

const createForm = reactive({
  customer: '',
  remark: '',
  items: [] as Array<{ productId: number | undefined; quantity: number }>
})

const createRules: FormRules = {
  customer: [{ required: true, message: '请输入客户名称', trigger: 'blur' }]
}

function addItem() {
  createForm.items.push({ productId: undefined, quantity: 1 })
}

function removeItem(index: number) {
  createForm.items.splice(index, 1)
}

async function openCreate() {
  createForm.customer = ''
  createForm.remark = ''
  createForm.items = []
  addItem()
  createVisible.value = true
  // 打开时刷新库存，便于在选择商品时给出可用量提示
  try {
    stockSummary.value = await stockApi.summary(appStore.currentWarehouseId)
  } catch {
    stockSummary.value = []
  }
}

/** 某商品的可用库存（用于表单提示与前端预警） */
function availableOf(productId?: number): number {
  if (!productId) return 0
  const row = stockSummary.value.find((s) => s.productId === productId)
  return row?.totalQuantity ?? 0
}

/** 可选商品：仅有库存的商品可选出库 */
const productOptions = computed(() =>
  products.value.filter((p) => availableOf(p.id) > 0)
)

const createTotal = computed(() =>
  createForm.items.reduce((sum, i) => sum + (Number(i.quantity) || 0), 0)
)

/** 前端预警：某行需求超过可用库存 */
const overStockRows = computed(() =>
  createForm.items
    .map((i, index) => ({ index, productId: i.productId, need: Number(i.quantity) || 0 }))
    .filter((r) => r.productId && r.need > availableOf(r.productId))
)

async function submitCreate() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!createForm.items.length) {
    ElMessage.warning('请至少添加一行出库明细')
    return
  }
  const invalid = createForm.items.find((i) => !i.productId || !i.quantity || i.quantity <= 0)
  if (invalid) {
    ElMessage.warning('请为每行选择商品并填写大于 0 的出库数量')
    return
  }

  creating.value = true
  try {
    const order = await outboundApi.create({
      warehouseId: appStore.currentWarehouseId,
      customer: createForm.customer,
      remark: createForm.remark,
      items: createForm.items.map((i) => ({
        productId: i.productId as number,
        quantity: Number(i.quantity)
      }))
    })
    ElMessage.success(`出库单 ${order.orderNo} 已创建，已按就近原则分配货位`)
    createVisible.value = false
    await refresh()
    await openDetail(order.id)
  } finally {
    creating.value = false
  }
}

/* ------------------------------ 详情与路径 ------------------------------ */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailOrder = ref<BizOutboundOrder | null>(null)
const detailItems = ref<BizOutboundItem[]>([])
const planning = ref(false)
const confirming = ref(false)
const planResult = ref<PlanRouteResult | null>(null)
const routeStrategy = ref('ant-colony')

async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  planResult.value = null
  try {
    const detail = await outboundApi.detail(id)
    detailOrder.value = detail.order
    detailItems.value = detail.items ?? []
    if (detail.order?.routeAlgo) {
      routeStrategy.value = detail.order.routeAlgo
      planResult.value = {
        orderId: id,
        orderNo: detail.order.orderNo,
        strategy: detail.order.routeAlgo,
        strategyLabel:
          PICKING_STRATEGIES.find((s) => s.value === detail.order.routeAlgo)?.label ?? '',
        pickPointCount: detailItems.value.length,
        distance: Number(detail.order.routeDistance ?? 0),
        elapsedMillis: 0,
        updatedItems: detailItems.value.length,
        route: parseRoute(detail.order.routeDetail),
        comparison: {}
      }
    }
  } finally {
    detailLoading.value = false
  }
}

function parseRoute(text?: string): Array<{ aisleNo: number; rowNo: number }> {
  if (!text) return []
  try {
    return (JSON.parse(text) as number[][]).map((p) => ({ aisleNo: p[0], rowNo: p[1] }))
  } catch {
    return []
  }
}

/** 拣货点：用于在平面图上标出全部需要访问的货位 */
const pickPoints = computed(() =>
  detailItems.value
    .filter((i) => i.aisleNo && i.rowNo)
    .map((i) => ({ aisleNo: i.aisleNo, rowNo: i.rowNo, label: i.productCode }))
)

const routePoints = computed(() => planResult.value?.route ?? [])

/** 就近分配的体现：明细按货位距离升序 */
const sortedItems = computed(() =>
  [...detailItems.value].sort((a, b) => (a.distance ?? 0) - (b.distance ?? 0))
)

async function planRoute() {
  if (!detailOrder.value) return
  planning.value = true
  try {
    const res = await outboundApi.planRoute(detailOrder.value.id, routeStrategy.value)
    planResult.value = res
    ElMessage.success(`${res.strategyLabel} 规划完成，路径长度 ${res.distance} 米`)
    const detail = await outboundApi.detail(detailOrder.value.id)
    detailItems.value = detail.items ?? []
    detailOrder.value = detail.order
    await refresh()
  } finally {
    planning.value = false
  }
}

async function confirmOutbound() {
  if (!detailOrder.value) return
  try {
    await ElMessageBox.confirm(
      `确认完成出库单「${detailOrder.value.orderNo}」吗？\n确认后将扣减库存、释放已清空的货位，并累加商品出库频次。`,
      '出库确认',
      { type: 'warning', confirmButtonText: '确认出库', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  confirming.value = true
  try {
    const res = await outboundApi.confirm(detailOrder.value.id)
    ElMessage.success(
      `出库完成：${res.shippedItems} 行已出库，释放 ${res.freedLocations} 个货位`
    )
    await openDetail(detailOrder.value.id)
    await refresh()
  } finally {
    confirming.value = false
  }
}

async function cancelOrder(row: BizOutboundOrder) {
  try {
    await ElMessageBox.confirm(`确定要取消出库单「${row.orderNo}」吗？`, '取消确认', {
      type: 'warning',
      confirmButtonText: '确定取消',
      cancelButtonText: '返回'
    })
  } catch {
    return
  }
  try {
    await outboundApi.cancel(row.id)
    ElMessage.success('出库单已取消')
    await refresh()
  } catch {
    // 已完成的单据会被后端拒绝
  }
}

onMounted(async () => {
  try {
    products.value = await productApi.list({ limit: 2000 })
  } catch {
    products.value = []
  }
  try {
    warehouse.value = await warehouseApi.detail(appStore.currentWarehouseId)
  } catch {
    warehouse.value = null
  }
})
</script>

<template>
  <div class="page-root outbound-page">
    <!-- 工具条 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <ElSelect v-model="filters.status" placeholder="单据状态" style="width: 150px">
          <ElOption label="全部状态" value="all" />
          <ElOption label="待拣货" value="0" />
          <ElOption label="拣货中" value="1" />
          <ElOption label="已完成" value="2" />
          <ElOption label="已取消" value="9" />
        </ElSelect>

        <ElButton :icon="'Refresh'" :loading="loading" @click="refresh">刷新</ElButton>

        <div class="wms-toolbar__right">
          <ElButton type="primary" :icon="'Plus'" @click="openCreate">新建出库单</ElButton>
        </div>
      </div>

      <div class="outbound-page__hint">
        <ElIcon><Guide /></ElIcon>
        <span>
          创建出库单时，系统按<b>就近出库</b>原则从离出入口最近的货位分配库存；
          规划拣货路径后，拣货顺序会写回明细。出库确认会<b>累加商品出库频次</b>，
          而频次正是货位分配算法的输入，由此形成优化闭环。
        </span>
      </div>
    </div>

    <!-- 列表 -->
    <div class="wms-card">
      <div class="wms-card__title">
        出库单列表
        <ElTag size="small" type="info" effect="plain">共 {{ total }} 条</ElTag>
      </div>

      <ElTable v-loading="loading" :data="pageList" stripe size="small">
        <ElTableColumn prop="orderNo" label="出库单号" width="170" fixed>
          <template #default="{ row }">
            <span class="outbound-page__no">{{ row.orderNo }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="customer" label="客户" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customer || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="总数量" width="100" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatQuantity(row.totalQuantity) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="路径算法" width="120" align="center">
          <template #default="{ row }">
            <ElTag v-if="row.routeAlgo" size="small" effect="plain" type="primary">
              {{ PICKING_STRATEGIES.find((s) => s.value === row.routeAlgo)?.label ?? row.routeAlgo }}
            </ElTag>
            <span v-else class="outbound-page__muted">未规划</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="路径长度" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.routeDistance" class="num outbound-page__distance">
              {{ formatDistance(Number(row.routeDistance)) }}
            </span>
            <span v-else class="outbound-page__muted">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="92" align="center">
          <template #default="{ row }">
            <ElTag :type="(statusOf(row.status).type as any)" size="small">
              {{ statusOf(row.status).label }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="创建时间" width="156">
          <template #default="{ row }">
            <span class="outbound-page__time">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="190" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton link type="primary" size="small" @click="openDetail(row.id)">详情</ElButton>
            <ElButton
              v-if="row.status === 1 || row.status === 0"
              link
              type="success"
              size="small"
              @click="openDetail(row.id)"
            >
              出库确认
            </ElButton>
            <ElButton
              v-if="row.status === 0"
              link
              type="danger"
              size="small"
              @click="cancelOrder(row)"
            >
              取消
            </ElButton>
          </template>
        </ElTableColumn>

        <template #empty>
          <EmptyState description="暂无出库单，点击右上角新建" />
        </template>
      </ElTable>

      <div v-if="total > pageSize" class="wms-pagination">
        <ElPagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
        />
      </div>
    </div>

    <!-- 新建出库单 -->
    <ElDialog v-model="createVisible" title="新建出库单" width="760px" :close-on-click-modal="false">
      <ElForm ref="formRef" :model="createForm" :rules="createRules" label-width="90px">
        <ElRow :gutter="12">
          <ElCol :span="12">
            <ElFormItem label="客户" prop="customer">
              <ElInput v-model="createForm.customer" placeholder="请输入客户名称" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="备注">
              <ElInput v-model="createForm.remark" placeholder="选填" />
            </ElFormItem>
          </ElCol>
        </ElRow>

        <ElAlert
          v-if="overStockRows.length"
          type="warning"
          :closable="false"
          show-icon
          class="outbound-page__warn"
        >
          <template #title>有 {{ overStockRows.length }} 行需求超过当前可用库存</template>
          <div class="outbound-page__warn-body">
            可先通过「入库管理」补货，或调整出库数量。提交时后端也会校验并给出具体差额。
          </div>
        </ElAlert>

        <ElDivider content-position="left">
          <span class="outbound-page__divider-text">出库明细（仅可选择有库存的商品）</span>
        </ElDivider>

        <ElTable :data="createForm.items" size="small">
          <ElTableColumn label="商品" min-width="240">
            <template #default="{ row }">
              <ElSelect
                v-model="row.productId"
                placeholder="选择商品"
                filterable
                style="width: 100%"
              >
                <ElOption
                  v-for="p in productOptions"
                  :key="p.id"
                  :value="p.id"
                  :label="`${p.productCode} ${p.productName}（可用 ${formatQuantity(availableOf(p.id))}）`"
                />
              </ElSelect>
            </template>
          </ElTableColumn>

          <ElTableColumn label="可用库存" width="110" align="right">
            <template #default="{ row }">
              <span class="num">{{ formatQuantity(availableOf(row.productId)) }}</span>
            </template>
          </ElTableColumn>

          <ElTableColumn label="出库数量" width="140">
            <template #default="{ row }">
              <ElInputNumber
                v-model="row.quantity"
                :min="1"
                :precision="0"
                size="small"
                style="width: 100%"
                :max="availableOf(row.productId) || 999999"
              />
            </template>
          </ElTableColumn>

          <ElTableColumn label="操作" width="70" align="center">
            <template #default="{ $index }">
              <ElButton link type="danger" size="small" @click="removeItem($index)">删除</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>

        <div class="outbound-page__items-footer">
          <ElButton size="small" :icon="'Plus'" @click="addItem">添加明细行</ElButton>
          <div class="outbound-page__totals">
            合计出库数量：<b>{{ createTotal }}</b>
          </div>
        </div>
      </ElForm>

      <template #footer>
        <ElButton @click="createVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="creating" @click="submitCreate">创建出库单</ElButton>
      </template>
    </ElDialog>

    <!-- 详情抽屉 -->
    <ElDrawer v-model="detailVisible" title="出库单详情" size="860px">
      <div v-loading="detailLoading">
        <template v-if="detailOrder">
          <ElDescriptions :column="3" border size="small" class="outbound-page__detail-head">
            <ElDescriptionsItem label="出库单号">
              <span class="outbound-page__no">{{ detailOrder.orderNo }}</span>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="客户">{{ detailOrder.customer || '—' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="状态">
              <ElTag :type="(statusOf(detailOrder.status).type as any)" size="small">
                {{ statusOf(detailOrder.status).label }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="总数量">
              {{ formatQuantity(detailOrder.totalQuantity) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="路径算法">
              {{ detailOrder.routeAlgo || '未规划' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="路径长度">
              {{
                detailOrder.routeDistance ? formatDistance(Number(detailOrder.routeDistance)) : '—'
              }}
            </ElDescriptionsItem>
          </ElDescriptions>

          <!-- 路径规划操作 -->
          <div class="outbound-page__plan-bar">
            <span class="outbound-page__plan-label">拣货路径规划算法</span>
            <ElSelect v-model="routeStrategy" size="small" style="width: 200px">
              <ElOption
                v-for="s in PICKING_STRATEGIES"
                :key="s.value"
                :value="s.value"
                :label="s.label"
              />
            </ElSelect>
            <ElButton
              type="primary"
              size="small"
              :icon="'Guide'"
              :loading="planning"
              :disabled="detailOrder.status === 2 || detailOrder.status === 9"
              @click="planRoute"
            >
              规划路径
            </ElButton>
            <span v-if="planResult" class="outbound-page__plan-result">
              当前路径 {{ planResult.distance }} 米 · {{ planResult.pickPointCount }} 个拣货点
            </span>
          </div>

          <!-- 库位平面图 -->
          <div v-if="warehouse" class="outbound-page__map">
            <PickPathMap
              :aisles="warehouse.aisleCount"
              :rows="warehouse.rowCount"
              :route="routePoints"
              :picks="pickPoints"
              :distance="planResult?.distance ?? 0"
            />
          </div>

          <!-- 明细 -->
          <div class="outbound-page__section-title">
            出库明细（按货位距离升序，体现就近出库）
          </div>
          <ElTable :data="sortedItems" size="small" stripe>
            <ElTableColumn prop="pickSeq" label="顺序" width="66" align="center">
              <template #default="{ row }">
                <span v-if="row.pickSeq" class="outbound-page__seq">{{ row.pickSeq }}</span>
                <span v-else class="outbound-page__muted">—</span>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="productCode" label="商品编码" width="106" />
            <ElTableColumn prop="productName" label="商品名称" min-width="130" show-overflow-tooltip />
            <ElTableColumn prop="locationCode" label="货位" width="94" align="center">
              <template #default="{ row }">
                <ElTag type="primary" size="small" effect="plain">{{ row.locationCode }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="距出入口" width="104" align="right">
              <template #default="{ row }">
                <span class="num outbound-page__distance">{{ formatDistance(row.distance) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="数量" width="86" align="right">
              <template #default="{ row }">
                <span class="num">{{ formatQuantity(row.quantity) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="拣货状态" width="90" align="center">
              <template #default="{ row }">
                <ElTag :type="row.picked === 1 ? 'success' : 'info'" size="small">
                  {{ row.picked === 1 ? '已拣' : '待拣' }}
                </ElTag>
              </template>
            </ElTableColumn>
          </ElTable>
        </template>

        <EmptyState v-else-if="!detailLoading" description="未获取到单据详情" />
      </div>

      <template #footer>
        <div class="outbound-page__drawer-footer">
          <ElButton @click="detailVisible = false">关闭</ElButton>
          <ElButton
            v-if="detailOrder && (detailOrder.status === 0 || detailOrder.status === 1)"
            type="primary"
            :loading="confirming"
            @click="confirmOutbound"
          >
            确认出库
          </ElButton>
        </div>
      </template>
    </ElDrawer>
  </div>
</template>

<style scoped lang="scss">
.outbound-page {
  &__hint {
    display: flex;
    align-items: flex-start;
    gap: 7px;
    margin-top: 12px;
    padding: 9px 12px;
    border-radius: 6px;
    background: #f0f7ff;
    font-size: 12px;
    line-height: 1.7;
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

  &__no {
    font-family: 'Consolas', monospace;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__time {
    font-size: 12px;
    color: #909399;
  }

  &__muted {
    color: #c0c4cc;
    font-size: 12px;
  }

  &__distance {
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__warn {
    margin-bottom: 12px;
  }

  &__warn-body {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.7;
  }

  &__divider-text {
    font-size: 13px;
    color: #606266;
  }

  &__items-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 10px;
  }

  &__totals {
    font-size: 12px;
    color: #606266;

    b {
      color: var(--el-color-primary);
      font-size: 14px;
    }
  }

  &__detail-head {
    margin-bottom: 14px;
  }

  &__plan-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    padding: 10px 12px;
    margin-bottom: 14px;
    border-radius: 6px;
    background: #f8fafc;
  }

  &__plan-label {
    font-size: 12px;
    color: #606266;
  }

  &__plan-result {
    font-size: 12px;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__map {
    margin-bottom: 14px;
  }

  &__section-title {
    margin: 14px 0 8px;
    font-size: 13px;
    font-weight: 600;
    color: #4b5563;
  }

  &__seq {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 22px;
    height: 22px;
    border-radius: 11px;
    background: var(--el-color-primary);
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    font-family: 'Consolas', monospace;
  }

  &__drawer-footer {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
  }
}
</style>
