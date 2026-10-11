<script setup lang="ts">
/**
 * 库位平面图与拣货路径可视化。
 *
 * 为什么手写 SVG 而不用图表库：
 * 仓库是规则网格，拣货路径是带顺序的折线，这类图形用图表库反而别扭；
 * 手写 SVG 可以精确控制坐标、编号、高亮与动画，效果更接近真实 WMS 系统。
 *
 * 坐标约定与后端及算法内核完全一致：
 *   横向 = 巷道号 x，纵向 = 纵深号 y，出入口 I/O 位于 (0, 0)。
 */
import { computed } from 'vue'

interface PathPoint {
  aisleNo: number
  rowNo: number
}

interface Props {
  /** 仓库巷道数 */
  aisles: number
  /** 仓库纵深数 */
  rows: number
  /** 行走顺序，含起终点出入口 (0,0) */
  route?: PathPoint[]
  /** 需要标出的拣货点（用于展示全部货位分布） */
  picks?: Array<{ aisleNo: number; rowNo: number; label?: string }>
  /** 画布内边距 */
  padding?: number
  /** 是否显示序号 */
  showSequence?: boolean
  /** 路径总长度（米），仅用于图例标注 */
  distance?: number
}

const props = withDefaults(defineProps<Props>(), {
  route: () => [],
  picks: () => [],
  padding: 44,
  showSequence: true,
  distance: 0
})

/** 单个格子的像素尺寸：随货位规模自适应，保证大仓库也能完整显示 */
const CELL = 26
const GAP = 8
const PAD = computed(() => props.padding)

const stepX = computed(() => CELL + GAP)
const stepY = computed(() => CELL + GAP)

/** 画布尺寸：多预留一格用于放置出入口 (0,0) */
const width = computed(() => PAD.value * 2 + (props.aisles + 1) * stepX.value)
const height = computed(() => PAD.value * 2 + (props.rows + 1) * stepY.value)

/** 坐标换算：把 (x, y) 映射为格子左上角的像素位置 */
function cellPos(x: number, y: number) {
  return {
    left: PAD.value + x * stepX.value,
    top: PAD.value + y * stepY.value
  }
}

/** 格子中心点：用于画折线 */
function cellCenter(p: PathPoint) {
  const pos = cellPos(p.aisleNo, p.rowNo)
  return { cx: pos.left + CELL / 2, cy: pos.top + CELL / 2 }
}

const ioCenter = computed(() => cellCenter({ aisleNo: 0, rowNo: 0 }))

/** 折线路径字符串 */
const polyline = computed(() => {
  return props.route.map((p) => {
    const c = cellCenter(p)
    return `${c.cx},${c.cy}`
  })
})

/** 拣货点集合的键，用于快速判断某货位是否被访问 */
const pickKeySet = computed(() => {
  const set = new Set<string>()
  props.picks.forEach((p) => set.add(`${p.aisleNo}-${p.rowNo}`))
  return set
})

/** 路径中被访问的货位（去重，去掉出入口），带访问序号 */
const visitedCells = computed(() => {
  const seen = new Map<string, { x: number; y: number; seq: number }>()
  let seq = 0
  props.route.forEach((p) => {
    if (p.aisleNo === 0 && p.rowNo === 0) return
    const key = `${p.aisleNo}-${p.rowNo}`
    if (seen.has(key)) return
    seq += 1
    seen.set(key, { x: p.aisleNo, y: p.rowNo, seq })
  })
  return seen
})

/** 渲染用的全部货位格子 */
const gridCells = computed(() => {
  const cells: Array<{
    key: string
    x: number
    y: number
    left: number
    top: number
    isPick: boolean
    isVisited: boolean
    seq: number
  }> = []
  for (let x = 1; x <= props.aisles; x++) {
    for (let y = 1; y <= props.rows; y++) {
      const pos = cellPos(x, y)
      const key = `${x}-${y}`
      const visited = visitedCells.value.get(key)
      cells.push({
        key,
        x,
        y,
        left: pos.left,
        top: pos.top,
        isPick: pickKeySet.value.has(key),
        isVisited: !!visited,
        seq: visited?.seq ?? 0
      })
    }
  }
  return cells
})

/** 巷道标签（货位较多时每 2 条标一个，避免拥挤） */
const aisleLabels = computed(() => {
  const list: Array<{ x: number; label: string; left: number; top: number }> = []
  const interval = props.aisles > 12 ? 2 : 1
  for (let x = 1; x <= props.aisles; x += interval) {
    const pos = cellPos(x, 1)
    list.push({ x, label: `A${x}`, left: pos.left + CELL / 2, top: PAD.value - 10 })
  }
  return list
})

/** 纵深标签 */
const rowLabels = computed(() => {
  const list: Array<{ y: number; label: string; left: number; top: number }> = []
  const interval = props.rows > 12 ? 2 : 1
  for (let y = 1; y <= props.rows; y += interval) {
    const pos = cellPos(1, y)
    list.push({ y, label: String(y), left: PAD.value - 18, top: pos.top + CELL / 2 + 4 })
  }
  return list
})

/** 路径总长度（米）：由父组件通过 props.distance 传入，仅用于图例标注 */
</script>

<template>
  <div class="pick-map">
    <div class="pick-map__legend">
      <span class="pick-map__legend-item">
        <i class="pick-map__dot pick-map__dot--io" />出入口 I/O
      </span>
      <span class="pick-map__legend-item">
        <i class="pick-map__dot pick-map__dot--free" />普通货位
      </span>
      <span class="pick-map__legend-item">
        <i class="pick-map__dot pick-map__dot--pick" />拣货点
      </span>
      <span class="pick-map__legend-item">
        <i class="pick-map__line" />拣货路径
      </span>
      <span v-if="props.distance" class="pick-map__legend-item pick-map__legend-distance">
        路径长度 {{ props.distance.toFixed(2) }} 米
      </span>
    </div>

    <div class="pick-map__canvas">
      <svg :width="width" :height="height" :viewBox="`0 0 ${width} ${height}`" role="img"
        aria-label="库位平面图与拣货路径">
        <defs>
          <linearGradient id="pathGradient" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#2563eb" />
            <stop offset="100%" stop-color="#22d3ee" />
          </linearGradient>
          <filter id="pathGlow" x="-40%" y="-40%" width="180%" height="180%">
            <feGaussianBlur stdDeviation="2.4" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <!-- 巷道与纵深标签 -->
        <g class="pick-map__labels">
          <text v-for="a in aisleLabels" :key="'ax' + a.x" :x="a.left" :y="a.top"
            text-anchor="middle">{{ a.label }}</text>
          <text v-for="r in rowLabels" :key="'ry' + r.y" :x="r.left" :y="r.top"
            text-anchor="end">{{ r.label }}</text>
        </g>

        <!-- 货位格子 -->
        <g>
          <template v-for="cell in gridCells" :key="cell.key">
            <rect :x="cell.left" :y="cell.top" :width="CELL" :height="CELL" rx="4"
              class="pick-map__cell"
              :class="{
                'is-pick': cell.isPick && !cell.isVisited,
                'is-visited': cell.isVisited
              }" />
            <text v-if="cell.isVisited && showSequence" :x="cell.left + CELL / 2"
              :y="cell.top + CELL / 2 + 3.5" text-anchor="middle" class="pick-map__seq">
              {{ cell.seq }}
            </text>
          </template>
        </g>

        <!-- 拣货路径折线：先描粗底再叠亮色，形成发光效果 -->
        <g v-if="polyline.length > 1" class="pick-map__route">
          <polyline :points="polyline.join(' ')" class="pick-map__route-halo" />
          <polyline :points="polyline.join(' ')" class="pick-map__route-line" filter="url(#pathGlow)"
            :style="{ strokeDasharray: 'var(--route-len)', strokeDashoffset: 'var(--route-offset)' }" />
          <!-- 方向箭头：每段中点标一个小三角 -->
          <g v-for="(pt, i) in route.slice(1)" :key="'arw' + i" class="pick-map__arrow">
            <circle :cx="(cellCenter(route[i]).cx + cellCenter(pt).cx) / 2"
              :cy="(cellCenter(route[i]).cy + cellCenter(pt).cy) / 2" r="2.6" />
          </g>
        </g>

        <!-- 出入口 -->
        <g>
          <rect :x="cellPos(0, 0).left" :y="cellPos(0, 0).top" :width="CELL" :height="CELL" rx="5"
            class="pick-map__io" />
          <text :x="ioCenter.cx" :y="ioCenter.cy + 4" text-anchor="middle" class="pick-map__io-text">
            I/O
          </text>
        </g>

        <!-- 起点与终点标记 -->
        <g v-if="polyline.length > 1">
          <circle :cx="ioCenter.cx" :cy="ioCenter.cy" r="4" class="pick-map__endpoint" />
        </g>
      </svg>
    </div>

    <div v-if="!route.length" class="pick-map__empty">
      尚未规划拣货路径，点击「规划路径」后此处将显示行走顺序
    </div>
  </div>
</template>

<style scoped lang="scss">
.pick-map {
  &__legend {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 16px;
    margin-bottom: 10px;
    font-size: 12px;
    color: #606266;
  }

  &__legend-item {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }

  &__legend-distance {
    margin-left: auto;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__dot {
    width: 11px;
    height: 11px;
    border-radius: 3px;
    display: inline-block;

    &--io {
      background: #1f2937;
    }

    &--free {
      background: #eef2f7;
      border: 1px solid #dbe1ea;
    }

    &--pick {
      background: #f59e0b;
    }
  }

  &__line {
    width: 18px;
    height: 3px;
    border-radius: 2px;
    background: linear-gradient(90deg, #2563eb, #22d3ee);
    display: inline-block;
  }

  &__canvas {
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 4px;
    background: #fbfcfe;
    border: 1px solid #eef1f6;
    border-radius: 8px;
  }

  &__labels text {
    font-size: 10px;
    fill: #a8abb2;
    font-family: 'Consolas', monospace;
  }

  &__cell {
    fill: #eef2f7;
    stroke: #dbe1ea;
    stroke-width: 1;

    &.is-pick {
      fill: #fef3c7;
      stroke: #f59e0b;
      stroke-width: 1.5;
    }

    &.is-visited {
      fill: #dbeafe;
      stroke: #2563eb;
      stroke-width: 1.5;
    }
  }

  &__seq {
    font-size: 11px;
    font-weight: 700;
    fill: #1d4ed8;
    font-family: 'Consolas', monospace;
  }

  &__route-halo {
    fill: none;
    stroke: #ffffff;
    stroke-width: 7;
    stroke-linecap: round;
    stroke-linejoin: round;
    opacity: 0.9;
  }

  &__route-line {
    fill: none;
    stroke: url(#pathGradient);
    stroke-width: 3;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-dasharray: 6 4;
    animation: route-flow 1.1s linear infinite;
  }

  &__arrow circle {
    fill: #2563eb;
    opacity: 0.75;
  }

  &__io {
    fill: #1f2937;
  }

  &__io-text {
    font-size: 9px;
    font-weight: 700;
    fill: #ffffff;
    font-family: 'Consolas', monospace;
  }

  &__endpoint {
    fill: #22d3ee;
    stroke: #fff;
    stroke-width: 1.6;
  }

  &__empty {
    margin-top: 10px;
    padding: 10px;
    text-align: center;
    font-size: 12px;
    color: #a8abb2;
    background: #f8fafc;
    border-radius: 6px;
  }
}

@keyframes route-flow {
  to {
    stroke-dashoffset: -10;
  }
}
</style>
