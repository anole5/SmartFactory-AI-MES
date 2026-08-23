<template>
  <div class="page-card">
    <!-- 入口切换 -->
    <div class="toolbar">
      <el-radio-group v-model="mode" @change="handleModeChange">
        <el-radio-button value="sn">按 SN 追溯</el-radio-button>
        <el-radio-button value="batch">按批次追溯</el-radio-button>
        <el-radio-button value="materialBatch">按物料批次反查</el-radio-button>
        <el-radio-button value="workOrder">按工单追溯</el-radio-button>
      </el-radio-group>
      <div class="spacer" />
      <el-tag type="info" effect="plain">整机 SN 由最后一道工序报工完成时自动生成</el-tag>
    </div>

    <!-- 按 SN -->
    <template v-if="mode === 'sn'">
      <div class="entry-bar">
        <el-input
          v-model="snInput"
          placeholder="输入整机 SN，如 SN202608230001"
          clearable
          style="width: 260px"
          @keyup.enter="handleSnTrace"
        />
        <el-button type="primary" @click="handleSnTrace">
          <el-icon><Search /></el-icon>&nbsp;追溯
        </el-button>
      </div>

      <el-card v-if="snResult" class="result-card" shadow="never">
        <div class="card-actions">
          <el-button link type="primary" @click="openTimeline(`SN ${snResult.sn}`, snResult.timeline)">
            <el-icon><Clock /></el-icon>&nbsp;查看工单时间线（{{ snResult.timeline.length }}）
          </el-button>
        </div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="整机 SN">{{ snResult.sn }}</el-descriptions-item>
          <el-descriptions-item label="工单号">{{ snResult.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="工单状态">
            <el-tag :type="tagTypeOf(snResult.workOrderStatus)" size="small">
              {{ labelOf(WORK_ORDER_STATUS, snResult.workOrderStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="产品">
            {{ snResult.productCodeSnapshot }} {{ snResult.productNameSnapshot }}
          </el-descriptions-item>
          <el-descriptions-item label="出生报工单">{{ snResult.reportNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="SN 生成时间">{{ snResult.createdAt }}</el-descriptions-item>
        </el-descriptions>
        <template v-if="snResult.materialBatches?.length">
          <h4 class="section-title">整机关键件批次（{{ snResult.materialBatches.length }}）</h4>
          <el-table :data="snResult.materialBatches" stripe border size="small">
            <el-table-column label="物料" min-width="180">
              <template #default="{ row }">{{ row.materialCodeSnapshot }} {{ row.materialNameSnapshot }}</template>
            </el-table-column>
            <el-table-column prop="batchNo" label="物料批次号" min-width="165" />
            <el-table-column prop="qtyUsed" label="使用数量" width="90" />
            <el-table-column prop="reportNo" label="绑定报工单" min-width="150" />
          </el-table>
        </template>
      </el-card>

      <div class="entry-bar">
        <el-input
          v-model="snListQuery.keyword"
          placeholder="SN 关键字过滤"
          clearable
          style="width: 200px"
          @keyup.enter="loadSnList"
          @clear="loadSnList"
        />
        <el-button @click="loadSnList">刷新 SN 列表</el-button>
      </div>
      <el-table v-loading="snLoading" :data="snRows" stripe border>
        <el-table-column prop="sn" label="整机 SN" min-width="180" />
        <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
        <el-table-column label="产品" min-width="200">
          <template #default="{ row }">{{ row.productCodeSnapshot }} {{ row.productNameSnapshot }}</template>
        </el-table-column>
        <el-table-column prop="reportNo" label="出生报工单" min-width="150" />
        <el-table-column prop="createdAt" label="生成时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="traceSnRow(row)">追溯</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="snListQuery.pageNum"
          v-model:page-size="snListQuery.pageSize"
          :total="Number(snTotal)"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="loadSnList"
        />
      </div>
    </template>

    <!-- 按批次 -->
    <template v-else-if="mode === 'batch'">
      <div class="entry-bar">
        <el-input
          v-model="batchInput"
          placeholder="输入生产批次号，如 BATCH-20260823"
          clearable
          style="width: 260px"
          @keyup.enter="handleBatchTrace"
        />
        <el-button type="primary" @click="handleBatchTrace">
          <el-icon><Search /></el-icon>&nbsp;追溯
        </el-button>
      </div>

      <template v-if="batchResult">
        <h4 class="section-title">批次报工记录（{{ batchResult.reports.length }}）</h4>
        <el-table :data="batchResult.reports" stripe border>
          <el-table-column prop="reportNo" label="报工单号" min-width="150" />
          <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
          <el-table-column prop="taskNo" label="工序任务" min-width="150" />
          <el-table-column prop="processNameSnapshot" label="工序" min-width="110" />
          <el-table-column label="操作员" width="90">
            <template #default="{ row }">{{ row.operatorName || '-' }}</template>
          </el-table-column>
          <el-table-column label="合格/不良" width="90">
            <template #default="{ row }">{{ row.goodQty }}/{{ row.defectQty }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="报工时间" width="170" />
        </el-table>

        <h4 class="section-title">涉及工单（{{ batchResult.workOrders.length }}）</h4>
        <el-table :data="batchResult.workOrders" stripe border>
          <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
          <el-table-column label="产品" min-width="200">
            <template #default="{ row }">{{ row.productCodeSnapshot }} {{ row.productNameSnapshot }}</template>
          </el-table-column>
          <el-table-column label="完成/计划" width="100">
            <template #default="{ row }">{{ row.completedQty }}/{{ row.planQty }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="tagTypeOf(row.status)" size="small">{{ labelOf(WORK_ORDER_STATUS, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="traceByWorkOrderId(row.id)">时间线</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </template>

    <!-- 按物料批次（第 6 周：关键件批次反查整机） -->
    <template v-else-if="mode === 'materialBatch'">
      <div class="entry-bar">
        <el-input
          v-model="materialBatchInput"
          placeholder="输入物料批次号，如 MB202608230001"
          clearable
          style="width: 260px"
          @keyup.enter="handleMaterialBatchTrace"
        />
        <el-button type="primary" @click="handleMaterialBatchTrace">
          <el-icon><Search /></el-icon>&nbsp;追溯
        </el-button>
        <div class="spacer" />
        <el-button v-permission="'production:material-batch:create'" type="success" @click="openBatchCreate">
          <el-icon><Plus /></el-icon>&nbsp;新建批次
        </el-button>
      </div>

      <template v-if="materialBatchResult">
        <el-card class="result-card" shadow="never">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="物料批次号">{{ materialBatchResult.batchNo }}</el-descriptions-item>
            <el-descriptions-item label="物料">
              {{ materialBatchResult.materialCodeSnapshot }} {{ materialBatchResult.materialNameSnapshot }}
            </el-descriptions-item>
            <el-descriptions-item label="批次/已用">
              {{ materialBatchResult.batchQty }}/{{ materialBatchResult.usedQty }}
            </el-descriptions-item>
            <el-descriptions-item label="入库日期">{{ materialBatchResult.inDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="供应商">{{ materialBatchResult.supplier || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <h4 class="section-title">绑定报工记录（{{ materialBatchResult.bindings.length }}）</h4>
        <el-table :data="materialBatchResult.bindings" stripe border>
          <el-table-column prop="reportNo" label="报工单号" min-width="150" />
          <el-table-column label="物料" min-width="200">
            <template #default="{ row }">{{ row.materialCodeSnapshot }} {{ row.materialNameSnapshot }}</template>
          </el-table-column>
          <el-table-column prop="qtyUsed" label="使用数量" width="90" />
          <el-table-column prop="createdAt" label="绑定时间" width="170" />
        </el-table>

        <h4 class="section-title">涉及工单（{{ materialBatchResult.workOrders.length }}）</h4>
        <el-table :data="materialBatchResult.workOrders" stripe border>
          <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
          <el-table-column label="产品" min-width="200">
            <template #default="{ row }">{{ row.productCodeSnapshot }} {{ row.productNameSnapshot }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="tagTypeOf(row.status)" size="small">{{ labelOf(WORK_ORDER_STATUS, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="traceByWorkOrderId(row.id)">时间线</el-button>
            </template>
          </el-table-column>
        </el-table>

        <h4 class="section-title">使用本批次的整机 SN（{{ materialBatchResult.sns.length }}）</h4>
        <el-table :data="materialBatchResult.sns" stripe border>
          <el-table-column prop="sn" label="整机 SN" min-width="180" />
          <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
          <el-table-column prop="productNameSnapshot" label="产品" min-width="160" />
          <el-table-column prop="createdAt" label="生成时间" width="170" />
        </el-table>
      </template>
    </template>

    <!-- 按工单 -->
    <template v-else>
      <div class="entry-bar">
        <el-input
          v-model="workOrderNoInput"
          placeholder="输入生产工单号，如 WO202608230001"
          clearable
          style="width: 260px"
          @keyup.enter="handleWorkOrderTrace"
        />
        <el-button type="primary" @click="handleWorkOrderTrace">
          <el-icon><Search /></el-icon>&nbsp;追溯
        </el-button>
      </div>

      <el-card v-if="workOrderResult" class="result-card" shadow="never">
        <div class="card-actions">
          <el-button link type="primary" @click="openTimeline(workOrderResult.workOrderNo, workOrderTraces)">
            <el-icon><Clock /></el-icon>&nbsp;查看时间线（{{ workOrderTraces.length }}）
          </el-button>
        </div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="工单号">{{ workOrderResult.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="产品">
            {{ workOrderResult.productCodeSnapshot }} {{ workOrderResult.productNameSnapshot }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="tagTypeOf(workOrderResult.status)" size="small">
              {{ labelOf(WORK_ORDER_STATUS, workOrderResult.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="完成/计划">
            {{ workOrderResult.completedQty }}/{{ workOrderResult.planQty }}
          </el-descriptions-item>
          <el-descriptions-item label="合格/不良">
            {{ workOrderResult.goodQty }}/{{ workOrderResult.defectQty }}
          </el-descriptions-item>
          <el-descriptions-item label="外部订单号">{{ workOrderResult.externalOrderNo || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </template>

    <!-- 新建物料批次弹窗（第 6 周） -->
    <el-dialog v-model="batchCreateVisible" title="新建物料批次" width="480px" destroy-on-close>
      <el-form ref="batchCreateFormRef" :model="batchCreateForm" :rules="batchCreateRules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select
            v-model="batchCreateForm.materialId"
            placeholder="选择物料（关键件可绑定追溯）"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="m in materials"
              :key="m.id"
              :label="`${m.materialCode} ${m.materialName}${m.traceRequired ? '（关键件）' : ''}`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="批次数量" prop="batchQty">
          <el-input-number v-model="batchCreateForm.batchQty" :min="1" style="width: 180px" />
        </el-form-item>
        <el-form-item label="入库日期">
          <el-date-picker
            v-model="batchCreateForm.inDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认今天"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="batchCreateForm.supplier" maxlength="64" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchCreateForm.remark" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="handleBatchCreate">确定创建</el-button>
      </template>
    </el-dialog>

    <!-- 时间线抽屉 -->
    <el-drawer v-model="timelineVisible" :title="timelineTitle" size="560px">
      <el-timeline class="trace-timeline">
        <el-timeline-item
          v-for="t in timelineRows"
          :key="t.id"
          :timestamp="`${t.actionTime}  ${t.operatorName ?? ''}`"
          placement="top"
        >
          <div class="trace-item">
            <el-tag size="small" effect="plain">{{ labelOf(ACTION_TYPE, t.actionType) }}</el-tag>
            <span v-if="t.taskId" class="trace-task">任务 #{{ t.taskId }}</span>
            <div v-if="t.actionDetail" class="trace-detail">{{ formatDetail(t.actionDetail) }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { materialApi, materialBatchApi, snApi, traceApi, workOrderApi } from '@/api'
import type {
  BatchSnTrace,
  BatchTrace,
  Material,
  MaterialBatchSave,
  Sn,
  SnQuery,
  SnTrace,
  TraceRecord,
  WorkOrder,
} from '@/api/types'
import { ACTION_TYPE, WORK_ORDER_STATUS, labelOf, tagTypeOf } from '@/constants/dict'

type TraceMode = 'sn' | 'batch' | 'materialBatch' | 'workOrder'
const mode = ref<TraceMode>('sn')

// ---------- 按 SN ----------
const snInput = ref('')
const snResult = ref<SnTrace | null>(null)

async function handleSnTrace() {
  const sn = snInput.value.trim()
  if (!sn) {
    ElMessage.warning('请输入 SN')
    return
  }
  const result = await traceApi.bySn(sn).catch(() => null)
  if (result) snResult.value = result
}

async function traceSnRow(row: Sn) {
  snInput.value = row.sn
  await handleSnTrace()
}

const snLoading = ref(false)
const snRows = ref<Sn[]>([])
const snTotal = ref('0')
const snListQuery = reactive<SnQuery>({ pageNum: 1, pageSize: 10, keyword: '' })

async function loadSnList() {
  snLoading.value = true
  try {
    const page = await snApi.page({
      pageNum: snListQuery.pageNum,
      pageSize: snListQuery.pageSize,
      keyword: snListQuery.keyword || undefined,
    })
    snRows.value = page.records
    snTotal.value = page.total
  } finally {
    snLoading.value = false
  }
}

// ---------- 按批次 ----------
const batchInput = ref('')
const batchResult = ref<BatchTrace | null>(null)

async function handleBatchTrace() {
  const batchNo = batchInput.value.trim()
  if (!batchNo) {
    ElMessage.warning('请输入生产批次号')
    return
  }
  const result = await traceApi.byBatch(batchNo).catch(() => null)
  if (!result) return
  if (result.reports.length === 0) {
    batchResult.value = null
    ElMessage.info(`批次 ${batchNo} 无报工记录`)
    return
  }
  batchResult.value = result
}

// ---------- 按物料批次（第 6 周：关键件批次反查整机） ----------
const materialBatchInput = ref('')
const materialBatchResult = ref<BatchSnTrace | null>(null)

async function handleMaterialBatchTrace() {
  const batchNo = materialBatchInput.value.trim()
  if (!batchNo) {
    ElMessage.warning('请输入物料批次号')
    return
  }
  const result = await traceApi.byMaterialBatch(batchNo).catch(() => null)
  if (result) materialBatchResult.value = result
}

// ---------- 新建物料批次 ----------
const batchCreateVisible = ref(false)
const batchSaving = ref(false)
const batchCreateFormRef = ref<FormInstance>()
const materials = ref<Material[]>([])
const batchCreateForm = reactive<MaterialBatchSave>({
  materialId: '',
  batchQty: 100,
  inDate: '',
  supplier: '',
  remark: '',
})

const batchCreateRules: FormRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  batchQty: [{ required: true, message: '请输入批次数量', trigger: 'blur' }],
}

async function openBatchCreate() {
  if (!materials.value.length) {
    const page = await materialApi
      .page({ pageNum: 1, pageSize: 100, status: 'ENABLED' })
      .catch(() => null)
    if (page) materials.value = page.records
  }
  Object.assign(batchCreateForm, { materialId: '', batchQty: 100, inDate: '', supplier: '', remark: '' })
  batchCreateVisible.value = true
}

async function handleBatchCreate() {
  const valid = await batchCreateFormRef.value?.validate().catch(() => false)
  if (!valid) return
  batchSaving.value = true
  try {
    await materialBatchApi.create({
      materialId: batchCreateForm.materialId,
      batchQty: batchCreateForm.batchQty,
      inDate: batchCreateForm.inDate || undefined,
      supplier: batchCreateForm.supplier || undefined,
      remark: batchCreateForm.remark || undefined,
    })
    ElMessage.success('批次创建成功')
    batchCreateVisible.value = false
  } finally {
    batchSaving.value = false
  }
}

// ---------- 按工单 ----------
const workOrderNoInput = ref('')
const workOrderResult = ref<WorkOrder | null>(null)
const workOrderTraces = ref<TraceRecord[]>([])

async function traceByWorkOrderId(workOrderId: string) {
  const [order, traces] = await Promise.all([
    workOrderApi.detail(workOrderId).catch(() => null),
    traceApi.listByWorkOrder(workOrderId).catch(() => []),
  ])
  if (order) workOrderResult.value = order
  if (traces.length) workOrderTraces.value = traces
}

async function handleWorkOrderTrace() {
  const workOrderNo = workOrderNoInput.value.trim()
  if (!workOrderNo) {
    ElMessage.warning('请输入工单号')
    return
  }
  const page = await workOrderApi.page({ pageNum: 1, pageSize: 1, keyword: workOrderNo }).catch(() => null)
  const order = page?.records?.[0]
  if (!order) {
    workOrderResult.value = null
    workOrderTraces.value = []
    ElMessage.warning(`未找到工单 ${workOrderNo}`)
    return
  }
  await traceByWorkOrderId(order.id)
}

// ---------- 时间线抽屉 ----------
const timelineVisible = ref(false)
const timelineTitle = ref('')
const timelineRows = ref<TraceRecord[]>([])

function openTimeline(title: string, rows: TraceRecord[]) {
  timelineTitle.value = `追溯时间线 - ${title}`
  timelineRows.value = rows
  timelineVisible.value = true
}

function formatDetail(raw: string): string {
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>
    return Object.entries(obj)
      .map(([k, v]) => `${k}=${v}`)
      .join('，')
  } catch {
    return raw
  }
}

function handleModeChange() {
  if (mode.value === 'sn') {
    loadSnList()
  }
}

onMounted(loadSnList)
</script>

<style scoped>
.entry-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.result-card {
  margin-bottom: 16px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.section-title {
  margin: 16px 0 8px;
  font-size: 14px;
  color: #303133;
}

.trace-task {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.trace-detail {
  margin-top: 4px;
  color: #606266;
  font-size: 12px;
}
</style>
