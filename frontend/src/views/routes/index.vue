<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="路线编号"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-model="query.productId"
        placeholder="产品"
        clearable
        filterable
        style="width: 240px"
        @change="handleSearch"
      >
        <el-option
          v-for="p in products"
          :key="p.id"
          :label="`${p.productCode} / ${p.productName}`"
          :value="p.id"
        />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="(label, code) in DRAFT_STATUS" :key="code" :label="label" :value="code" />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="openEdit()">
        <el-icon><Plus /></el-icon>&nbsp;新建工艺路线
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="routeNo" label="路线编号" min-width="190" />
      <el-table-column label="产品" min-width="200">
        <template #default="{ row }">{{ row.productCode }} / {{ row.productName }}</template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.status)">{{ labelOf(DRAFT_STATUS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openView(row)">详情</el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleStatus(row, 'ACTIVE', '激活')">
            激活
          </el-button>
          <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="handleStatus(row, 'OBSOLETE', '作废')">
            作废
          </el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 编辑抽屉（新建/编辑共用） -->
    <RouteDrawer
      v-model="editVisible"
      :route-id="editId"
      :products="enabledProducts"
      :processes="processes"
      :workstations="workstations"
      @saved="load"
    />

    <!-- 详情抽屉（只读） -->
    <el-drawer v-model="viewVisible" title="工艺路线详情" size="820px">
      <template v-if="viewRoute">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="路线编号">{{ viewRoute.routeNo }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ viewRoute.version }}</el-descriptions-item>
          <el-descriptions-item label="产品">{{ viewRoute.productCode }} / {{ viewRoute.productName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="tagTypeOf(viewRoute.status)">{{ labelOf(DRAFT_STATUS, viewRoute.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ viewRoute.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ viewRoute.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4>工艺步骤（{{ viewRoute.steps?.length ?? 0 }} 步）</h4>
        <el-table :data="viewRoute.steps ?? []" stripe border size="small">
          <el-table-column prop="sequenceNo" label="序号" width="60" />
          <el-table-column prop="processCodeSnapshot" label="工序编码" min-width="130" />
          <el-table-column prop="processNameSnapshot" label="工序名称" min-width="140" />
          <el-table-column label="默认工位" min-width="150">
            <template #default="{ row }">
              {{ row.workstationCode ? `${row.workstationCode} / ${row.workstationName}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="质检" width="70">
            <template #default="{ row }">
              <el-tag :type="row.needInspection ? 'warning' : 'info'" size="small">
                {{ row.needInspection ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="standardMinutes" label="标准工时(分)" width="100" />
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RouteDrawer from './RouteDrawer.vue'
import { processApi, productApi, routeApi, workstationApi } from '@/api'
import type { Process, Product, Route, RouteQuery, Workstation } from '@/api/types'
import { DRAFT_STATUS, labelOf, tagTypeOf } from '@/constants/dict'

const loading = ref(false)
const rows = ref<Route[]>([])
const total = ref('0')

const query = reactive<RouteQuery>({ pageNum: 1, pageSize: 10, keyword: '', productId: '', status: '' })

// 下拉选项
const products = ref<Product[]>([])
const enabledProducts = ref<Product[]>([])
const processes = ref<Process[]>([])
const workstations = ref<Workstation[]>([])

async function loadOptions() {
  const [allProducts, enabled, processPage, workstationPage] = await Promise.all([
    productApi.page({ pageNum: 1, pageSize: 100 }),
    productApi.page({ pageNum: 1, pageSize: 100, status: 'ENABLED' }),
    processApi.page({ pageNum: 1, pageSize: 100 }),
    workstationApi.page({ pageNum: 1, pageSize: 100, status: 'ENABLED' }),
  ])
  products.value = allProducts.records
  enabledProducts.value = enabled.records
  processes.value = processPage.records
  workstations.value = workstationPage.records
}

async function load() {
  loading.value = true
  try {
    const page = await routeApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      productId: query.productId || undefined,
      status: query.status || undefined,
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
  query.keyword = ''
  query.productId = ''
  query.status = ''
  query.pageNum = 1
  load()
}

// ---------- 新建/编辑抽屉 ----------
const editVisible = ref(false)
const editId = ref<string | null>(null)

function openEdit(row?: Route) {
  editId.value = row?.id ?? null
  editVisible.value = true
}

// ---------- 详情抽屉 ----------
const viewVisible = ref(false)
const viewRoute = ref<Route | null>(null)

async function openView(row: Route) {
  viewVisible.value = true
  viewRoute.value = await routeApi.detail(row.id)
}

// ---------- 状态流转 / 删除 ----------
async function handleStatus(row: Route, target: string, action: string) {
  await ElMessageBox.confirm(`确定${action}工艺路线「${row.routeNo}」吗？`, '提示', { type: 'warning' })
  await routeApi.changeStatus(row.id, target)
  ElMessage.success(`${action}成功`)
  load()
}

async function handleDelete(row: Route) {
  await ElMessageBox.confirm(`确定删除草稿工艺路线「${row.routeNo}」吗？步骤将一并删除。`, '警告', {
    type: 'warning',
  })
  await routeApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => {
  load()
  loadOptions()
})
</script>
