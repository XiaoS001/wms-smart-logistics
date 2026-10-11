<script setup lang="ts">
/**
 * 入库管理。
 *
 * 本页面是「算法嵌入业务」的直接体现：
 * 创建入库单时会调用货位分配算法为每个商品推荐目标货位，
 * 详情中把推荐理由逐行展示出来，让使用者不仅知道"放哪里"，还知道"为什么放这里"。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { inboundApi } from '@/api/inbound'
import { productApi } from '@/api/product'
import { useTable } from '@/composables/useTable'
import { useAppStore } from '@/stores/app'
import { ABC_COLORS, ORDER_STATUS, SLOTTING_STRATEGIES } from '@/utils/constants'
import { formatDateTime, formatDistance, formatQuantity } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'
import type { BaseProduct } from '@/types/base'
import type { BizInboundItem, BizInboundOrder } from '@/types/business'

const appStore = useAppStore()

/* ------------------------------ 列表 ------------------------------ */
const filters = reactive({
  status: 'all' as string
})

const {
  loading,
  pageList,
  total,
  currentPage,
  pageSize,
  refresh
} = useTable<BizInboundOrder>({
  fetcher: () => inboundApi.list({ warehouseId: appStore.currentWarehouseId, limit: 500 }),
  filter: (row) => {
    if (filters.status === 'all') return true
    return row.status === Number(filters.status)
  }
})

/** 状态展示配置 */
function statusOf(status: number) {
  return (
    ORDER_STATUS.INBOUND[status as 0 | 1 | 2 | 9] ?? { label: '未知', type: 'info' }
  )
}

/* ------------------------------ 新增入库单 ------------------------------ */
const createVisible = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()
const products = ref<BaseProduct[]>([])

const createForm = reactive({
  supplier: '',
  useSlottingSuggest: true,
  strategy: 'frequency-greedy',
  remark: '',
  items: [] as Array<{ productId: number | undefined; planQuantity: number; unitPrice?: number; batchNo: string }>
})

const createRules: FormRules = {
  supplier: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }]
}

function addItem() {
  createForm.items.push({
    productId: undefined,
    planQuantity: 1,
    unitPrice: undefined,
    batchNo: ''
  })
}

function removeItem(index: number) {
  createForm.items.splice(index, 1)
}

function openCreate() {
  createForm.supplier = ''
  createForm.useSlottingSuggest = true
  createForm.strategy = 'frequency-greedy'
  createForm.remark = ''
  createForm.items = []
  addItem()
  createVisible.value = true
}

/** 计划总数量与总金额，实时反馈 */
const createTotal = computed(() => {
  let qty = 0
  let amount = 0
  createForm.items.forEach((i) => {
    qty += Number(i.planQuantity) || 0
    amount += (Number(i.planQuantity) || 0) * (Number(i.unitPrice) || 0)
  })
  return { qty, amount }
})

/** 可选商品：优先展示出库频次高的商品（算法的输入顺序） */
const productOptions = computed(() =>
  [...products.value].sort((a, b) => (b.pickFreq ?? 0) - (a.pickFreq ?? 0))
)

function productOf(id?: number) {
  return products.value.find((p) => p.id === id)
}

async function submitCreate() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!createForm.items.length) {
    ElMessage.warning('请至少添加一行入库明细')
    return
  }
  const invalid = createForm.items.find((i) => !i.productId || !i.planQuantity || i.planQuantity <= 0)
  if (invalid) {
    ElMessage.warning('请为每行选择商品并填写大于 0 的计划数量')
    return
  }

  creating.value = true
  try {
    const order = await inboundApi.create({
      warehouseId: appStore.currentWarehouseId,
      supplier: createForm.supplier,
      useSlottingSuggest: createForm.useSlottingSuggest,
      strategy: createForm.strategy,
      remark: createForm.remark,
      items: createForm.items.map((i) => ({
        productId: i.productId as number,
        planQuantity: Number(i.planQuantity),
        unitPrice: i.unitPrice ? Number(i.unitPrice) : undefined,
        batchNo: i.batchNo || undefined
      }))
    })
    ElMessage.success(
      createForm.useSlottingSuggest
        ? `入库单 ${order.orderNo} 已创建，并已由算法推荐货位`
        : `入库单 ${order.orderNo} 已创建`
    )
    createVisible.value = false
    await refresh()
    // 直接打开详情，让用户看到推荐结果
    await openDetail(order.id)
  } finally {
    creating.value = false
  }
}

/* ------------------------------ 详情 ------------------------------ */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailOrder = ref<BizInboundOrder | null>(null)
const detailItems = ref<BizInboundItem[]>([])
const confirming = ref(false)

async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const detail = await inboundApi.detail(id)
    detailOrder.value = detail.order
    detailItems.value = detail.items ?? []
  } finally {
    detailLoading.value = false
  }
}

/** 推荐货位是否已由算法给出 */
const hasSuggestion = computed(() =>
  detailItems.value.some((i) => i.suggestReason && i.suggestReason.length > 0)
)

/** 推荐结果按距离升序，便于观察高频商品是否被安排在近端 */
const suggestedItems = computed(() =>
  [...detailItems.value].sort((a, b) => (a.distance ?? 0) - (b.distance ?? 0))
)

async function confirmInbound() {
  if (!detailOrder.value) return
  try {
    await ElMessageBox.confirm(
      `确认完成入库单「${detailOrder.value.orderNo}」吗？\n确认后将写入库存、把货位标记为占用，且不可撤销。`,
      '入库确认',
      { type: 'warning', confirmButtonText: '确认入库', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  confirming.value = true
  try {
    const res = await inboundApi.confirm(detailOrder.value.id)
    ElMessage.success(
      `入库完成：${res.stockedItems} 行已入库，占用 ${res.occupiedLocations} 个货位`
    )
    await openDetail(detailOrder.value.id)
    await refresh()
  } finally {
    confirming.value = false
  }
}

async function cancelOrder(row: BizInboundOrder) {
  try {
    await ElMessageBox.confirm(`确定要取消入库单「${row.orderNo}」吗？`, '取消确认', {
      type: 'warning',
      confirmButtonText: '确定取消',
      cancelButtonText: '返回'
    })
  } catch {
    return
  }
  try {
    await inboundApi.cancel(row.id)
    ElMessage.success('入库单已取消')
    await refresh()
  } catch {
    // 已完成的单据会被后端拒绝
  }
}

async function loadProducts() {
  try {
    products.value = await productApi.list({ limit: 2000 })
  } catch {
    products.value = []
  }
}

onMounted(loadProducts)
</script>

<template>
  <div class="page-root inbound-page">
    <!-- 工具条 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <ElSelect v-model="filters.status" placeholder="单据状态" style="width: 150px">
          <ElOption label="全部状态" value="all" />
          <ElOption label="待入库" value="0" />
          <ElOption label="已完成" value="2" />
          <ElOption label="已取消" value="9" />
        </ElSelect>

        <ElButton :icon="'Refresh'" :loading="loading" @click="refresh">刷新</ElButton>

        <div class="wms-toolbar__right">
          <ElButton type="primary" :icon="'Plus'" @click="openCreate">新建入库单</ElButton>
        </div>
      </div>

      <div class="inbound-page__hint">
        <ElIcon><MagicStick /></ElIcon>
        <span>
          新建入库单时可开启<b>算法推荐货位</b>：系统会依据商品出库频次与 ABC 分类，
          自动为每个商品分配目标货位并给出推荐理由，人工指定的货位优先于算法推荐。
        </span>
      </div>
    </div>

    <!-- 单据列表 -->
    <div class="wms-card">
      <div class="wms-card__title">
        入库单列表
        <ElTag size="small" type="info" effect="plain">共 {{ total }} 条</ElTag>
      </div>

      <ElTable v-loading="loading" :data="pageList" stripe size="small">
        <ElTableColumn prop="orderNo" label="入库单号" width="160" fixed>
          <template #default="{ row }">
            <span class="inbound-page__no">{{ row.orderNo }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="supplier" label="供应商" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplier || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="总数量" width="110" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatQuantity(row.totalQuantity) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="总金额" width="120" align="right">
          <template #default="{ row }">
            <span class="num">¥{{ formatQuantity(row.totalAmount) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="算法推荐" width="100" align="center">
          <template #default="{ row }">
            <ElTag v-if="row.useSlottingSuggest === 1" type="success" size="small" effect="plain">
              已启用
            </ElTag>
            <span v-else class="inbound-page__muted">未启用</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="96" align="center">
          <template #default="{ row }">
            <ElTag :type="(statusOf(row.status).type as any)" size="small">
              {{ statusOf(row.status).label }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="创建时间" width="160">
          <template #default="{ row }">
            <span class="inbound-page__time">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="remark" label="备注" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton link type="primary" size="small" @click="openDetail(row.id)">详情</ElButton>
            <ElButton
              v-if="row.status === 0"
              link
              type="success"
              size="small"
              @click="openDetail(row.id)"
            >
              入库确认
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
          <EmptyState description="暂无入库单，点击右上角新建" />
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

    <!-- 新建入库单 -->
    <ElDialog
      v-model="createVisible"
      title="新建入库单"
      width="920px"
      :close-on-click-modal="false"
    >
      <ElForm ref="formRef" :model="createForm" :rules="createRules" label-width="100px">
        <ElRow :gutter="12">
          <ElCol :span="10">
            <ElFormItem label="供应商" prop="supplier">
              <ElInput v-model="createForm.supplier" placeholder="请输入供应商名称" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="14">
            <ElFormItem label="算法推荐货位">
              <ElSwitch v-model="createForm.useSlottingSuggest" />
              <span class="inbound-page__inline-tip">
                {{
                  createForm.useSlottingSuggest
                    ? '系统将按出库频次与 ABC 分类推荐目标货位'
                    : '仅保存单据，货位留待人工指定'
                }}
              </span>
            </ElFormItem>
          </ElCol>

          <ElCol v-if="createForm.useSlottingSuggest" :span="12">
            <ElFormItem label="分配策略">
              <ElSelect v-model="createForm.strategy" style="width: 100%">
                <ElOption
                  v-for="s in SLOTTING_STRATEGIES"
                  :key="s.value"
                  :value="s.value"
                  :label="s.label"
                />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :span="createForm.useSlottingSuggest ? 12 : 24">
            <ElFormItem label="备注">
              <ElInput v-model="createForm.remark" placeholder="选填" />
            </ElFormItem>
          </ElCol>
        </ElRow>

        <ElDivider content-position="left">
          <span class="inbound-page__divider-text">入库明细</span>
        </ElDivider>

        <ElTable :data="createForm.items" size="small" class="inbound-page__items">
          <ElTableColumn label="商品" min-width="220">
            <template #default="{ row }">
              <ElSelect
                v-model="row.productId"
                placeholder="选择商品（按期次降序）"
                filterable
                style="width: 100%"
              >
                <ElOption
                  v-for="p in productOptions"
                  :key="p.id"
                  :value="p.id"
                  :label="`${p.productCode} ${p.productName}（频次 ${p.pickFreq}）`"
                />
              </ElSelect>
            </template>
          </ElTableColumn>

          <ElTableColumn label="ABC" width="80" align="center">
            <template #default="{ row }">
              <ElTag
                v-if="productOf(row.productId)"
                :color="ABC_COLORS[productOf(row.productId)?.abcClass ?? 'C']"
                effect="dark"
                size="small"
              >
                {{ productOf(row.productId)?.abcClass }} 类
              </ElTag>
              <span v-else class="inbound-page__muted">—</span>
            </template>
          </ElTableColumn>

          <ElTableColumn label="计划数量" width="130">
            <template #default="{ row }">
              <ElInputNumber v-model="row.planQuantity" :min="1" :precision="0" size="small" style="width: 100%" />
            </template>
          </ElTableColumn>

          <ElTableColumn label="单价" width="120">
            <template #default="{ row }">
              <ElInputNumber v-model="row.unitPrice" :min="0" :precision="2" size="small" style="width: 100%" />
            </template>
          </ElTableColumn>

          <ElTableColumn label="批次号" width="140">
            <template #default="{ row }">
              <ElInput v-model="row.batchNo" placeholder="选填" size="small" />
            </template>
          </ElTableColumn>

          <ElTableColumn label="操作" width="70" align="center">
            <template #default="{ $index }">
              <ElButton link type="danger" size="small" @click="removeItem($index)">删除</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>

        <div class="inbound-page__items-footer">
          <ElButton size="small" :icon="'Plus'" @click="addItem">添加明细行</ElButton>
          <div class="inbound-page__totals">
            <span>合计数量：<b>{{ createTotal.qty }}</b></span>
            <span>合计金额：<b>¥{{ createTotal.amount.toFixed(2) }}</b></span>
          </div>
        </div>
      </ElForm>

      <template #footer>
        <ElButton @click="createVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="creating" @click="submitCreate">
          {{ createForm.useSlottingSuggest ? '创建并推荐货位' : '创建入库单' }}
        </ElButton>
      </template>
    </ElDialog>

    <!-- 单据详情 -->
    <ElDrawer v-model="detailVisible" title="入库单详情" size="820px">
      <div v-loading="detailLoading">
        <template v-if="detailOrder">
          <ElDescriptions :column="3" border size="small" class="inbound-page__detail-head">
            <ElDescriptionsItem label="入库单号">
              <span class="inbound-page__no">{{ detailOrder.orderNo }}</span>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="供应商">
              {{ detailOrder.supplier || '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="状态">
              <ElTag :type="(statusOf(detailOrder.status).type as any)" size="small">
                {{ statusOf(detailOrder.status).label }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="总数量">
              {{ formatQuantity(detailOrder.totalQuantity) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="总金额">
              ¥{{ formatQuantity(detailOrder.totalAmount) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="创建时间">
              {{ formatDateTime(detailOrder.createTime) }}
            </ElDescriptionsItem>
          </ElDescriptions>

          <ElAlert
            v-if="hasSuggestion"
            type="success"
            :closable="false"
            show-icon
            class="inbound-page__suggest-alert"
          >
            <template #title>算法已推荐目标货位</template>
            <div class="inbound-page__suggest-body">
              下表按货位到出入口的距离升序排列，可见出库频次越高的商品被安排在越近的货位。
              这是货位分配算法"高频近端"原则的直接体现。
            </div>
          </ElAlert>

          <div class="inbound-page__detail-title">
            入库明细（{{ detailItems.length }} 行）
          </div>

          <ElTable :data="suggestedItems" size="small" stripe>
            <ElTableColumn prop="productCode" label="商品编码" width="110" />
            <ElTableColumn prop="productName" label="商品名称" min-width="140" show-overflow-tooltip />
            <ElTableColumn label="频次" width="84" align="right">
              <template #default="{ row }">
                <span class="num inbound-page__freq">{{ formatQuantity(row.pickFreq) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="ABC" width="76" align="center">
              <template #default="{ row }">
                <ElTag :color="ABC_COLORS[row.abcClass]" effect="dark" size="small">
                  {{ row.abcClass }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="推荐货位" width="100" align="center">
              <template #default="{ row }">
                <ElTag v-if="row.locationCode" type="primary" size="small" effect="plain">
                  {{ row.locationCode }}
                </ElTag>
                <span v-else class="inbound-page__muted">未指定</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="距出入口" width="106" align="right">
              <template #default="{ row }">
                <span v-if="row.distance" class="num">{{ formatDistance(row.distance) }}</span>
                <span v-else class="inbound-page__muted">—</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="计划数量" width="96" align="right">
              <template #default="{ row }">
                <span class="num">{{ formatQuantity(row.planQuantity) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="实收数量" width="96" align="right">
              <template #default="{ row }">
                <span class="num">{{ formatQuantity(row.actualQuantity) }}</span>
              </template>
            </ElTableColumn>
          </ElTable>

          <!-- 推荐理由：本页面的重点，逐行展示算法的判断依据 -->
          <template v-if="hasSuggestion">
            <div class="inbound-page__detail-title">算法推荐理由</div>
            <div class="inbound-page__reasons">
              <div
                v-for="item in suggestedItems.filter((i) => i.suggestReason)"
                :key="item.id"
                class="inbound-page__reason"
              >
                <div class="inbound-page__reason-head">
                  <ElTag :color="ABC_COLORS[item.abcClass]" effect="dark" size="small">
                    {{ item.abcClass }} 类
                  </ElTag>
                  <span class="inbound-page__reason-product">
                    {{ item.productCode }} {{ item.productName }}
                  </span>
                  <span class="inbound-page__reason-location">
                    → {{ item.locationCode }}（{{ formatDistance(item.distance) }}）
                  </span>
                </div>
                <div class="inbound-page__reason-text">{{ item.suggestReason }}</div>
              </div>
            </div>
          </template>
        </template>

        <EmptyState v-else-if="!detailLoading" description="未获取到单据详情" />
      </div>

      <template #footer>
        <div class="inbound-page__drawer-footer">
          <ElButton @click="detailVisible = false">关闭</ElButton>
          <ElButton
            v-if="detailOrder && detailOrder.status === 0"
            type="primary"
            :loading="confirming"
            @click="confirmInbound"
          >
            确认入库
          </ElButton>
        </div>
      </template>
    </ElDrawer>
  </div>
</template>

<style scoped lang="scss">
.inbound-page {
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

  &__inline-tip {
    margin-left: 10px;
    font-size: 12px;
    color: #909399;
  }

  &__divider-text {
    font-size: 13px;
    color: #606266;
  }

  &__items {
    margin-bottom: 10px;
  }

  &__items-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__totals {
    display: flex;
    gap: 18px;
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

  &__suggest-alert {
    margin-bottom: 14px;
  }

  &__suggest-body {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.75;
  }

  &__detail-title {
    margin: 16px 0 8px;
    font-size: 13px;
    font-weight: 600;
    color: #4b5563;
  }

  &__freq {
    font-weight: 600;
    color: #1f2937;
  }

  &__reasons {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__reason {
    padding: 10px 12px;
    border-radius: 6px;
    background: #f8fafc;
    border-left: 3px solid var(--el-color-primary);
  }

  &__reason-head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__reason-product {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
  }

  &__reason-location {
    font-size: 12px;
    color: var(--el-color-primary);
    font-family: 'Consolas', monospace;
  }

  &__reason-text {
    margin-top: 5px;
    font-size: 12px;
    line-height: 1.7;
    color: #606266;
  }

  &__drawer-footer {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
  }
}
</style>
