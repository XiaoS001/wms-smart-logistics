<script setup lang="ts">
/**
 * 仓库管理。
 *
 * 仓库的布局参数（巷道数与纵深数、巷道间距与纵深间距）是货位分配与拣货路径
 * 两个算法计算距离的基础，因此本页面在编辑时会对"已生成货位后再改布局参数"
 * 给出明确提醒：已有货位的距离不会自动重算，需要重新生成。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { warehouseApi } from '@/api/warehouse'
import { useAppStore } from '@/stores/app'
import { ENABLE_STATUS } from '@/utils/constants'
import { formatDistance, formatQuantity } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'
import type { BaseWarehouse, GenerateLocationsResult, LocationSummary } from '@/types/base'

const appStore = useAppStore()

const loading = ref(false)
const warehouses = ref<BaseWarehouse[]>([])
const locationSummary = ref<Record<number, LocationSummary[]>>({})

/* ------------------------------ 表单 ------------------------------ */
const dialogVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined as number | undefined,
  warehouseCode: '',
  warehouseName: '',
  aisleCount: 10,
  rowCount: 20,
  aisleGap: 2.5,
  rowGap: 1.2,
  status: 1,
  remark: ''
})

const rules: FormRules = {
  warehouseCode: [
    { required: true, message: '请输入仓库编码', trigger: 'blur' },
    { max: 50, message: '编码长度不超过 50 个字符', trigger: 'blur' }
  ],
  warehouseName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  aisleCount: [{ required: true, message: '请输入巷道数', trigger: 'blur' }],
  rowCount: [{ required: true, message: '请输入纵深数', trigger: 'blur' }]
}

/** 总货位数：随表单实时变化，帮助用户理解布局规模 */
const totalSlots = computed(() => (form.aisleCount || 0) * (form.rowCount || 0))

/** 该仓库的最远距离：用于说明布局参数对距离范围的影响 */
const maxDistance = computed(() =>
  (form.aisleGap || 0) * (form.aisleCount || 0) + (form.rowGap || 0) * (form.rowCount || 0)
)

/** 编辑态下若已生成货位，则提示布局变更的影响 */
const layoutChangedWarning = computed(() => {
  if (!isEdit.value || !form.id) return null
  const existing = locationSummary.value[form.id]
  const count = (existing ?? []).reduce((sum, z) => sum + (z.locationCount ?? 0), 0)
  if (!count) return null
  const original = warehouses.value.find((w) => w.id === form.id)
  if (!original) return null
  const changed =
    original.aisleCount !== form.aisleCount ||
    original.rowCount !== form.rowCount ||
    Number(original.aisleGap) !== Number(form.aisleGap) ||
    Number(original.rowGap) !== Number(form.rowGap)
  if (!changed) return null
  return `该仓库已有 ${count} 个货位。修改布局参数不会自动重算已有货位的距离，如需生效请在「库位管理」中重新生成货位。`
})

function resetForm() {
  form.id = undefined
  form.warehouseCode = ''
  form.warehouseName = ''
  form.aisleCount = 10
  form.rowCount = 20
  form.aisleGap = 2.5
  form.rowGap = 1.2
  form.status = 1
  form.remark = ''
}

function openCreate() {
  resetForm()
  isEdit.value = false
  dialogVisible.value = true
}

function openEdit(row: BaseWarehouse) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    warehouseCode: row.warehouseCode,
    warehouseName: row.warehouseName,
    aisleCount: row.aisleCount,
    rowCount: row.rowCount,
    aisleGap: Number(row.aisleGap),
    rowGap: Number(row.rowGap),
    status: row.status,
    remark: row.remark ?? ''
  })
  isEdit.value = true
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = { ...form }
    if (isEdit.value && form.id) {
      await warehouseApi.update(form.id, payload)
      ElMessage.success('仓库已更新')
    } else {
      delete (payload as { id?: number }).id
      await warehouseApi.create(payload)
      ElMessage.success('仓库已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function handleRemove(row: BaseWarehouse) {
  try {
    await ElMessageBox.confirm(
      `确定要删除仓库「${row.warehouseName}」吗？若该仓库下仍有货位，删除会被拒绝。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await warehouseApi.remove(row.id)
    ElMessage.success('仓库已删除')
    await load()
  } catch {
    // 后端会返回 409 及具体原因（例如"该仓库下仍有 200 个货位"）
  }
}

/** 快速生成货位：使用仓库自身布局参数 */
const generating = ref<number | null>(null)
async function quickGenerate(row: BaseWarehouse) {
  generating.value = row.id
  try {
    const res: GenerateLocationsResult = await warehouseApi.generateLocations(row.id, {
      overwrite: false
    })
    if (res.created === 0 && res.skipped > 0) {
      ElMessage.info(`货位已存在，本次跳过 ${res.skipped} 个（共 ${res.total} 个）`)
    } else {
      ElMessage.success(
        `新增 ${res.created} 个货位，跳过 ${res.skipped} 个，共 ${res.total} 个；距离 ${res.minDistance} ~ ${res.maxDistance} 米`
      )
    }
    await load()
  } finally {
    generating.value = null
  }
}

function switchTo(row: BaseWarehouse) {
  appStore.setCurrentWarehouse(row.id, row.warehouseName)
  ElMessage.success(`已切换当前仓库为「${row.warehouseName}」`)
}

function isCurrent(row: BaseWarehouse) {
  return appStore.currentWarehouseId === row.id
}

/** 每个仓库的货位总数（用汇总数据求和） */
function locationCountOf(id: number): number {
  const list = locationSummary.value[id]
  if (!list) return 0
  return list.reduce((sum, z) => sum + (z.locationCount ?? 0), 0)
}

function maxDistanceOf(row: BaseWarehouse): number {
  return Number(row.aisleGap) * row.aisleCount + Number(row.rowGap) * row.rowCount
}

async function load() {
  loading.value = true
  try {
    warehouses.value = await warehouseApi.list()
    // 并发拉取各仓库的货位汇总，用于展示货位数量与布局变更提醒
    const summaries = await Promise.all(
      warehouses.value.map((w) =>
        warehouseApi
          .locationSummary(w.id)
          .then((s) => ({ id: w.id, data: s }))
          .catch(() => ({ id: w.id, data: [] as LocationSummary[] }))
      )
    )
    const map: Record<number, LocationSummary[]> = {}
    summaries.forEach((s) => (map[s.id] = s.data))
    locationSummary.value = map
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-root warehouse-page">
    <div class="wms-card">
      <div class="wms-toolbar">
        <div class="warehouse-page__title">仓库列表</div>
        <ElTag size="small" type="info" effect="plain">共 {{ warehouses.length }} 个仓库</ElTag>
        <div class="wms-toolbar__right">
          <ElButton :icon="'Refresh'" :loading="loading" @click="load">刷新</ElButton>
          <ElButton type="primary" :icon="'Plus'" @click="openCreate">新增仓库</ElButton>
        </div>
      </div>

      <ElAlert type="info" :closable="false" show-icon class="warehouse-page__alert">
        <template #title>布局参数是算法计算距离的基础</template>
        <div class="warehouse-page__alert-body">
          货位到出入口的距离按
          <code>巷道间距 × 巷道号 + 纵深间距 × 纵深号</code>
          计算。算法内核使用同一公式，因此这里的参数直接决定优化效果的空间尺度。
          修改布局参数后需重新生成货位才会生效。
        </div>
      </ElAlert>
    </div>

    <div class="wms-card">
      <ElTable v-loading="loading" :data="warehouses" stripe size="small">
        <ElTableColumn label="仓库编码" width="110" fixed>
          <template #default="{ row }">
            <span class="warehouse-page__code">{{ row.warehouseCode }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="warehouseName" label="仓库名称" min-width="150" show-overflow-tooltip fixed>
          <template #default="{ row }">
            <span>{{ row.warehouseName }}</span>
            <ElTag v-if="isCurrent(row)" size="small" type="primary" effect="dark" class="warehouse-page__current">
              当前
            </ElTag>
          </template>
        </ElTableColumn>

        <ElTableColumn label="布局（巷道 × 纵深）" width="170" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.aisleCount }} × {{ row.rowCount }}</span>
          </template>
        </ElTableColumn>

        <ElTableColumn label="间距（巷道/纵深）" width="160" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.aisleGap }} / {{ row.rowGap }} 米</span>
          </template>
        </ElTableColumn>

        <ElTableColumn label="货位数量" width="110" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatQuantity(locationCountOf(row.id)) }}</span>
          </template>
        </ElTableColumn>

        <ElTableColumn label="最远距离" width="120" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatDistance(maxDistanceOf(row)) }}</span>
          </template>
        </ElTableColumn>

        <ElTableColumn prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </ElTableColumn>

        <ElTableColumn label="状态" width="86" align="center">
          <template #default="{ row }">
            <ElTag :type="(ENABLE_STATUS[row.status]?.type as any) ?? 'info'" size="small">
              {{ ENABLE_STATUS[row.status]?.label ?? '未知' }}
            </ElTag>
          </template>
        </ElTableColumn>

        <ElTableColumn label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              size="small"
              :disabled="isCurrent(row)"
              @click="switchTo(row)"
            >
              设为当前
            </ElButton>
            <ElButton
              link
              type="success"
              size="small"
              :loading="generating === row.id"
              @click="quickGenerate(row)"
            >
              生成货位
            </ElButton>
            <ElButton link type="primary" size="small" @click="openEdit(row)">编辑</ElButton>
            <ElButton link type="danger" size="small" @click="handleRemove(row)">删除</ElButton>
          </template>
        </ElTableColumn>

        <template #empty>
          <EmptyState description="暂无仓库，请先新增一个仓库" />
        </template>
      </ElTable>
    </div>

    <!-- 新增/编辑 -->
    <ElDialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑仓库' : '新增仓库'"
      width="600px"
      :close-on-click-modal="false"
    >
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="120px">
        <ElRow :gutter="12">
          <ElCol :span="12">
            <ElFormItem label="仓库编码" prop="warehouseCode">
              <ElInput v-model="form.warehouseCode" placeholder="如 WH001" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="仓库名称" prop="warehouseName">
              <ElInput v-model="form.warehouseName" placeholder="如 智慧物流示范仓" />
            </ElFormItem>
          </ElCol>

          <ElCol :span="24">
            <ElDivider content-position="left" class="warehouse-page__divider">
              <span class="warehouse-page__divider-text">布局参数（决定算法计算距离的空间尺度）</span>
            </ElDivider>
          </ElCol>

          <ElCol :span="12">
            <ElFormItem label="巷道数 X" prop="aisleCount">
              <ElInputNumber v-model="form.aisleCount" :min="1" :max="200" style="width: 100%" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="纵深数 Y" prop="rowCount">
              <ElInputNumber v-model="form.rowCount" :min="1" :max="200" style="width: 100%" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="巷道间距（米）">
              <ElInputNumber v-model="form.aisleGap" :min="0.1" :precision="2" :step="0.1" style="width: 100%" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="纵深间距（米）">
              <ElInputNumber v-model="form.rowGap" :min="0.1" :precision="2" :step="0.1" style="width: 100%" />
            </ElFormItem>
          </ElCol>

          <ElCol :span="24">
            <div class="warehouse-page__preview">
              <span>货位总数：<b>{{ totalSlots }}</b> 个</span>
              <span>最远距离：<b>{{ maxDistance.toFixed(2) }}</b> 米</span>
              <span class="warehouse-page__preview-note">
                （按巷道间距 × 巷道数 + 纵深间距 × 纵深数估算）
              </span>
            </div>
          </ElCol>

          <ElCol :span="12">
            <ElFormItem label="状态">
              <ElRadioGroup v-model="form.status">
                <ElRadio :value="1">启用</ElRadio>
                <ElRadio :value="0">停用</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
          </ElCol>
          <ElCol :span="24">
            <ElFormItem label="备注">
              <ElInput v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
            </ElFormItem>
          </ElCol>
        </ElRow>

        <ElAlert
          v-if="layoutChangedWarning"
          type="warning"
          :closable="false"
          show-icon
          class="warehouse-page__warning"
        >
          {{ layoutChangedWarning }}
        </ElAlert>
      </ElForm>

      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="submitting" @click="submitForm">
          {{ isEdit ? '保存修改' : '确认创建' }}
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.warehouse-page {
  &__title {
    font-size: 15px;
    font-weight: 600;
    color: #1f2937;
  }

  &__alert {
    margin-top: 4px;
  }

  &__alert-body {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.75;

    code {
      padding: 1px 5px;
      border-radius: 3px;
      background: #f2f3f5;
      font-family: 'Consolas', monospace;
      font-size: 11px;
      color: #c7254e;
    }
  }

  &__code {
    font-family: 'Consolas', monospace;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__current {
    margin-left: 6px;
  }

  &__divider {
    margin: 4px 0 14px;

    :deep(.el-divider__text) {
      background: #fff;
    }
  }

  &__divider-text {
    font-size: 12px;
    color: #909399;
  }

  &__preview {
    display: flex;
    flex-wrap: wrap;
    gap: 18px;
    margin-bottom: 16px;
    padding: 10px 14px;
    border-radius: 6px;
    background: #f8fafc;
    font-size: 12px;
    color: #606266;

    b {
      color: var(--el-color-primary);
      font-size: 14px;
    }
  }

  &__preview-note {
    color: #a8abb2;
  }

  &__warning {
    margin-top: 6px;
  }
}
</style>
