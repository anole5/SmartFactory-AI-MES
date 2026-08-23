<template>
  <div class="page-card">
    <!-- 工具栏：日期选择 + 执行排程 -->
    <div class="toolbar">
      <el-date-picker
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="排程日期"
        style="width: 150px"
        @change="loadGantt"
      />
      <el-button type="primary" v-permission="'production:schedule:run'" :loading="running" @click="handleRun">
        <el-icon><MagicStick /></el-icon>&nbsp;执行排程
      </el-button>
      <el-button @click="loadGantt">
        <el-icon><Refresh /></el-icon>&nbsp;刷新
      </el-button>
      <el-tag v-if="runResult" type="success" effect="plain">
        已排程 {{ runResult.workOrderCount }} 个工单 / {{ runResult.taskCount }} 个任务（{{ runResult.runAt }}）
      </el-tag>
      <div class="spacer" />
      <el-tag type="info" effect="plain">排程按 优先级 → 交期 排序，同工位任务串行；红色横道 = 已逾期</el-tag>
    </div>

    <!-- 工单颜色图例 -->
    <div v-if="workOrderLegend.length" class="legend-bar">
      <span v-for="w in workOrderLegend" :key="w.workOrderId" class="legend-chip">
        <span class="legend-dot" :style="{ background: w.color }" />
        {{ w.workOrderNo }}
      </span>
    </div>

    <!-- 甘特图 -->
    <div ref="ganttEl" class="gantt-chart" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import type { CustomSeriesRenderItemAPI, CustomSeriesRenderItemParams } from 'echarts'
import { scheduleApi } from '@/api'
import type { GanttTask, ScheduleRunResult } from '@/api/types'
import { PRIORITY, TASK_STATUS, labelOf } from '@/constants/dict'

// ---------- 状态 ----------
const date = ref(today())
const tasks = ref<GanttTask[]>([])
const running = ref(false)
const runResult = ref<ScheduleRunResult | null>(null)
const ganttEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

function today(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// ---------- 甘特图（custom series renderItem：x 轴毫秒 value，y 轴工位 category） ----------
/** 横道调色板：颜色 = 工单 id % 调色板长度 */
const PALETTE = ['#409eff', '#67c23a', '#e6a23c', '#9f7ef5', '#00bcd4', '#ff7a45', '#7a9ef5', '#5a9e6f']
const OVERDUE_COLOR = '#f56c6c'
const BAR_HEIGHT = 18

const parseMs = (s?: string) => (s ? new Date(s.replace(' ', 'T')).getTime() : 0)

const workOrderLegend = computed(() => {
  const map = new Map<string, { workOrderId: string; workOrderNo: string; color: string }>()
  for (const t of tasks.value) {
    if (map.has(t.workOrderId)) continue
    map.set(t.workOrderId, {
      workOrderId: t.workOrderId,
      workOrderNo: t.workOrderNo ?? '-',
      color: PALETTE[Number(t.workOrderId) % PALETTE.length],
    })
  }
  return [...map.values()]
})

function renderGantt() {
  if (!chart) return
  const rows = tasks.value
  // 工位行：按后端返回顺序去重（未分配工位兜底名）
  const workstationNames = [...new Set(rows.map((t) => t.workstationName || '未分配工位'))]
  const rowIndexOf = new Map(workstationNames.map((name, i) => [name, i]))

  if (!rows.length) {
    chart.setOption({
      title: {
        text: '暂无排程数据，请先点击「执行排程」',
        left: 'center',
        top: 'middle',
        textStyle: { color: '#909399', fontSize: 14 },
      },
      xAxis: { show: false },
      yAxis: { show: false },
      series: [],
    })
    return
  }

  const dayStart = new Date(`${date.value} 00:00:00`.replace(' ', 'T')).getTime()
  const dayEnd = dayStart + 24 * 3600 * 1000
  const starts = rows.map((t) => parseMs(t.planStartTime))
  const ends = rows.map((t) => parseMs(t.planEndTime))
  // x 轴范围 = 该日 00:00 ~ 次日 00:00 与数据起止的并集
  const xMin = Math.min(dayStart, ...starts)
  const xMax = Math.max(dayEnd, ...ends)

  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: { dataIndex: number }) => tooltipHtml(rows[p.dataIndex]),
    },
    grid: { left: 10, right: 30, top: 30, bottom: 10, containLabel: true },
    xAxis: {
      type: 'value',
      min: xMin,
      max: xMax,
      axisLabel: {
        formatter: (ms: number) => `${String(Math.floor(ms / 3600000) % 24).padStart(2, '0')}:${String(Math.floor(ms / 60000) % 60).padStart(2, '0')}`,
      },
      axisLine: { show: true },
      splitLine: { lineStyle: { color: '#f0f2f5' } },
    },
    yAxis: {
      type: 'category',
      data: workstationNames,
      axisLabel: { fontSize: 12 },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [
      {
        type: 'custom',
        renderItem: (params: CustomSeriesRenderItemParams, api: CustomSeriesRenderItemAPI) => {
          const startMs = api.value(0) as number
          const endMs = api.value(1) as number
          const rowIndex = api.value(2) as number
          const colorIdx = api.value(3) as number
          const overdue = api.value(4) as number
          // 毫秒→像素：x 取两端，y 取行中心
          const x1 = api.coord([startMs, rowIndex])[0]
          const x2 = api.coord([endMs, rowIndex])[0]
          const y = api.coord([startMs, rowIndex])[1]
          // 裁切到绘图区：跨日任务在本日窗口外截断显示（后端口径：跨日两天各返回一次）
          const cs = params.coordSys as unknown as { x: number; width: number }
          const clipX1 = Math.max(x1, cs.x)
          const clipX2 = Math.min(x2, cs.x + cs.width)
          return {
            type: 'rect',
            shape: {
              x: clipX1,
              y: y - BAR_HEIGHT / 2,
              width: Math.max(clipX2 - clipX1, 2),
              height: BAR_HEIGHT,
            },
            style: {
              fill: overdue ? OVERDUE_COLOR : PALETTE[colorIdx % PALETTE.length],
              stroke: overdue ? '#c45656' : 'rgba(0,0,0,0.12)',
              lineWidth: overdue ? 2 : 1,
            },
          }
        },
        data: rows.map((t, i) => [
          parseMs(t.planStartTime),
          parseMs(t.planEndTime),
          rowIndexOf.get(t.workstationName || '未分配工位') ?? 0,
          Number(t.workOrderId) % PALETTE.length,
          t.isOverdue ? 1 : 0,
          i,
        ]),
      },
    ],
  })
}

/** tooltip：完整任务信息（html 字符串） */
function tooltipHtml(t?: GanttTask): string {
  if (!t) return ''
  const rows = [
    ['任务号', t.taskNo],
    ['工单号', t.workOrderNo ?? '-'],
    ['工序', `${t.sequenceNo}. ${t.processNameSnapshot ?? '-'}（${t.processCodeSnapshot ?? '-'}）`],
    ['工位', t.workstationName || '未分配工位'],
    ['计划', `${t.planStartTime ?? '-'} ~ ${t.planEndTime ?? '-'}`],
    ['数量', String(t.planQty)],
    ['优先级', labelOf(PRIORITY, t.priority)],
    ['状态', labelOf(TASK_STATUS, t.status)],
  ]
  if (t.isOverdue) rows.push(['⚠', '已逾期'])
  return rows.map(([k, v]) => `<b>${k}</b>: ${v}`).join('<br/>')
}

// ---------- 加载与执行排程 ----------
async function loadGantt() {
  const rows = await scheduleApi.gantt(date.value).catch(() => null)
  tasks.value = rows ?? []
  renderGantt()
}

async function handleRun() {
  running.value = true
  try {
    const res = await scheduleApi.run().catch(() => null)
    if (res) {
      runResult.value = res
      ElMessage.success(`排程完成：${res.workOrderCount} 个工单 / ${res.taskCount} 个任务`)
      await loadGantt()
    }
  } finally {
    running.value = false
  }
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  chart = echarts.init(ganttEl.value!)
  loadGantt()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.gantt-chart {
  height: 520px;
  margin-top: 12px;
}

.legend-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
}

.legend-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
}
</style>
