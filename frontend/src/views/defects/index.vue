<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-select v-model="query.defectCode" placeholder="不良类型" clearable style="width: 160px" @change="handleSearch">
        <el-option v-for="(label, code) in DEFECT_CODES" :key="code" :label="label" :value="code" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="不良单号 / 工单号"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-tag type="info" effect="plain">不良记录由检验录入自动生成，可转为异常单</el-tag>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="defectNo" label="不良单号" min-width="150" />
      <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
      <el-table-column prop="processNameSnapshot" label="工序" min-width="110" />
      <el-table-column label="不良类型" width="150">
        <template #default="{ row }">
          <el-tag type="warning">{{ labelOf(DEFECT_CODES, row.defectCode) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="defectQty" label="数量" width="80" />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'quality:defect:to-exception'"
            link
            type="danger"
            @click="handleToException(row)"
          >生成异常单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
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
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { defectApi } from '@/api'
import type { DefectQuery, DefectRecord } from '@/api/types'
import { DEFECT_CODES, labelOf } from '@/constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref<DefectRecord[]>([])
const total = ref('0')
const query = reactive<DefectQuery>({ pageNum: 1, pageSize: 10, defectCode: '', keyword: '' })

async function load() {
  loading.value = true
  try {
    const page = await defectApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      defectCode: query.defectCode || undefined,
      keyword: query.keyword || undefined,
    })
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  load()
}

function handleReset() {
  query.defectCode = ''
  query.keyword = ''
  query.pageNum = 1
  load()
}

// ---------- 生成异常单 ----------
async function handleToException(row: DefectRecord) {
  const ok = await ElMessageBox.confirm(
    `将基于不良单 ${row.defectNo} 生成异常单，是否继续？`,
    '提示',
    { type: 'warning' },
  ).catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    const exceptionId = await defectApi.toException(row.id)
    ElMessage.success(`异常单已生成（ID: ${exceptionId}），请到异常管理处理`)
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
