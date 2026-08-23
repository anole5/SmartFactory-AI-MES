<template>
  <div class="page-card">
    <!-- 工具栏：粒度 + 日期 + 导出 -->
    <div class="toolbar">
      <el-radio-group v-model="type" @change="handleTypeChange">
        <el-radio-button value="day">日报</el-radio-button>
        <el-radio-button value="week">周报</el-radio-button>
        <el-radio-button value="month">月报</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="基准日期"
        style="width: 150px"
        @change="load"
      />
      <div class="spacer" />
      <el-button v-permission="'production:report:export'" type="success" :loading="exporting" @click="handleExport">
        <el-icon><Download /></el-icon>&nbsp;导出 Excel
      </el-button>
    </div>

    <!-- 汇总卡片 -->
    <div v-if="summary" class="stat-row">
      <div class="stat-card">
        <div class="stat-value good">{{ summary.totalGoodQty }}</div>
        <div class="stat-label">合格数量</div>
      </div>
      <div class="stat-card">
        <div class="stat-value defect">{{ summary.totalDefectQty }}</div>
        <div class="stat-label">不良数量</div>
      </div>
      <div class="stat-card">
        <div class="stat-value yield">{{ summary.yieldRate }}%</div>
        <div class="stat-label">良率</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ summary.reportCount }}</div>
        <div class="stat-label">报工数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ summary.workOrderCount }}</div>
        <div class="stat-label">工单数</div>
      </div>
      <div class="stat-card window">
        <div class="stat-value window-text">{{ summary.rangeStart?.slice(0, 10) }} ~ {{ summary.rangeEnd?.slice(0, 10) }}</div>
        <div class="stat-label">统计窗口</div>
      </div>
    </div>

    <!-- 明细表 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="groupKey" :label="type === 'day' ? '工序' : '日期'" min-width="140" />
      <el-table-column v-if="type === 'day'" prop="processCode" label="工序编码" min-width="110" />
      <el-table-column prop="goodQty" label="合格数量" width="100" />
      <el-table-column prop="defectQty" label="不良数量" width="100" />
      <el-table-column prop="reportCount" label="报工数" width="90" />
      <el-table-column prop="workOrderCount" label="工单数" width="90" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { reportCenterApi } from '@/api'
import type { ReportRow, ReportSummary } from '@/api/types'

const TYPE_LABEL: Record<string, string> = { day: '日报', week: '周报', month: '月报' }

// ---------- 状态 ----------
const type = ref('day')
const date = ref(today())
const loading = ref(false)
const exporting = ref(false)
const summary = ref<ReportSummary | null>(null)

const rows = computed<ReportRow[]>(() => summary.value?.rows ?? [])

function today(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// ---------- 加载与导出 ----------
async function load() {
  loading.value = true
  try {
    const res = await reportCenterApi.summary(type.value, date.value).catch(() => null)
    summary.value = res
  } finally {
    loading.value = false
  }
}

function handleTypeChange() {
  load()
}

/** 导出：裸 axios 拿 Blob，从 Content-Disposition 解析 UTF-8 文件名后落盘 */
async function handleExport() {
  exporting.value = true
  try {
    const res = await reportCenterApi.exportExcel(type.value, date.value)
    if (!res) return
    const disposition = res.headers['content-disposition'] ?? ''
    const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    const filename = match ? decodeURIComponent(match[1]) : `生产报表_${TYPE_LABEL[type.value]}_${date.value}.xlsx`
    const url = URL.createObjectURL(res.blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 8px;
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  font-family: 'Consolas', 'Courier New', monospace;
  color: #303133;
}

.stat-value.good {
  color: #67c23a;
}

.stat-value.defect {
  color: #f56c6c;
}

.stat-value.yield {
  color: #409eff;
}

.stat-value.window-text {
  font-size: 13px;
  line-height: 34px;
}

.stat-label {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
