<script setup lang="ts">
/**
 * ECharts 通用封装。
 *
 * 把常用的初始化、响应式尺寸、选项更新与销毁逻辑收在一处，
 * 使各页面只需关心 option 本身。
 *
 * 说明：使用 echarts 的按需引入，避免全量打包（全量约 1MB）。
 */
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  DatasetComponent,
  MarkLineComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  DatasetComponent,
  MarkLineComponent,
  CanvasRenderer
])

interface Props {
  /** 图表配置 */
  option: EChartsOption
  /** 容器高度 */
  height?: string
  /** 是否在数据变化时自动重新渲染 */
  autoResize?: boolean
  /** 加载状态 */
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  height: '320px',
  autoResize: true,
  loading: false
})

const containerRef = ref<HTMLDivElement>()
// 用 shallowRef：ECharts 实例是大型对象，深层响应式会带来无谓开销
const chart = shallowRef<echarts.ECharts>()
let observer: ResizeObserver | null = null

const styleHeight = computed(() => props.height)

function render() {
  if (!chart.value) return
  // notMerge = true：避免前后两次 option 的残留配置互相干扰
  chart.value.setOption(props.option, true)
}

function init() {
  if (!containerRef.value) return
  chart.value = echarts.init(containerRef.value)
  render()

  if (props.autoResize) {
    observer = new ResizeObserver(() => chart.value?.resize())
    observer.observe(containerRef.value)
  }
}

function dispose() {
  observer?.disconnect()
  observer = null
  chart.value?.dispose()
  chart.value = undefined
}

onMounted(init)
onBeforeUnmount(dispose)

watch(() => props.option, render, { deep: true })

watch(
  () => props.loading,
  (value) => {
    if (!chart.value) return
    if (value) {
      chart.value.showLoading('default', {
        text: '加载中',
        color: '#2563eb',
        textColor: '#909399',
        maskColor: 'rgba(255, 255, 255, 0.85)',
        zlevel: 0
      })
    } else {
      chart.value.hideLoading()
    }
  }
)

defineExpose({
  /** 供父组件在容器尺寸变化后手动触发重绘 */
  resize: () => chart.value?.resize(),
  getInstance: () => chart.value
})
</script>

<template>
  <div ref="containerRef" class="chart-container" :style="{ height: styleHeight }" />
</template>

<style scoped>
.chart-container {
  width: 100%;
}
</style>
