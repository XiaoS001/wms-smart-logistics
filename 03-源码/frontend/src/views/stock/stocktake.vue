<script setup lang="ts">
/**
 * 库存盘点。
 *
 * 流程：生成盘点单（以当前库存为账面数量）→ 录入实盘数量 → 提交并可选调整库存。
 *
 * 盘点差异会写回库存表并同步货位占用状态，因此本页面是校正"账面与实物不一致"的入口。
 * 已调整的盘点记录不可重复提交，已调整的盘点单不可撤销，保证库存变动可追溯。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { stockApi } from '@/api/stock'
import { useAppStore } from '@/stores/app'
import { ORDER_STATUS } from '@/utils/constants'
import { formatDateTime, formatDistance, formatQuantity } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'
import type { BizStocktake } from '@/types/business'

const appStore = useAppStore()

const loading = ref(false)
const generating = ref(false)
const submitting = ref(false)

const records = ref<BizStocktake[]>([])
const currentNo = ref<string>('')
/** 录入中的实盘数量：id -> 数量 */
const actualInput = reactive<Record<number, number>>({})
const adjustStock = ref(true)

/** 盘点单号列表（按单号分组展示） */
const stocktakeNos = computed(() => {
  const set = new Set<string>()
  records.value.forEach((r) => set.add(r.stocktakeNo))
  return Array.from(set)
})

/** 当前单号的记录 */
const currentRecords = computed(() =>
  currentNo.value ? records.value.filter((r) => r.stocktakeNo === currentNo.value) : records.value
)

function statusOf(status: number) {
  return ORDER_STATUS.STOCKTAKE[status as 0 | 1 | 2] ?? { label: '未知', type: 'info' }
}

/** 差异计算：实盘 − 账面 */
function diffOf(row: BizStocktake): number {
  const actual = actualInput[row.id]
  if (actual === undefined || actual === null) return 0
  return Number(actual) - Number(row.bookQuantity)
}

/** 提交前的差异统计，帮助用户确认影响面 */
const diffSummary = computed(() => {
  let diffRows = 0
  let absSum = 0
  let netSum = 0
  currentRecords.value.forEach((r) => {
    const d = diffOf(r)
    if (d !== 0) {
      diffRows++
      absSum += Math.abs(d)
      netSum += d
    }
  })
  return { diffRows, absSum, netSum }
})

/** 是否所有记录都已录入实盘数量 */
const allFilled = computed(() =>
  currentRecords.value.length > 0 &&
  currentRecords.value.every((r) => actualInput[r.id] !== undefined && actualInput[r.id] !== null)
)

/** 是否存在已调整的记录（不可再次提交） */
const hasAdjusted = computed(() => currentRecords.value.some((r) => r.status === 2))

async function load() {
  loading.value = true
  try {
    records.value = await stockApi.listStocktake()
    // 默认选中最新的一张盘点单
    if (!currentNo.value && stocktakeNos.value.length) {
      currentNo.value = stocktakeNos.value[0]
    }
    // 回填已有实盘数量，便于查看历史盘点结果
    records.value.forEach((r) => {
      if (r.status !== 0 && actualInput[r.id] === undefined) {
        actualInput[r.id] = Number(r.actualQuantity)
      }
    })
  } finally {
    loading.value = false
  }
}

async function generate() {
  generating.value = true
  try {
    const res = await stockApi.generateStocktake(appStore.currentWarehouseId)
    ElMessage.success(`盘点单 ${res.stocktakeNo} 已生成，共 ${res.recordCount} 条记录`)
    Object.keys(actualInput).forEach((k) => delete actualInput[Number(k)])
    currentNo.value = res.stocktakeNo
    await load()
  } finally {
    generating.value = false
  }
}

/** 一键以账面数量填充：适用于"账实相符"的快速确认 */
function fillWithBook() {
  currentRecords.value.forEach((r) => {
    if (actualInput[r.id] === undefined || actualInput[r.id] === null) {
      actualInput[r.id] = Number(r.bookQuantity)
    }
  })
  ElMessage.info('已按账面数量填充未录入的记录')
}

function clearInput() {
  currentRecords.value.forEach((r) => {
    if (r.status === 0) delete actualInput[r.id]
  })
}

async function submit() {
  if (!currentNo.value) {
    ElMessage.warning('请先选择或生成一张盘点单')
    return
  }
  if (!allFilled.value) {
    ElMessage.warning('请为每条记录录入实盘数量（可点击「按账面填充」快速处理）')
    return
  }

  const entries = currentRecords.value
    .filter((r) => r.status !== 2)
    .map((r) => ({ id: r.id, actualQuantity: Number(actualInput[r.id] ?? 0) }))

  if (!entries.length) {
    ElMessage.warning('该盘点单的记录均已调整，无需再次提交')
    return
  }

  const tip = adjustStock.value
    ? `提交后将把差异写回库存表，并同步货位占用状态。\n差异 ${diffSummary.value.diffRows} 条，差异绝对值和 ${diffSummary.value.absSum}。`
    : `提交后仅记录实盘数量与差异，暂不调整库存。\n差异 ${diffSummary.value.diffRows} 条。`

  try {
    await ElMessageBox.confirm(tip + '\n是否继续？', '提交盘点结果', {
      type: 'warning',
      confirmButtonText: '确认提交',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  submitting.value = true
  try {
    const res = await stockApi.submitStocktake({
      stocktakeNo: currentNo.value,
      adjustStock: adjustStock.value,
      entries
    })
    ElMessage.success(
      `盘点已提交：录入 ${res.countedRows} 条，差异 ${res.diffRows} 条` +
        (res.adjustStock ? `，已调整 ${res.adjustedRows} 条库存` : '（未调整库存）')
    )
    await load()
  } finally {
    submitting.value = false
  }
}

async function cancelStocktake() {
  if (!currentNo.value) return
  try {
    await ElMessageBox.confirm(
      `确定要撤销盘点单「${currentNo.value}」吗？\n已调整库存的盘点单不能撤销。`,
      '撤销确认',
      { type: 'warning', confirmButtonText: '确定撤销', cancelButtonText: '返回' }
    )
  } catch {
    return
  }
  try {
    await stockApi.cancelStocktake(currentNo.value)
    ElMessage.success('盘点单已撤销')
    currentNo.value = ''
    await load()
  } catch {
    // 已调整的单据会被后端拒绝
  }
}

onMounted(load)
</script>

<template>
  <div class="page-root stocktake-page">
    <!-- 操作区 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <span class="stocktake-page__label">盘点单号</span>
        <ElSelect
          v-model="currentNo"
          placeholder="选择或生成盘点单"
          style="width: 260px"
          clearable
          @change="load"
        >
          <ElOption v-for="no in stocktakeNos" :key="no" :value="no" :label="no" />
        </ElSelect>

        <ElButton :icon="'Refresh'" :loading="loading" @click="load">刷新</ElButton>

        <div class="wms-toolbar__right">
          <ElButton :icon="'Plus'" :loading="generating" @click="generate">生成盘点单</ElButton>
          <ElButton
            :icon="'Delete'"
            :disabled="!currentNo"
            @click="cancelStocktake"
          >
            撤销盘点单
          </ElButton>
        </div>
      </div>

      <ElAlert type="info" :closable="false" show-icon class="stocktake-page__intro">
        <template #title>盘点流程与影响</template>
        <div class="stocktake-page__intro-body">
          生成盘点单时以<b>当前库存</b>为账面数量；录入实盘数量后系统计算差异。
          若选择「调整库存」，差异会在同一事务中写回库存表并同步货位占用状态
          （实盘为 0 的货位会被释放）。已调整的记录不可重复提交，已调整的盘点单不可撤销。
        </div>
      </ElAlert>
    </div>

    <!-- 差异统计 -->
    <div v-if="currentRecords.length" class="stocktake-page__metrics">
      <div class="metric-card">
        <div class="metric-card__label">盘点记录</div>
        <div class="metric-card__value">
          <span class="num">{{ currentRecords.length }}</span>
          <span class="metric-card__unit">条</span>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-card__label">账面数量合计</div>
        <div class="metric-card__value">
          <span class="num">
            {{ formatQuantity(currentRecords.reduce((s, r) => s + Number(r.bookQuantity), 0)) }}
          </span>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-card__label">差异条数</div>
        <div class="metric-card__value" :class="{ 'is-danger': diffSummary.diffRows > 0 }">
          <span class="num">{{ diffSummary.diffRows }}</span>
          <span class="metric-card__unit">条</span>
        </div>
        <div class="metric-card__hint">差异绝对值和 {{ diffSummary.absSum }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-card__label">净差异</div>
        <div class="metric-card__value" :class="diffSummary.netSum === 0 ? '' : 'is-warning'">
          <span class="num">{{ diffSummary.netSum > 0 ? '+' : '' }}{{ diffSummary.netSum }}</span>
        </div>
        <div class="metric-card__hint">正数表示实盘多于账面</div>
      </div>
    </div>

    <!-- 盘点明细 -->
    <div class="wms-card">
      <div class="wms-card__title">
        盘点明细
        <ElTag v-if="hasAdjusted" size="small" type="success" effect="plain">含已调整记录</ElTag>
      </div>

      <div v-if="currentRecords.length" class="stocktake-page__tools">
        <ElButton size="small" :icon="'MagicStick'" @click="fillWithBook">按账面填充空白</ElButton>
        <ElButton size="small" :icon="'RefreshLeft'" @click="clearInput">清空录入</ElButton>
        <ElCheckbox v-model="adjustStock" class="stocktake-page__adjust">
          提交时调整库存（写回差异并同步货位状态）
        </ElCheckbox>
      </div>

      <ElTable v-loading="loading" :data="currentRecords" size="small" stripe height="420">
        <ElTableColumn prop="locationCode" label="货位" width="96" fixed>
          <template #default="{ row }">
            <ElTag type="primary" size="small" effect="plain">{{ row.locationCode }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="距出入口" width="100" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatDistance(row.distance) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="productCode" label="商品编码" width="106" />
        <ElTableColumn prop="productName" label="商品名称" min-width="140" show-overflow-tooltip />
        <ElTableColumn label="账面数量" width="106" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatQuantity(row.bookQuantity) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="实盘数量" width="150">
          <template #default="{ row }">
            <ElInputNumber
              v-model="actualInput[row.id]"
              :min="0"
              :precision="0"
              size="small"
              style="width: 100%"
              :disabled="row.status === 2"
              placeholder="请输入"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="差异" width="100" align="right">
          <template #default="{ row }">
            <span
              class="num stocktake-page__diff"
              :class="{
                'is-up': diffOf(row) > 0,
                'is-down': diffOf(row) < 0
              }"
            >
              {{ diffOf(row) > 0 ? '+' : '' }}{{ diffOf(row) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="92" align="center">
          <template #default="{ row }">
            <ElTag :type="(statusOf(row.status).type as any)" size="small">
              {{ statusOf(row.status).label }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="盘点时间" min-width="150">
          <template #default="{ row }">
            <span class="stocktake-page__time">
              {{ row.stocktakeTime ? formatDateTime(row.stocktakeTime) : '—' }}
            </span>
          </template>
        </ElTableColumn>

        <template #empty>
          <EmptyState description="暂无盘点记录，点击右上角「生成盘点单」开始盘点" />
        </template>
      </ElTable>

      <div v-if="currentRecords.length" class="stocktake-page__footer">
        <ElButton
          type="primary"
          :loading="submitting"
          :disabled="!allFilled || hasAdjusted"
          @click="submit"
        >
          提交盘点结果
        </ElButton>
        <span v-if="hasAdjusted" class="stocktake-page__footer-tip">
          该盘点单包含已调整的记录，不能再次提交
        </span>
        <span v-else-if="!allFilled" class="stocktake-page__footer-tip">
          还有记录未录入实盘数量
        </span>
        <span v-else class="stocktake-page__footer-ok">
          已全部录入，可提交；当前差异 {{ diffSummary.diffRows }} 条
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.stocktake-page {
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
  }

  &__metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    margin: 12px 0;
  }

  &__tools {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
    padding: 8px 10px;
    border-radius: 6px;
    background: #f8fafc;
  }

  &__adjust {
    margin-left: auto;
  }

  &__diff {
    font-weight: 600;
    color: #909399;

    &.is-up {
      color: #10b981;
    }

    &.is-down {
      color: #ef4444;
    }
  }

  &__time {
    font-size: 12px;
    color: #909399;
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 14px;
  }

  &__footer-tip {
    font-size: 12px;
    color: #e6a23c;
  }

  &__footer-ok {
    font-size: 12px;
    color: #10b981;
  }
}

.metric-card {
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

    &.is-danger {
      color: #ef4444;
    }

    &.is-warning {
      color: #f59e0b;
    }
  }

  &__unit {
    margin-left: 3px;
    font-size: 12px;
    font-weight: 400;
    color: #909399;
  }

  &__hint {
    margin-top: 3px;
    font-size: 11px;
    color: #a8abb2;
  }
}

@media (max-width: 1200px) {
  .stocktake-page__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
