<script setup lang="ts">
/**
 * 商品管理。
 *
 * 除常规增删改查外，本页面的重点是让「出库频次」与「ABC 分类」这两个
 * 货位分配算法的输入变量变得可见、可操作：
 *  - 列表按出库频次降序，最高频商品排在最前；
 *  - 可一键重算 ABC 分类，并把分类结果与频次占比反馈出来；
 *  - 支持手工校正某个商品的出库频次，用于演示频次变化对算法的影响。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { categoryApi, productApi } from '@/api/product'
import { useTable, confirmRemove } from '@/composables/useTable'
import { ABC_COLORS, ENABLE_STATUS } from '@/utils/constants'
import { formatQuantity } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'
import type { AbcClassifyResult, BaseCategory, BaseProduct } from '@/types/base'

/* ------------------------------ 筛选与列表 ------------------------------ */
const filters = reactive({
  keyword: '',
  categoryId: 'all' as string | number,
  abcClass: 'all' as string,
  status: 'all' as string
})

const {
  loading,
  pageList,
  total,
  currentPage,
  pageSize,
  refresh
} = useTable<BaseProduct>({
  // 一次取全量，筛选与分页在前端完成（数据量有限，避免为筛选再加接口）
  fetcher: () => productApi.list({ limit: 2000 }),
  filter: (row) => {
    const kw = filters.keyword.trim().toLowerCase()
    if (kw) {
      const hit =
        row.productCode.toLowerCase().includes(kw) || row.productName.toLowerCase().includes(kw)
      if (!hit) return false
    }
    if (filters.categoryId !== 'all' && row.categoryId !== Number(filters.categoryId)) return false
    if (filters.abcClass !== 'all' && row.abcClass !== filters.abcClass) return false
    if (filters.status !== 'all' && row.status !== Number(filters.status)) return false
    return true
  }
})

/** 分类列表，供筛选与表单下拉使用 */
const categories = ref<BaseCategory[]>([])
const categoryMap = computed(() => {
  const map = new Map<number, string>()
  categories.value.forEach((c) => map.set(c.id, c.categoryName))
  return map
})

/* ------------------------------ 表单 ------------------------------ */
const dialogVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined as number | undefined,
  productCode: '',
  productName: '',
  categoryId: undefined as number | undefined,
  spec: '',
  unit: '',
  weight: undefined as number | undefined,
  volume: undefined as number | undefined,
  pickFreq: 0,
  abcClass: 'C',
  safetyStock: 0,
  status: 1
})

const rules: FormRules = {
  productCode: [
    { required: true, message: '请输入商品编码', trigger: 'blur' },
    { max: 50, message: '编码长度不超过 50 个字符', trigger: 'blur' }
  ],
  productName: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  abcClass: [{ required: true, message: '请选择 ABC 分类', trigger: 'change' }]
}

function resetForm() {
  form.id = undefined
  form.productCode = ''
  form.productName = ''
  form.categoryId = undefined
  form.spec = ''
  form.unit = '件'
  form.weight = undefined
  form.volume = undefined
  form.pickFreq = 0
  form.abcClass = 'C'
  form.safetyStock = 0
  form.status = 1
}

function openCreate() {
  resetForm()
  isEdit.value = false
  dialogVisible.value = true
}

function openEdit(row: BaseProduct) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    productCode: row.productCode,
    productName: row.productName,
    categoryId: row.categoryId,
    spec: row.spec ?? '',
    unit: row.unit ?? '件',
    weight: row.weight,
    volume: row.volume,
    pickFreq: row.pickFreq ?? 0,
    abcClass: row.abcClass ?? 'C',
    safetyStock: row.safetyStock ?? 0,
    status: row.status
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
      await productApi.update(form.id, payload)
      ElMessage.success('商品已更新')
    } else {
      delete (payload as { id?: number }).id
      await productApi.create(payload)
      ElMessage.success('商品已创建')
    }
    dialogVisible.value = false
    await refresh()
  } finally {
    submitting.value = false
  }
}

async function handleRemove(row: BaseProduct) {
  await confirmRemove(`${row.productCode} ${row.productName}`, () => productApi.remove(row.id), refresh)
}

/* ------------------------------ ABC 分类 ------------------------------ */
const abcDialogVisible = ref(false)
const abcRunning = ref(false)
const abcResult = ref<AbcClassifyResult | null>(null)

async function runAbcClassify() {
  abcRunning.value = true
  try {
    abcResult.value = await productApi.classifyAbc()
    abcDialogVisible.value = true
    await refresh()
  } finally {
    abcRunning.value = false
  }
}

/** ABC 结果表格 */
const abcRows = computed(() => {
  const r = abcResult.value
  if (!r) return []
  const order = ['A', 'B', 'C']
  return order
    .filter((c) => r.classCounts?.[c] !== undefined)
    .map((c) => ({
      abcClass: c,
      count: r.classCounts[c] ?? 0,
      frequency: r.classFrequencies?.[c] ?? 0,
      ratio: r.classFrequencyRatio?.[c] ?? 0
    }))
})

/* ------------------------------ 频次校正 ------------------------------ */
async function editPickFreq(row: BaseProduct) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入「${row.productName}」新的累计出库频次。\n频次是货位分配算法的核心输入，修改后建议重新执行货位优化。`,
      '校正出库频次',
      {
        inputValue: String(row.pickFreq ?? 0),
        inputPattern: /^\d+$/,
        inputErrorMessage: '请输入非负整数',
        confirmButtonText: '保存',
        cancelButtonText: '取消'
      }
    )
    // 后端提供的是增量累加接口，因此按差量补足到目标值
    const target = Number(value)
    const delta = target - (row.pickFreq ?? 0)
    if (delta === 0) {
      ElMessage.info('频次未发生变化')
      return
    }
    if (delta < 0) {
      ElMessage.warning('频次只支持累加，不能直接调低；如需降低请通过盘点或直接调整数据')
      return
    }
    await productApi.increasePickFreq(row.id, delta)
    ElMessage.success(`频次已更新为 ${target}`)
    await refresh()
  } catch {
    // 用户取消
  }
}

/* ------------------------------ 初始化 ------------------------------ */
onMounted(async () => {
  try {
    const tree = await categoryApi.tree()
    // 分类树展平，供下拉选择
    const flat: BaseCategory[] = []
    const walk = (nodes: typeof tree) => {
      nodes.forEach((n) => {
        flat.push({ id: n.id, parentId: n.parentId, categoryName: n.categoryName, sort: n.sort })
        if (n.children?.length) walk(n.children)
      })
    }
    walk(tree)
    categories.value = flat
  } catch {
    categories.value = []
  }
})
</script>

<template>
  <div class="page-root product-page">
    <!-- 筛选与操作 -->
    <div class="wms-card">
      <div class="wms-toolbar">
        <ElInput
          v-model="filters.keyword"
          placeholder="搜索商品编码或名称"
          clearable
          style="width: 220px"
          :prefix-icon="'Search'"
        />

        <ElSelect v-model="filters.categoryId" placeholder="商品分类" style="width: 160px">
          <ElOption label="全部分类" value="all" />
          <ElOption v-for="c in categories" :key="c.id" :value="c.id" :label="c.categoryName" />
        </ElSelect>

        <ElSelect v-model="filters.abcClass" placeholder="ABC 分类" style="width: 140px">
          <ElOption label="全部 ABC" value="all" />
          <ElOption label="A 类（高频）" value="A" />
          <ElOption label="B 类（中频）" value="B" />
          <ElOption label="C 类（低频）" value="C" />
        </ElSelect>

        <ElSelect v-model="filters.status" placeholder="状态" style="width: 120px">
          <ElOption label="全部状态" value="all" />
          <ElOption label="启用" value="1" />
          <ElOption label="停用" value="0" />
        </ElSelect>

        <ElButton :icon="'Refresh'" :loading="loading" @click="refresh">刷新</ElButton>

        <div class="wms-toolbar__right">
          <ElTooltip
            content="按累计出库频次占比重新划分 A/B/C 类：达到 70% 为 A 类、70%~90% 为 B 类、其余为 C 类。该规则与算法内核一致。"
            placement="top"
          >
            <ElButton :icon="'MagicStick'" :loading="abcRunning" @click="runAbcClassify">
              重算 ABC 分类
            </ElButton>
          </ElTooltip>
          <ElButton type="primary" :icon="'Plus'" @click="openCreate">新增商品</ElButton>
        </div>
      </div>

      <div class="product-page__hint">
        <ElIcon><InfoFilled /></ElIcon>
        <span>
          列表按<b>累计出库频次降序</b>排列。频次与 ABC 分类是货位分配算法的两个输入变量：
          频次越高、分类越靠前，越应该被安排在靠近出入口的货位。
        </span>
      </div>
    </div>

    <!-- 列表 -->
    <div class="wms-card">
      <div class="wms-card__title">
        商品列表
        <ElTag size="small" type="info" effect="plain">共 {{ total }} 条</ElTag>
      </div>

      <ElTable v-loading="loading" :data="pageList" stripe size="small">
        <ElTableColumn prop="productCode" label="商品编码" width="110" fixed />
        <ElTableColumn prop="productName" label="商品名称" min-width="160" show-overflow-tooltip fixed />
        <ElTableColumn label="分类" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ categoryMap.get(row.categoryId) ?? '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="spec" label="规格" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.spec || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="unit" label="单位" width="70" align="center">
          <template #default="{ row }">{{ row.unit || '—' }}</template>
        </ElTableColumn>

        <ElTableColumn label="累计出库频次" width="140" align="right" sortable :sort-method="(a: BaseProduct, b: BaseProduct) => (a.pickFreq ?? 0) - (b.pickFreq ?? 0)">
          <template #default="{ row }">
            <span class="product-page__freq">{{ formatQuantity(row.pickFreq) }}</span>
          </template>
        </ElTableColumn>

        <ElTableColumn label="ABC 分类" width="100" align="center">
          <template #default="{ row }">
            <ElTag :color="ABC_COLORS[row.abcClass]" effect="dark" size="small">
              {{ row.abcClass ?? '—' }} 类
            </ElTag>
          </template>
        </ElTableColumn>

        <ElTableColumn label="安全库存" width="100" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatQuantity(row.safetyStock) }}</span>
          </template>
        </ElTableColumn>

        <ElTableColumn label="状态" width="86" align="center">
          <template #default="{ row }">
            <ElTag :type="(ENABLE_STATUS[row.status]?.type as any) ?? 'info'" size="small">
              {{ ENABLE_STATUS[row.status]?.label ?? '未知' }}
            </ElTag>
          </template>
        </ElTableColumn>

        <ElTableColumn label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton link type="primary" size="small" @click="openEdit(row)">编辑</ElButton>
            <ElButton link type="warning" size="small" @click="editPickFreq(row)">校正频次</ElButton>
            <ElButton link type="danger" size="small" @click="handleRemove(row)">删除</ElButton>
          </template>
        </ElTableColumn>

        <template #empty>
          <EmptyState description="没有符合条件的商品" />
        </template>
      </ElTable>

      <div v-if="total > pageSize" class="wms-pagination">
        <ElPagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <ElDialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑商品' : '新增商品'"
      width="620px"
      :close-on-click-modal="false"
    >
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="110px">
        <ElRow :gutter="12">
          <ElCol :span="12">
            <ElFormItem label="商品编码" prop="productCode">
              <ElInput v-model="form.productCode" placeholder="如 P0001" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="商品名称" prop="productName">
              <ElInput v-model="form.productName" placeholder="请输入商品名称" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="商品分类">
              <ElSelect v-model="form.categoryId" placeholder="请选择分类" clearable style="width: 100%">
                <ElOption v-for="c in categories" :key="c.id" :value="c.id" :label="c.categoryName" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="规格">
              <ElInput v-model="form.spec" placeholder="如 500ml/瓶" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="计量单位">
              <ElInput v-model="form.unit" placeholder="如 件、箱、托" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="重量（kg）">
              <ElInputNumber v-model="form.weight" :min="0" :precision="3" style="width: 100%" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="累计出库频次">
              <ElInputNumber v-model="form.pickFreq" :min="0" :precision="0" style="width: 100%" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="ABC 分类" prop="abcClass">
              <ElSelect v-model="form.abcClass" style="width: 100%">
                <ElOption label="A 类（高频）" value="A" />
                <ElOption label="B 类（中频）" value="B" />
                <ElOption label="C 类（低频）" value="C" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="安全库存">
              <ElInputNumber v-model="form.safetyStock" :min="0" :precision="2" style="width: 100%" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="状态">
              <ElRadioGroup v-model="form.status">
                <ElRadio :value="1">启用</ElRadio>
                <ElRadio :value="0">停用</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
          </ElCol>
        </ElRow>
      </ElForm>

      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="submitting" @click="submitForm">
          {{ isEdit ? '保存修改' : '确认创建' }}
        </ElButton>
      </template>
    </ElDialog>

    <!-- ABC 分类结果 -->
    <ElDialog v-model="abcDialogVisible" title="ABC 分类计算结果" width="620px">
      <ElAlert type="success" :closable="false" show-icon class="product-page__abc-alert">
        <template #title>
          已重新划分 {{ abcResult?.totalProducts ?? 0 }} 个商品的 ABC 分类，
          更新 {{ abcResult?.updatedCount ?? 0 }} 条记录
        </template>
        <div class="product-page__abc-desc">
          划分规则：商品按出库频次降序累加，累计占比首次超过 70% 归入 A 类、
          首次超过 90% 归入 B 类，其余为 C 类。该规则与算法内核完全一致，
          因此系统里的分类结果可以直接与论文实验数据对照。
        </div>
      </ElAlert>

      <ElTable :data="abcRows" size="small" stripe>
        <ElTableColumn prop="abcClass" label="分类" width="100" align="center">
          <template #default="{ row }">
            <ElTag :color="ABC_COLORS[row.abcClass]" effect="dark" size="small">
              {{ row.abcClass }} 类
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="count" label="商品数量" width="110" align="right" />
        <ElTableColumn label="出库频次合计" width="140" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatQuantity(row.frequency) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="频次占比" min-width="120" align="right">
          <template #default="{ row }">
            <span class="product-page__ratio">{{ row.ratio }}%</span>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="product-page__abc-note">
        注：A 类商品数量通常不多，但贡献了大部分出库量，这正是把它们安排在近端货位的依据。
      </div>

      <template #footer>
        <ElButton type="primary" @click="abcDialogVisible = false">知道了</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.product-page {
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

  &__freq {
    font-weight: 600;
    color: #1f2937;
  }

  &__ratio {
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__abc-alert {
    margin-bottom: 14px;
  }

  &__abc-desc {
    margin-top: 5px;
    font-size: 12px;
    line-height: 1.75;
  }

  &__abc-note {
    margin-top: 12px;
    padding: 9px 12px;
    border-radius: 6px;
    background: #f8fafc;
    font-size: 12px;
    color: #606266;
    line-height: 1.7;
  }
}
</style>
