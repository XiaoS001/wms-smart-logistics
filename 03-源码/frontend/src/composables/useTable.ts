import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 列表页通用逻辑。
 *
 * 把「查询条件 → 加载 → 筛选 → 分页 → 计数」这套在管理后台里重复出现的逻辑收敛到一处，
 * 页面只提供 fetcher 与筛选条件，从而减少样板代码并统一交互。
 *
 * 说明：本系统的列表接口一次返回全量（数据集规模有限，最多几百条），
 * 因此分页在前端完成，避免为分页再增加后端接口。
 */
export interface UseTableOptions<T> {
  /** 数据加载函数 */
  fetcher: () => Promise<T[]>
  /** 每页条数，默认 20 */
  pageSize?: number
  /** 是否在创建后立即加载 */
  immediate?: boolean
  /** 客户端筛选函数；返回 true 表示该行保留 */
  filter?: (row: T) => boolean
  /** 加载失败时的提示文案 */
  errorMessage?: string
}

export function useTable<T>(options: UseTableOptions<T>) {
  const { fetcher, pageSize = 20, immediate = true, filter, errorMessage } = options

  const loading = ref(false)
  const rawList = ref<T[]>([]) as { value: T[] }
  const currentPage = ref(1)
  const size = ref(pageSize)

  /** 经筛选后的完整数据集 */
  const filteredList = computed<T[]>(() => {
    if (!filter) return rawList.value
    return rawList.value.filter(filter)
  })

  const total = computed(() => filteredList.value.length)

  /** 当前页数据 */
  const pageList = computed<T[]>(() => {
    const start = (currentPage.value - 1) * size.value
    return filteredList.value.slice(start, start + size.value)
  })

  /** 加载数据 */
  async function load(silent = false) {
    loading.value = true
    try {
      rawList.value = await fetcher()
    } catch {
      if (!silent && errorMessage) {
        ElMessage.error(errorMessage)
      }
      rawList.value = []
    } finally {
      loading.value = false
    }
  }

  /** 重置到第一页并重新加载 */
  async function refresh() {
    currentPage.value = 1
    await load()
  }

  // 筛选条件变化时回到第一页，避免停留在超出范围的页码上
  watch(total, (value) => {
    const maxPage = Math.max(1, Math.ceil(value / size.value))
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage
    }
  })

  if (immediate) {
    void load()
  }

  return {
    loading,
    rawList,
    filteredList,
    pageList,
    total,
    currentPage,
    pageSize: size,
    load,
    refresh
  }
}

/**
 * 表单对话框通用逻辑。
 *
 * 统一「打开 → 编辑/新增 → 校验 → 提交 → 关闭 → 刷新列表」的流程与提交按钮的 loading 状态。
 */
export interface UseFormDialogOptions<T> {
  /** 表单初始值工厂，保证每次打开都是干净对象 */
  initial: () => T
  /** 提交函数 */
  submit: (data: T, isEdit: boolean) => Promise<unknown>
  /** 成功后回调（通常是刷新列表） */
  onSuccess?: () => void | Promise<void>
  /** 成功提示文案 */
  successMessage?: string
  /** 编辑时用于回填的转换函数 */
  toForm?: (row: T) => T
}

export function useFormDialog<T extends Record<string, unknown>>(options: UseFormDialogOptions<T>) {
  const { initial, submit, onSuccess, successMessage = '保存成功', toForm } = options

  const visible = ref(false)
  const submitting = ref(false)
  const isEdit = ref(false)
  const form = reactive(initial()) as T

  /** 重置表单为初始值 */
  function reset() {
    const fresh = initial()
    Object.keys(fresh).forEach((key) => {
      ;(form as Record<string, unknown>)[key] = (fresh as Record<string, unknown>)[key]
    })
  }

  /** 打开新增对话框 */
  function openCreate() {
    reset()
    isEdit.value = false
    visible.value = true
  }

  /** 打开编辑对话框 */
  function openEdit(row: T) {
    reset()
    const data = toForm ? toForm(row) : row
    Object.keys(data).forEach((key) => {
      ;(form as Record<string, unknown>)[key] = (data as Record<string, unknown>)[key]
    })
    isEdit.value = true
    visible.value = true
  }

  /** 提交 */
  async function handleSubmit() {
    submitting.value = true
    try {
      await submit(form as T, isEdit.value)
      ElMessage.success(successMessage)
      visible.value = false
      await onSuccess?.()
    } catch {
      // 错误提示已由请求拦截器统一处理
    } finally {
      submitting.value = false
    }
  }

  return { visible, submitting, isEdit, form, openCreate, openEdit, handleSubmit, reset }
}

/**
 * 删除确认与执行。
 *
 * 统一二次确认文案与删除后的提示，避免每个页面各写一套。
 */
export async function confirmRemove(
  name: string,
  action: () => Promise<unknown>,
  onSuccess?: () => void | Promise<void>
): Promise<boolean> {
  try {
    await ElMessageBox.confirm(`确定要删除「${name}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return false
  }

  try {
    await action()
    ElMessage.success('删除成功')
    await onSuccess?.()
    return true
  } catch {
    // 失败提示由请求拦截器处理（例如"该分类下仍有商品"）
    return false
  }
}
