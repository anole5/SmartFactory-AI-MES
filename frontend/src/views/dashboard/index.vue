<template>
  <div class="board">
    <div class="board-head">
      <span class="board-title">智能电视生产线 · 实时生产看板</span>
      <el-tag type="success" effect="dark" size="small">数据每 10s 自动刷新</el-tag>
    </div>

    <!-- KPI -->
    <div class="kpi-row">
      <div v-for="kpi in kpis" :key="kpi.label" class="kpi-card">
        <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        <div class="kpi-label">{{ kpi.label }}</div>
      </div>
    </div>

    <!-- 图表 -->
    <div class="chart-grid">
      <div class="chart-panel">
        <div class="panel-title">进行中工单进度</div>
        <div ref="workOrderEl" class="chart" />
      </div>
      <div class="chart-panel">
        <div class="panel-title">工序良率（%）</div>
        <div ref="processEl" class="chart" />
      </div>
      <div class="chart-panel">
        <div class="panel-title">不良分布</div>
        <div ref="defectEl" class="chart" />
      </div>
      <div class="chart-panel">
        <div class="panel-title">设备状态分布</div>
        <div ref="equipmentEl" class="chart" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api'
import type {
  DashboardEquipment,
  DashboardQuality,
  DashboardSummary,
  DashboardWorkOrderItem,
} from '@/api/types'
import { DEFECT_CODES, EQUIPMENT_STATUS } from '@/constants/dict'

// ---------- 数据 ----------
const summary = ref<DashboardSummary | null>(null)
const workOrders = ref<DashboardWorkOrderItem[]>([])
const quality = ref<DashboardQuality | null>(null)
const equipment = ref<DashboardEquipment | null>(null)

const kpis = computed(() => [
  { label: '今日产量', value: summary.value ? String(summary.value.todayOutputQty) : '-', color: '#00d4ff' },
  {
    label: '今日良率',
    value: summary.value?.todayYieldRate != null ? `${summary.value.todayYieldRate}%` : '-',
    color: '#67c23a',
  },
  { label: '今日不良', value: summary.value ? String(summary.value.todayDefectQty) : '-', color: '#f56c6c' },
  { label: '今日报工数', value: summary.value ? String(summary.value.todayReportCount) : '-', color: '#9f7ef5' },
  { label: '进行中工单', value: summary.value ? String(summary.value.inProgressWorkOrderCount) : '-', color: '#e6a23c' },
  { label: '未关闭异常', value: summary.value ? String(summary.value.openExceptionCount) : '-', color: '#ff7a45' },
])

// ---------- 图表 ----------
const workOrderEl = ref<HTMLDivElement>()
const processEl = ref<HTMLDivElement>()
const defectEl = ref<HTMLDivElement>()
const equipmentEl = ref<HTMLDivElement>()

let workOrderChart: echarts.ECharts | null = null
let processChart: echarts.ECharts | null = null
let defectChart: echarts.ECharts | null = null
let equipmentChart: echarts.ECharts | null = null

const AXIS = '#7fa8c9'
const SPLIT = 'rgba(255,255,255,0.08)'
const EQUIPMENT_COLORS: Record<string, string> = {
  RUNNING: '#00d4ff',
  IDLE: '#67c23a',
  STOPPED: '#f56c6c',
  MAINTENANCE: '#e6a23c',
}

function initCharts() {
  workOrderChart = echarts.init(workOrderEl.value!)
  processChart = echarts.init(processEl.value!)
  defectChart = echarts.init(defectEl.value!)
  equipmentChart = echarts.init(equipmentEl.value!)
}

function renderWorkOrders() {
  const rows = workOrders.value
  workOrderChart?.setOption({
    grid: { left: 10, right: 50, top: 10, bottom: 10, containLabel: true },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: { color: AXIS, formatter: '{value}%' },
      splitLine: { lineStyle: { color: SPLIT } },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map((r) => `${r.workOrderNo} ${r.productNameSnapshot ?? ''}`),
      axisLabel: { color: AXIS, fontSize: 11 },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [
      {
        type: 'bar',
        data: rows.map((r) => r.progressPercent),
        barWidth: 14,
        itemStyle: { color: '#00d4ff', borderRadius: [0, 7, 7, 0] },
        label: { show: true, position: 'right', color: '#d8e6f3', formatter: '{c}%' },
      },
    ],
    ...(rows.length
      ? {}
      : {
          title: { text: '暂无进行中工单', left: 'center', top: 'middle', textStyle: { color: '#5a7a99', fontSize: 13 } },
          xAxis: { show: false },
          yAxis: { show: false },
        }),
  })
}

function renderProcessYields() {
  const rows = quality.value?.processYields ?? []
  processChart?.setOption({
    grid: { left: 10, right: 10, top: 30, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.processName),
      axisLabel: { color: AXIS, fontSize: 10, rotate: 25 },
      axisLine: { lineStyle: { color: SPLIT } },
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: { color: AXIS, formatter: '{value}%' },
      splitLine: { lineStyle: { color: SPLIT } },
    },
    series: [
      {
        type: 'bar',
        data: rows.map((r) => r.yieldRate ?? 0),
        barWidth: 18,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#00d4ff' },
            { offset: 1, color: 'rgba(0,212,255,0.15)' },
          ]),
        },
        label: {
          show: true,
          position: 'top',
          color: '#d8e6f3',
          formatter: (p: { dataIndex: number }) => {
            const rate = quality.value?.processYields[p.dataIndex]?.yieldRate
            return rate == null ? '-' : `${rate}%`
          },
        },
      },
    ],
    ...(rows.length
      ? {}
      : {
          title: { text: '暂无质检数据', left: 'center', top: 'middle', textStyle: { color: '#5a7a99', fontSize: 13 } },
          xAxis: { show: false },
          yAxis: { show: false },
        }),
  })
}

function renderDefects() {
  const rows = quality.value?.defectDistribution ?? []
  defectChart?.setOption({
    color: ['#f56c6c', '#e6a23c', '#9f7ef5', '#00d4ff', '#67c23a', '#ff7a45', '#5a7a99'],
    tooltip: { trigger: 'item', formatter: '{b}: {c}（{d}%）' },
    legend: { bottom: 0, textStyle: { color: AXIS }, itemWidth: 10, itemHeight: 10 },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '45%'],
        data: rows.map((r) => ({
          name: DEFECT_CODES[r.defectCode] || r.defectCode,
          value: Number(r.count),
        })),
        label: { color: '#d8e6f3' },
        itemStyle: { borderColor: '#0a1628', borderWidth: 2 },
      },
    ],
    ...(rows.length
      ? {}
      : {
          title: { text: '暂无不良数据', left: 'center', top: 'middle', textStyle: { color: '#5a7a99', fontSize: 13 } },
          legend: { show: false },
        }),
  })
}

function renderEquipment() {
  const rows = equipment.value?.statusCounts ?? []
  equipmentChart?.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}（{d}%）' },
    legend: { bottom: 0, textStyle: { color: AXIS }, itemWidth: 10, itemHeight: 10 },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '45%'],
        data: rows.map((r) => ({
          name: EQUIPMENT_STATUS[r.status] || r.status,
          value: Number(r.count),
          itemStyle: { color: EQUIPMENT_COLORS[r.status] || '#5a7a99' },
        })),
        label: { color: '#d8e6f3' },
        itemStyle: { borderColor: '#0a1628', borderWidth: 2 },
      },
    ],
  })
}

function renderAll() {
  renderWorkOrders()
  renderProcessYields()
  renderDefects()
  renderEquipment()
}

// ---------- 加载与轮询 ----------
let timer: ReturnType<typeof setInterval> | null = null

async function loadAll() {
  const [s, wo, q, eq] = await Promise.all([
    dashboardApi.summary().catch(() => null),
    dashboardApi.workOrders().catch(() => []),
    dashboardApi.quality().catch(() => null),
    dashboardApi.equipment().catch(() => null),
  ])
  summary.value = s
  workOrders.value = wo ?? []
  quality.value = q
  equipment.value = eq
  renderAll()
}

function handleResize() {
  workOrderChart?.resize()
  processChart?.resize()
  defectChart?.resize()
  equipmentChart?.resize()
}

onMounted(() => {
  initCharts()
  loadAll()
  timer = setInterval(loadAll, 10000)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('resize', handleResize)
  workOrderChart?.dispose()
  processChart?.dispose()
  defectChart?.dispose()
  equipmentChart?.dispose()
})
</script>

<style scoped>
.board {
  min-height: 100%;
  background: #0a1628;
  border-radius: 6px;
  padding: 16px;
  color: #d8e6f3;
}

.board-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(0, 212, 255, 0.25);
}

.board-title {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #00d4ff;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-top: 14px;
}

.kpi-card {
  background: rgba(0, 212, 255, 0.06);
  border: 1px solid rgba(0, 212, 255, 0.2);
  border-radius: 6px;
  padding: 14px 8px;
  text-align: center;
}

.kpi-value {
  font-size: 26px;
  font-weight: 700;
  font-family: 'Consolas', 'Courier New', monospace;
}

.kpi-label {
  margin-top: 6px;
  font-size: 12px;
  color: #7fa8c9;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-top: 12px;
}

.chart-panel {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  padding: 10px;
}

.panel-title {
  padding: 2px 6px 6px;
  font-size: 13px;
  color: #7fa8c9;
}

.chart {
  height: 260px;
}
</style>
