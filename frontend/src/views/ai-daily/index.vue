<template>
  <div class="daily-page">
    <!-- 左：生成 + 编辑 -->
    <div class="left-panel">
      <div class="toolbar">
        <el-date-picker
          v-model="reportDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="报告日期"
          style="width: 160px"
        />
        <el-button v-permission="'ai:daily:generate'" type="primary" :loading="generating" @click="handlePreview">
          <el-icon><MagicStick /></el-icon>&nbsp;生成日报
        </el-button>
        <el-button v-permission="'ai:daily:save'" type="success" :loading="saving" :disabled="!content.trim()" @click="handleSave">
          <el-icon><CircleCheck /></el-icon>&nbsp;保存日报
        </el-button>
      </div>

      <el-alert
        v-if="fallback"
        title="AI 服务暂不可用，已返回模板日报（统计数据直出）"
        type="warning"
        :closable="false"
        style="margin-bottom: 10px"
      />

      <el-input
        v-model="content"
        type="textarea"
        :rows="14"
        placeholder="选择日期后点击「生成日报」，AI 会聚合当日产量/良率/工单/异常/设备数据并润色成文，可编辑后保存"
      />

      <el-collapse v-if="summary" class="summary-box">
        <el-collapse-item title="统计数据来源">
          <pre class="summary-text">{{ summary }}</pre>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 右：历史日报 -->
    <div class="right-panel">
      <div class="panel-title">已保存日报</div>
      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        height="calc(100vh - 240px)"
        highlight-current-row
        @row-click="handlePick"
      >
        <el-table-column prop="reportDate" label="日期" width="110" />
        <el-table-column prop="content" label="内容" show-overflow-tooltip />
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="Number(total)"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dailyApi } from '@/api'
import type { DailyReport, DailyReportQuery } from '@/api/types'

const generating = ref(false)
const saving = ref(false)
const reportDate = ref(new Date().toISOString().slice(0, 10))
const content = ref('')
const summary = ref('')
const fallback = ref(false)

const loading = ref(false)
const rows = ref<DailyReport[]>([])
const total = ref('0')
const query = reactive<DailyReportQuery>({ pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const page = await dailyApi.page({ pageNum: query.pageNum, pageSize: query.pageSize })
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function handlePreview() {
  if (!reportDate.value || generating.value) return
  generating.value = true
  try {
    const res = await dailyApi.preview(reportDate.value)
    content.value = res.content
    summary.value = res.summary ?? ''
    fallback.value = res.fallback ?? false
  } catch {
    ElMessage.error('日报生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

async function handleSave() {
  if (!reportDate.value || saving.value) return
  saving.value = true
  try {
    await dailyApi.save({ reportDate: reportDate.value, content: content.value.trim() })
    ElMessage.success('日报已保存（同日重复保存自动覆盖）')
    await load()
  } catch {
    ElMessage.error('保存失败（请确认有保存权限）')
  } finally {
    saving.value = false
  }
}

function handlePick(row: DailyReport) {
  reportDate.value = row.reportDate
  content.value = row.content
  summary.value = ''
  fallback.value = false
}

onMounted(load)
</script>

<style scoped>
.daily-page {
  display: flex;
  gap: 12px;
}

.left-panel {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #ebeef5;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.summary-box {
  margin-top: 10px;
}

.summary-text {
  margin: 0;
  font-size: 12px;
  color: #606266;
  white-space: pre-wrap;
  font-family: inherit;
}

.right-panel {
  width: 420px;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #ebeef5;
}

.panel-title {
  font-weight: 600;
  margin-bottom: 10px;
}
</style>
