<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-input
        v-model="query.workOrderId"
        placeholder="工单 ID（选填）"
        clearable
        style="width: 160px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-model="query.operatorId"
        placeholder="报工人"
        clearable
        filterable
        style="width: 180px"
        @change="handleSearch"
      >
        <el-option
          v-for="u in users"
          :key="u.id"
          :label="`${u.realName || u.username}（${u.username}）`"
          :value="u.id"
        />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-tag type="info" effect="plain">报工记录只增不改，是生产追溯的审计依据</el-tag>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="reportNo" label="报工单号" min-width="165" />
      <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
      <el-table-column prop="taskNo" label="任务号" min-width="165" />
      <el-table-column prop="processNameSnapshot" label="工序" min-width="130" show-overflow-tooltip />
      <el-table-column prop="operatorName" label="报工人" width="100" />
      <el-table-column prop="reportQty" label="数量" width="70" />
      <el-table-column prop="goodQty" label="合格" width="70" />
      <el-table-column prop="defectQty" label="不良" width="70" />
      <el-table-column prop="productBatchNo" label="批次号" min-width="130" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="报工时间" width="160" />
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
import { useRoute } from 'vue-router'
import { authApi, reportApi } from '@/api'
import type { UserOption, WorkReport, WorkReportQuery } from '@/api/types'

const route = useRoute()

const loading = ref(false)
const rows = ref<WorkReport[]>([])
const total = ref('0')
const users = ref<UserOption[]>([])

// 支持从工单详情跳转携带 ?workOrderId=xx 直接过滤
const query = reactive<WorkReportQuery>({
  pageNum: 1,
  pageSize: 10,
  workOrderId: (route.query.workOrderId as string) || '',
  operatorId: '',
})

async function load() {
  loading.value = true
  try {
    const page = await reportApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      workOrderId: query.workOrderId || undefined,
      operatorId: query.operatorId || undefined,
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
  query.workOrderId = ''
  query.operatorId = ''
  query.pageNum = 1
  load()
}

onMounted(async () => {
  load()
  users.value = await authApi.users()
})
</script>
