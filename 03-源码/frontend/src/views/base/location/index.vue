<script setup lang="ts">
/**
 * 库位管理。
 *
 * 三块内容：
 * 1. 货位热力图：用颜色表达"离出入口的远近"，是理解货位分配算法最直观的工具；
 * 2. 货位列表：按到出入口距离升序，支持按状态筛选；
 * 3. 批量生成：按仓库布局参数生成规则货位，自动计算距离并划分库区。
 *
 * 生成接口具有幂等性（默认跳过已存在坐标），页面会明确把"跳过"数量反馈给用户。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { locationApi, warehouseApi } from '@/api/warehouse'
import { useAppStore } from '@/stores/app'
import { LOCATION_STATUS } from '@/utils/constants'
import { formatDistance, formatQuantity } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'
import LocationHeatMap from '@/components/warehouse/LocationHeatMap.vue'
import type { BaseLocation, BaseWarehouse, GenerateLocationsResult, LocationSummary } from '@/types/base'

const appStore = useAppStore()

const loading = ref(false)
const generating = ref(false)
const locations = ref<BaseLocation[]>([])
const warehouses = ref<BaseWarehouse[]>([])
const currentWarehouse = ref<BaseWarehouse | null>(null)
const zoneSummary = ref<LocationSummary[]>([])

/** 筛选条件：用字符串表示"全部"，避免 undefined 作为下拉值带来类型与语义歧义 */
const filters = reactive({
  status: 'all' as string
})

/** 生成表单 */
const genDialogVisible = ref(false)
const genFormRef = ref<FormInstance>()
const genForm = reactive({
  aisleCount: 10,
  rowCount: 20,
  aisleDistance: 15,
  rowDistance: 30,
  capacity: 100,
  overwrite: false
})

const genRules: FormRules = {
  aisleCount: [
    { required: true, message: '请输入巷道数', trigger: 'blur' },
    { type: 'number', min: 1, max: 200, message: '巷道数范围为 1 到 200', trigger: 'blur' }
  ],
  rowCount: [
    { required: true, message: '请输入纵深数', trigger: 'blur' },
    { type: 'number', min: 1, max: 200, message: '纵深数范围为 1 到 200', trigger: 'blur' }
  ]
}

/** 按状态筛选后的货位 */
const filteredLocations = computed(() => {
  if (filters.status === 'all') return locations.value
  const target = Number(filters.status)
  return locations.value.filter((l) => l.status === target)
})

const usedCount = computed(() => locations.value.filter((l) => l.status === 2).length)
const freeCount = computed(() => locations.value.filter((l) => l.status === 1).length)

/** 生成总量预览：让用户在提交前就知道会生成多少货位 */
const genTotal = computed(() => (genForm.aisleCount || 0) * (genForm.rowCount || 0))

/** 库区分布表 */
const zoneRows = computed(() =>
  zoneSummary.value.map((z, i) => ({
    zoneLabel: ['A 类近端区', 'B 类中区', 'C 类远端区', '其它库区'][i] ?? `库区 ${z.zoneId}`,
    ...z
  }))
)

async function loadWarehouses() {
  try {
    warehouses.value = await warehouseApi.list()
  } catch {
    // 拿不到仓库列表时不能让整页瘫痪：
    // 当前仓库 ID 是持久化的，用它兜底仍可查询货位。
    warehouses.value = []
    ElMessage.warning('未能获取仓库列表，已使用上次选择的仓库')
  }

  if (!currentWarehouse.value) {
    const target =
      warehouses.value.find((w) => w.id === appStore.currentWarehouseId) ??
      warehouses.value[0] ??
      // 列表为空时的兜底：只用 ID，布局参数等展示信息缺失
      ({ id: appStore.currentWarehouseId, warehouseName: appStore.currentWarehouseName } as BaseWarehouse)
    currentWarehouse.value = target
    if (target.warehouseName) {
      appStore.setCurrentWarehouse(target.id, target.warehouseName)
    }
  }
}

async function loadLocations() {
  if (!currentWarehouse.value) return
  loading.value = true
  try {
    const [list, summary] = await Promise.all([
      locationApi.list({ warehouseId: currentWarehouse.value.id, limit: 5000 }),
      warehouseApi.locationSummary(currentWarehouse.value.id).catch(() => [])
    ])
    locations.value = list
    zoneSummary.value = summary as LocationSummary[]
  } finally {
    loading.value = false
  }
}

function openGenerateDialog() {
  if (!currentWarehouse.value) {
    ElMessage.warning('请先选择仓库')
    return
  }
  // 用仓库现有布局参数作为默认值，减少手工输入
  genForm.aisleCount = currentWarehouse.value.aisleCount || 10
  genForm.rowCount = currentWarehouse.value.rowCount || 20
  genForm.aisleDistance = 15
  genForm.rowDistance = 30
  genForm.capacity = 100
  genForm.overwrite = false
  genDialogVisible.value = true
}

async function submitGenerate() {
  if (!currentWarehouse.value || !genFormRef.value) return
  const valid = await genFormRef.value.validate().catch(() => false)
  if (!valid) return

  if (genForm.overwrite) {
    try {
      await ElMessageBox.confirm(
        '覆盖模式会先清空该仓库的全部货位再重新生成。若货位上有库存，请谨慎操作。是否继续？',
        '确认覆盖',
        { type: 'warning', confirmButtonText: '继续生成', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }

  generating.value = true
  try {
    const res: GenerateLocationsResult = await warehouseApi.generateLocations(
      currentWarehouse.value.id,
      {
        aisleCount: genForm.aisleCount,
        rowCount: genForm.rowCount,
        aisleDistance: genForm.aisleDistance,
        rowDistance: genForm.rowDistance,
        capacity: genForm.capacity,
        overwrite: genForm.overwrite
      }
    )

    const parts = [`新增 ${res.created} 个货位`]
    if (res.skipped) parts.push(`跳过已存在 ${res.skipped} 个`)
    if (res.removed) parts.push(`清除旧货位 ${res.removed} 个`)
    parts.push(`当前共 ${res.total} 个`)
    parts.push(`距离 ${res.minDistance} ~ ${res.maxDistance} 米`)
    ElMessage.success(parts.join('，'))

    genDialogVisible.value = false
    await loadLocations()
  } finally {
    generating.value = false
  }
}

async function handleWarehouseChange(id: number) {
  const target = warehouses.value.find((w) => w.id === id)
  if (target) {
    currentWarehouse.value = target
    appStore.setCurrentWarehouse(target.id, target.warehouseName)
  }
  await loadLocations()
}

onMounted(async () => {
  await loadWarehouses()
  await loadLocations()
})
</script>

<template>
  <div class="page-root location-page">
    <!-- 工具条 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <ElSelect
          :model-value="currentWarehouse?.id"
          placeholder="选择仓库"
          style="width: 240px"
          @change="handleWarehouseChange"
        >
          <ElOption
            v-for="w in warehouses"
            :key="w.id"
            :value="w.id"
            :label="`${w.warehouseName}（${w.warehouseCode}）`"
          />
        </ElSelect>

        <ElSelect v-model="filters.status" placeholder="货位状态" style="width: 140px">
          <ElOption label="全部状态" value="all" />
          <ElOption label="空闲" value="1" />
          <ElOption label="占用" value="2" />
          <ElOption label="停用" value="0" />
        </ElSelect>

        <ElButton :icon="'Refresh'" :loading="loading" @click="loadLocations">刷新</ElButton>

        <div class="wms-toolbar__right">
          <ElButton type="primary" :icon="'Plus'" @click="openGenerateDialog">
            批量生成货位
          </ElButton>
        </div>
      </div>

      <div v-if="currentWarehouse" class="location-page__brief">
        <span>仓库布局：<b>{{ currentWarehouse.aisleCount }} 巷道 × {{ currentWarehouse.rowCount }} 纵深</b></span>
        <span>货位总数：<b>{{ locations.length }}</b></span>
        <span>空闲：<b class="is-free">{{ freeCount }}</b></span>
        <span>占用：<b class="is-occupied">{{ usedCount }}</b></span>
        <span>巷道间距：{{ currentWarehouse.aisleGap }} 米 · 纵深间距：{{ currentWarehouse.rowGap }} 米</span>
      </div>
    </div>

    <!-- 热力图 -->
    <div class="wms-card">
      <div class="wms-card__title">
        货位距离热力图
        <ElTooltip
          content="颜色表示货位到出入口的行走距离：越绿越近、越红越远。算法会把出库频次高的商品优先安排在近端货位。"
          placement="top"
        >
          <ElIcon class="location-page__info"><QuestionFilled /></ElIcon>
        </ElTooltip>
      </div>

      <LocationHeatMap
        v-if="currentWarehouse && locations.length"
        :locations="locations"
        :aisles="currentWarehouse.aisleCount"
        :rows="currentWarehouse.rowCount"
      />
      <EmptyState v-else description="该仓库尚未生成货位，请点击「批量生成货位」" />
    </div>

    <!-- 库区分布 -->
    <div v-if="zoneRows.length" class="wms-card">
      <div class="wms-card__title">库区货位分布</div>
      <ElTable :data="zoneRows" size="small" stripe>
        <ElTableColumn prop="zoneLabel" label="库区" min-width="130" />
        <ElTableColumn prop="locationCount" label="货位数量" width="120" align="right" />
        <ElTableColumn label="最短距离" width="120" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatDistance(row.minDistance) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="最长距离" width="120" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatDistance(row.maxDistance) }}</span>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>

    <!-- 货位列表 -->
    <div class="wms-card">
      <div class="wms-card__title">
        货位列表
        <ElTag size="small" type="info" effect="plain">
          按到出入口距离升序，共 {{ filteredLocations.length }} 条
        </ElTag>
      </div>

      <ElTable
        v-loading="loading"
        :data="filteredLocations"
        stripe
        size="small"
        height="420"
      >
        <ElTableColumn prop="locationCode" label="货位编码" width="110" fixed />
        <ElTableColumn label="坐标 (巷道, 纵深)" width="150" align="center">
          <template #default="{ row }">
            <span class="num">({{ row.aisleNo }}, {{ row.rowNo }})</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="距出入口" width="120" align="right" sortable :sort-method="(a: BaseLocation, b: BaseLocation) => a.distance - b.distance">
          <template #default="{ row }">
            <span class="num location-page__distance">{{ formatDistance(row.distance) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="容量" width="100" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatQuantity(row.capacity) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90" align="center">
          <template #default="{ row }">
            <ElTag :type="(LOCATION_STATUS[row.status]?.type as any) ?? 'info'" size="small">
              {{ LOCATION_STATUS[row.status]?.label ?? '未知' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="zoneId" label="所属库区 ID" min-width="110" align="center" />
      </ElTable>
    </div>

    <!-- 批量生成对话框 -->
    <ElDialog
      v-model="genDialogVisible"
      title="批量生成货位"
      width="560px"
      :close-on-click-modal="false"
    >
      <ElAlert type="info" :closable="false" show-icon class="location-page__alert">
        <template #title>
          生成规则：按「巷道数 × 纵深数」生成规则货位
        </template>
        <div class="location-page__alert-body">
          距离按 <code>巷道间距 × 巷道号 + 纵深间距 × 纵深号</code> 计算，
          与算法内核使用同一公式；再按距离阈值划分 A/B/C 库区。
          重复生成会跳过已存在的坐标，因此本操作是幂等的。
        </div>
      </ElAlert>

      <ElForm ref="genFormRef" :model="genForm" :rules="genRules" label-width="120px" class="location-page__form">
        <ElFormItem label="巷道数 X" prop="aisleCount">
          <ElInputNumber v-model="genForm.aisleCount" :min="1" :max="200" controls-position="right" />
        </ElFormItem>
        <ElFormItem label="纵深数 Y" prop="rowCount">
          <ElInputNumber v-model="genForm.rowCount" :min="1" :max="200" controls-position="right" />
        </ElFormItem>
        <ElFormItem label="A 区距离阈值">
          <ElInputNumber v-model="genForm.aisleDistance" :min="1" :precision="1" controls-position="right" />
          <span class="location-page__unit">米以内为 A 类近端区</span>
        </ElFormItem>
        <ElFormItem label="B 区距离阈值">
          <ElInputNumber v-model="genForm.rowDistance" :min="2" :precision="1" controls-position="right" />
          <span class="location-page__unit">米以内为 B 类中区，其余为 C 类远端区</span>
        </ElFormItem>
        <ElFormItem label="货位容量">
          <ElInputNumber v-model="genForm.capacity" :min="1" :precision="2" controls-position="right" />
        </ElFormItem>
        <ElFormItem label="覆盖已有货位">
          <ElSwitch v-model="genForm.overwrite" />
          <span class="location-page__unit">
            {{ genForm.overwrite ? '将先清空该仓库全部货位' : '跳过已存在的坐标（推荐）' }}
          </span>
        </ElFormItem>
      </ElForm>

      <template #footer>
        <div class="location-page__footer">
          <span class="location-page__preview">
            预计生成 <b>{{ genTotal }}</b> 个货位
          </span>
          <div>
            <ElButton @click="genDialogVisible = false">取消</ElButton>
            <ElButton type="primary" :loading="generating" @click="submitGenerate">
              确认生成
            </ElButton>
          </div>
        </div>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.location-page {
  &__brief {
    display: flex;
    flex-wrap: wrap;
    gap: 18px;
    padding-top: 12px;
    border-top: 1px dashed #ebeef5;
    font-size: 12px;
    color: #606266;

    b {
      color: #1f2937;
    }

    .is-free {
      color: #10b981;
    }

    .is-occupied {
      color: #1f2937;
    }
  }

  &__info {
    margin-left: 4px;
    color: #c0c4cc;
    cursor: help;
    font-size: 13px;
  }

  &__distance {
    color: var(--el-color-primary);
    font-weight: 500;
  }

  &__alert {
    margin-bottom: 16px;
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

  &__form {
    :deep(.el-input-number) {
      width: 140px;
    }
  }

  &__unit {
    margin-left: 10px;
    font-size: 12px;
    color: #909399;
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__preview {
    font-size: 12px;
    color: #606266;

    b {
      color: var(--el-color-primary);
      font-size: 14px;
    }
  }
}
</style>
