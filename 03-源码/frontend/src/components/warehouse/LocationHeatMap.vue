<script setup lang="ts">
/**
 * 库位热力图。
 *
 * 用颜色深浅直观表达"货位离出入口有多远"：
 * 颜色越深绿表示越靠近出入口（行走距离越短，越适合放高频商品），
 * 越偏红表示越远。同时区分空闲与占用状态，鼠标悬停显示货位详情。
 *
 * 这张图是理解货位分配算法最直观的工具：
 * 算法要做的就是把出库频次高的商品尽量放到深绿色格子里。
 */
import { computed, ref } from 'vue'

interface LocationCell {
  id: number
  locationCode: string
  aisleNo: number
  rowNo: number
  distance: number
  status: number
  zoneId?: number
}

interface Props {
  locations: LocationCell[]
  aisles: number
  rows: number
}

const props = defineProps<Props>()

const CELL = 24
const GAP = 5
const PAD = 42

const stepX = CELL + GAP
const stepY = CELL + GAP

const width = computed(() => PAD * 2 + (props.aisles + 1) * stepX)
const height = computed(() => PAD * 2 + (props.rows + 1) * stepY)

function cellPos(x: number, y: number) {
  return { left: PAD + x * stepX, top: PAD + y * stepY }
}

/** 距离区间：用于颜色映射 */
const distanceRange = computed(() => {
  if (!props.locations.length) return { min: 0, max: 1 }
  const values = props.locations.map((l) => l.distance)
  return { min: Math.min(...values), max: Math.max(...values) }
})

/**
 * 把距离映射为颜色。
 * 近端 = 绿色（#10b981），中段 = 蓝色（#3b82f6），远端 = 橙红（#f59e0b）
 * 用 HSL 插值获得平滑过渡。
 */
function distanceColor(distance: number): string {
  const { min, max } = distanceRange.value
  const ratio = max === min ? 0 : (distance - min) / (max - min)
  // 色相从 152（绿）经 210（蓝）到 30（橙），跨度取 0..1
  const hue = ratio < 0.5 ? 152 + ratio * 2 * (210 - 152) : 210 + (ratio - 0.5) * 2 * (390 - 210)
  const h = hue % 360
  const saturation = 62 + ratio * 8
  const lightness = 62 - ratio * 12
  return `hsl(${h.toFixed(0)}, ${saturation.toFixed(0)}%, ${lightness.toFixed(0)}%)`
}

/** 网格：按坐标建索引，便于快速查找 */
const grid = computed(() => {
  const map = new Map<string, LocationCell>()
  props.locations.forEach((l) => map.set(`${l.aisleNo}-${l.rowNo}`, l))
  const cells: Array<LocationCell & { left: number; top: number; color: string }> = []
  for (let x = 1; x <= props.aisles; x++) {
    for (let y = 1; y <= props.rows; y++) {
      const loc = map.get(`${x}-${y}`)
      if (!loc) continue
      const pos = cellPos(x, y)
      cells.push({ ...loc, left: pos.left, top: pos.top, color: distanceColor(loc.distance) })
    }
  }
  return cells
})

/** 颜色图例：从最近到最远分 5 档 */
const legend = computed(() => {
  const { min, max } = distanceRange.value
  const steps = 5
  const items: Array<{ color: string; label: string }> = []
  for (let i = 0; i < steps; i++) {
    const distance = min + ((max - min) * i) / (steps - 1)
    items.push({
      color: distanceColor(distance),
      label: `${distance.toFixed(1)}m`
    })
  }
  return items
})

/** 悬停提示 */
const hovered = ref<(LocationCell & { left: number; top: number; color: string }) | null>(null)

function onEnter(cell: LocationCell & { left: number; top: number; color: string }) {
  hovered.value = cell
}

function onLeave() {
  hovered.value = null
}

/** 巷道标签：货位多时抽稀 */
const aisleLabels = computed(() => {
  const interval = props.aisles > 14 ? 2 : 1
  const list: Array<{ label: string; left: number; top: number }> = []
  for (let x = 1; x <= props.aisles; x += interval) {
    const pos = cellPos(x, 1)
    list.push({ label: `A${x}`, left: pos.left + CELL / 2, top: PAD - 10 })
  }
  return list
})

const rowLabels = computed(() => {
  const interval = props.rows > 14 ? 2 : 1
  const list: Array<{ label: string; left: number; top: number }> = []
  for (let y = 1; y <= props.rows; y += interval) {
    const pos = cellPos(1, y)
    list.push({ label: String(y), left: PAD - 18, top: pos.top + CELL / 2 + 4 })
  }
  return list
})

/** 统计信息 */
const stats = computed(() => {
  const total = props.locations.length
  const used = props.locations.filter((l) => l.status === 2).length
  return { total, used, free: total - used }
})
</script>

<template>
  <div class="heat-map">
    <div class="heat-map__bar">
      <div class="heat-map__stats">
        <span>货位总数 <b>{{ stats.total }}</b></span>
        <span>占用 <b class="is-used">{{ stats.used }}</b></span>
        <span>空闲 <b class="is-free">{{ stats.free }}</b></span>
      </div>

      <div class="heat-map__legend">
        <span class="heat-map__legend-label">距出入口</span>
        <span class="heat-map__legend-near">近</span>
        <span
          v-for="(item, i) in legend"
          :key="i"
          class="heat-map__legend-block"
          :style="{ background: item.color }"
          :title="item.label"
        />
        <span class="heat-map__legend-far">远</span>
      </div>
    </div>

    <div class="heat-map__canvas">
      <svg :width="width" :height="height" :viewBox="`0 0 ${width} ${height}`" role="img"
        aria-label="货位距离热力图">
        <!-- 坐标标签 -->
        <g class="heat-map__labels">
          <text v-for="(a, i) in aisleLabels" :key="'ax' + i" :x="a.left" :y="a.top"
            text-anchor="middle">{{ a.label }}</text>
          <text v-for="(r, i) in rowLabels" :key="'ry' + i" :x="r.left" :y="r.top"
            text-anchor="end">{{ r.label }}</text>
        </g>

        <!-- 货位格子 -->
        <g>
          <rect
            v-for="cell in grid"
            :key="cell.id"
            :x="cell.left"
            :y="cell.top"
            :width="CELL"
            :height="CELL"
            rx="4"
            :fill="cell.color"
            :stroke="cell.status === 2 ? '#1f2937' : 'transparent'"
            :stroke-width="cell.status === 2 ? 1.8 : 0"
            class="heat-map__cell"
            @mouseenter="onEnter(cell)"
            @mouseleave="onLeave"
          />
          <!-- 占用货位加一个小角标 -->
          <circle
            v-for="cell in grid.filter((c) => c.status === 2)"
            :key="'dot' + cell.id"
            :cx="cell.left + CELL - 5"
            :cy="cell.top + 5"
            r="2.6"
            fill="#1f2937"
            stroke="#fff"
            stroke-width="1"
          />
        </g>

        <!-- 出入口 -->
        <g>
          <rect :x="cellPos(0, 0).left" :y="cellPos(0, 0).top" :width="CELL" :height="CELL" rx="5"
            class="heat-map__io" />
          <text :x="cellPos(0, 0).left + CELL / 2" :y="cellPos(0, 0).top + CELL / 2 + 4"
            text-anchor="middle" class="heat-map__io-text">I/O</text>
        </g>
      </svg>

      <!-- 悬停详情 -->
      <div
        v-if="hovered"
        class="heat-map__tooltip"
        :style="{ left: hovered.left + CELL + 8 + 'px', top: hovered.top - 4 + 'px' }"
      >
        <div class="heat-map__tooltip-code">{{ hovered.locationCode }}</div>
        <div>坐标：({{ hovered.aisleNo }}, {{ hovered.rowNo }})</div>
        <div>距出入口：{{ hovered.distance.toFixed(2) }} 米</div>
        <div>
          状态：
          <span :class="hovered.status === 2 ? 'is-used' : 'is-free'">
            {{ hovered.status === 2 ? '占用' : hovered.status === 1 ? '空闲' : '停用' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.heat-map {
  &__bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
    font-size: 12px;
    color: #606266;
  }

  &__stats {
    display: flex;
    gap: 16px;

    b {
      color: #1f2937;
      font-weight: 600;
    }

    .is-used {
      color: #1f2937;
    }

    .is-free {
      color: #10b981;
    }
  }

  &__legend {
    display: flex;
    align-items: center;
    gap: 3px;
  }

  &__legend-label {
    margin-right: 6px;
    color: #909399;
  }

  &__legend-near,
  &__legend-far {
    margin: 0 5px;
    color: #909399;
  }

  &__legend-block {
    width: 22px;
    height: 12px;
    border-radius: 2px;
  }

  &__canvas {
    position: relative;
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
    cursor: pointer;
    transition: opacity 0.15s;

    &:hover {
      opacity: 0.82;
    }
  }

  &__io {
    fill: #1f2937;
  }

  &__io-text {
    font-size: 9px;
    font-weight: 700;
    fill: #fff;
    font-family: 'Consolas', monospace;
  }

  &__tooltip {
    position: absolute;
    z-index: 5;
    min-width: 130px;
    padding: 8px 10px;
    background: rgb(31 41 55 / 94%);
    color: #fff;
    border-radius: 6px;
    font-size: 11px;
    line-height: 1.75;
    pointer-events: none;
    box-shadow: 0 4px 12px rgb(0 0 0 / 18%);
  }

  &__tooltip-code {
    font-weight: 700;
    font-family: 'Consolas', monospace;
    margin-bottom: 2px;
  }

  &__tooltip .is-used {
    color: #fbbf24;
  }

  &__tooltip .is-free {
    color: #34d399;
  }
}
</style>
