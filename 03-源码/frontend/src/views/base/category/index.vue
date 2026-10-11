<script setup lang="ts">
/**
 * 商品分类管理。
 *
 * 采用「左侧分类树 + 右侧详情/操作」的经典布局。
 * 树节点的商品数量由后端一次查询带出，避免逐节点查询造成 N+1。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { ElTree } from 'element-plus'
import { categoryApi } from '@/api/product'
import EmptyState from '@/components/EmptyState.vue'
import type { CategoryTreeNode } from '@/types/base'

const loading = ref(false)
const treeData = ref<CategoryTreeNode[]>([])
const selected = ref<CategoryTreeNode | null>(null)
const treeRef = ref<InstanceType<typeof ElTree>>()
const filterText = ref('')

const dialogVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined as number | undefined,
  parentId: 0 as number,
  categoryName: '',
  sort: 0
})

const rules: FormRules = {
  categoryName: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 50, message: '名称长度不超过 50 个字符', trigger: 'blur' }
  ]
}

/** 树节点数量统计 */
const stats = computed(() => {
  let total = 0
  let withProduct = 0
  const walk = (nodes: CategoryTreeNode[]) => {
    nodes.forEach((n) => {
      total += 1
      if (n.productCount > 0) withProduct += 1
      if (n.children?.length) walk(n.children)
    })
  }
  walk(treeData.value)
  return { total, withProduct }
})

/** 父级候选：编辑时不能把自己或自己的后代设为父级 */
const parentOptions = computed(() => {
  const options: Array<{ id: number; label: string; depth: number }> = []
  const excluded = new Set<number>()
  if (isEdit.value && selected.value) {
    const collect = (node: CategoryTreeNode) => {
      excluded.add(node.id)
      node.children?.forEach(collect)
    }
    collect(selected.value)
  }
  const walk = (nodes: CategoryTreeNode[], depth: number) => {
    nodes.forEach((n) => {
      if (!excluded.has(n.id)) {
        options.push({ id: n.id, label: n.categoryName, depth })
        if (n.children?.length) walk(n.children, depth + 1)
      }
    })
  }
  walk(treeData.value, 1)
  return options
})

/** 按关键字过滤树：命中节点与其祖先保留 */
const filteredTree = computed(() => {
  const kw = filterText.value.trim()
  if (!kw) return treeData.value
  const filter = (nodes: CategoryTreeNode[]): CategoryTreeNode[] => {
    const result: CategoryTreeNode[] = []
    nodes.forEach((n) => {
      const children = n.children?.length ? filter(n.children) : []
      if (n.categoryName.includes(kw) || children.length) {
        result.push({ ...n, children })
      }
    })
    return result
  }
  return filter(treeData.value)
})

async function load() {
  loading.value = true
  try {
    treeData.value = await categoryApi.tree()
    // 刷新后保持选中状态
    if (selected.value) {
      const find = (nodes: CategoryTreeNode[]): CategoryTreeNode | null => {
        for (const n of nodes) {
          if (n.id === selected.value?.id) return n
          const hit = n.children?.length ? find(n.children) : null
          if (hit) return hit
        }
        return null
      }
      selected.value = find(treeData.value)
    }
  } finally {
    loading.value = false
  }
}

function handleNodeClick(node: CategoryTreeNode) {
  selected.value = node
}

function resetForm() {
  form.id = undefined
  form.parentId = selected.value?.id ?? 0
  form.categoryName = ''
  form.sort = 0
}

/** 新增子分类：默认父级为当前选中节点 */
function openCreateChild(parent?: CategoryTreeNode) {
  resetForm()
  form.parentId = parent?.id ?? 0
  isEdit.value = false
  dialogVisible.value = true
}

/** 新增顶级分类 */
function openCreateRoot() {
  resetForm()
  form.parentId = 0
  isEdit.value = false
  dialogVisible.value = true
}

function openEdit(node: CategoryTreeNode) {
  resetForm()
  form.id = node.id
  form.parentId = node.parentId ?? 0
  form.categoryName = node.categoryName
  form.sort = node.sort ?? 0
  isEdit.value = true
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      parentId: form.parentId || 0,
      categoryName: form.categoryName.trim(),
      sort: form.sort
    }
    if (isEdit.value && form.id) {
      await categoryApi.update(form.id, payload)
      ElMessage.success('分类已更新')
    } else {
      await categoryApi.create(payload)
      ElMessage.success('分类已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function handleRemove(node: CategoryTreeNode) {
  try {
    await ElMessageBox.confirm(
      `确定要删除分类「${node.categoryName}」吗？\n若该分类下仍有子分类或关联商品，删除会被拒绝。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await categoryApi.remove(node.id)
    ElMessage.success('分类已删除')
    if (selected.value?.id === node.id) selected.value = null
    await load()
  } catch {
    // 后端返回 409 与具体原因（子分类数或商品数）
  }
}

onMounted(load)
</script>

<template>
  <div class="page-root category-page">
    <ElRow :gutter="12">
      <!-- 左侧：分类树 -->
      <ElCol :xs="24" :lg="9">
        <div class="wms-card category-page__tree-card">
          <div class="wms-card__title">
            分类树
            <ElTag size="small" type="info" effect="plain">
              {{ stats.total }} 个分类
            </ElTag>
          </div>

          <ElInput
            v-model="filterText"
            placeholder="搜索分类名称"
            clearable
            size="small"
            :prefix-icon="'Search'"
            class="category-page__search"
          />

          <ElScrollbar v-loading="loading" class="category-page__tree-scroll">
            <ElTree
              ref="treeRef"
              :data="filteredTree"
              node-key="id"
              :props="{ label: 'categoryName', children: 'children' }"
              default-expand-all
              highlight-current
              :expand-on-click-node="false"
              @node-click="handleNodeClick"
            >
              <template #default="{ data }">
                <span class="category-page__node">
                  <span class="category-page__node-name">{{ data.categoryName }}</span>
                  <ElTag v-if="data.productCount > 0" size="small" effect="plain" type="primary">
                    {{ data.productCount }}
                  </ElTag>
                </span>
              </template>
            </ElTree>

            <EmptyState v-if="!filteredTree.length && !loading" description="暂无分类" />
          </ElScrollbar>

          <div class="category-page__tree-actions">
            <ElButton type="primary" size="small" :icon="'Plus'" @click="openCreateRoot">
              新增顶级分类
            </ElButton>
            <ElButton
              size="small"
              :icon="'FolderAdd'"
              :disabled="!selected"
              @click="openCreateChild(selected ?? undefined)"
            >
              新增子分类
            </ElButton>
          </div>
        </div>
      </ElCol>

      <!-- 右侧：详情 -->
      <ElCol :xs="24" :lg="15">
        <div class="wms-card">
          <div class="wms-card__title">分类详情</div>

          <template v-if="selected">
            <ElDescriptions :column="2" border size="small">
              <ElDescriptionsItem label="分类名称">{{ selected.categoryName }}</ElDescriptionsItem>
              <ElDescriptionsItem label="分类 ID">{{ selected.id }}</ElDescriptionsItem>
              <ElDescriptionsItem label="父级 ID">
                {{ selected.parentId === 0 ? '顶级分类' : selected.parentId }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="排序号">{{ selected.sort }}</ElDescriptionsItem>
              <ElDescriptionsItem label="关联商品数">
                <ElTag v-if="selected.productCount > 0" type="primary" size="small">
                  {{ selected.productCount }} 个
                </ElTag>
                <span v-else>无</span>
              </ElDescriptionsItem>
              <ElDescriptionsItem label="子分类数">
                {{ selected.children?.length ?? 0 }}
              </ElDescriptionsItem>
            </ElDescriptions>

            <div class="category-page__detail-actions">
              <ElButton type="primary" size="small" :icon="'Edit'" @click="openEdit(selected)">
                编辑分类
              </ElButton>
              <ElButton size="small" :icon="'FolderAdd'" @click="openCreateChild(selected)">
                新增子分类
              </ElButton>
              <ElButton type="danger" size="small" :icon="'Delete'" @click="handleRemove(selected)">
                删除分类
              </ElButton>
            </div>

            <!-- 子分类列表 -->
            <div v-if="selected.children?.length" class="category-page__children">
              <div class="category-page__children-title">
                子分类（{{ selected.children.length }}）
              </div>
              <ElTable :data="selected.children" size="small" stripe>
                <ElTableColumn prop="categoryName" label="名称" min-width="140" />
                <ElTableColumn prop="sort" label="排序" width="80" align="center" />
                <ElTableColumn label="商品数" width="100" align="right">
                  <template #default="{ row }">
                    <ElTag v-if="row.productCount > 0" type="primary" size="small" effect="plain">
                      {{ row.productCount }}
                    </ElTag>
                    <span v-else>—</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="操作" width="140" align="center">
                  <template #default="{ row }">
                    <ElButton link type="primary" size="small" @click="openEdit(row)">编辑</ElButton>
                    <ElButton link type="danger" size="small" @click="handleRemove(row)">删除</ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </div>
          </template>

          <EmptyState v-else description="请在左侧选择一个分类查看详情" />
        </div>
      </ElCol>
    </ElRow>

    <!-- 新增/编辑对话框 -->
    <ElDialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑分类' : '新增分类'"
      width="480px"
      :close-on-click-modal="false"
    >
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="100px">
        <ElFormItem label="上级分类">
          <ElSelect v-model="form.parentId" style="width: 100%">
            <ElOption label="顶级分类" :value="0" />
            <ElOption
              v-for="opt in parentOptions"
              :key="opt.id"
              :value="opt.id"
              :label="(opt.depth > 1 ? '　'.repeat(opt.depth - 1) : '') + opt.label"
            />
          </ElSelect>
          <div v-if="isEdit" class="category-page__tip">
            不能把分类的父级设置为它自己或它的子分类
          </div>
        </ElFormItem>
        <ElFormItem label="分类名称" prop="categoryName">
          <ElInput v-model="form.categoryName" placeholder="如 饮料、日用品" />
        </ElFormItem>
        <ElFormItem label="排序号">
          <ElInputNumber v-model="form.sort" :min="0" :precision="0" style="width: 140px" />
          <span class="category-page__tip-inline">数字越小越靠前</span>
        </ElFormItem>
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
.category-page {
  &__tree-card {
    display: flex;
    flex-direction: column;
    min-height: 520px;
  }

  &__search {
    margin-bottom: 10px;
  }

  &__tree-scroll {
    flex: 1;
    min-height: 320px;
    max-height: 460px;
  }

  &__node {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
  }

  &__node-name {
    color: #303133;
  }

  &__tree-actions {
    display: flex;
    gap: 8px;
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px dashed #ebeef5;
  }

  &__detail-actions {
    display: flex;
    gap: 8px;
    margin-top: 16px;
  }

  &__children {
    margin-top: 20px;
  }

  &__children-title {
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 600;
    color: #4b5563;
  }

  &__tip {
    margin-top: 4px;
    font-size: 11px;
    color: #a8abb2;
    line-height: 1.6;
  }

  &__tip-inline {
    margin-left: 10px;
    font-size: 11px;
    color: #a8abb2;
  }
}
</style>
