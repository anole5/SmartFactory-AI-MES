<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="工单号/外部单号/产品名"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="(label, code) in WORK_ORDER_STATUS" :key="code" :label="label" :value="code" />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-button v-permission="'production:work-order:create'" type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新建工单
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
      <el-table-column prop="productNameSnapshot" label="产品" min-width="170" show-overflow-tooltip />
      <el-table-column label="进度" min-width="150">
        <template #default="{ row }">
          <el-progress
            :percentage="percentOf(row)"
            :stroke-width="14"
            :format="() => `${row.completedQty}/${row.planQty}`"
          />
        </template>
      </el-table-column>
      <el-table-column label="合格/不良" width="90">
        <template #default="{ row }">
          <span class="good-qty">{{ row.goodQty }}</span>/<span class="defect-qty">{{ row.defectQty }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="95">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.status)">{{ labelOf(WORK_ORDER_STATUS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="80">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.priority)" effect="plain">{{ labelOf(PRIORITY, row.priority) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="planStartTime" label="计划开始" width="160" />
      <el-table-column label="操作" width="235" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            v-permission="'production:work-order:update'"
            link
            type="primary"
            @click="openDialog(row)"
          >编辑</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            v-permission="'production:work-order:release'"
            link
            type="success"
            @click="handleRelease(row)"
          >下发</el-button>
          <el-button
            v-if="['DRAFT', 'RELEASED', 'IN_PROGRESS'].includes(row.status)"
            v-permission="'production:work-order:cancel'"
            link
            type="danger"
            @click="handleCancel(row)"
          >取消</el-button>
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

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑工单（仅草稿可编辑）' : '新建工单'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="产品" prop="productId">
          <el-select
            v-model="form.productId"
            placeholder="选择产品（自动解析其生效 BOM/工艺路线）"
            filterable
            style="width: 100%"
            :disabled="!!editingId"
          >
            <el-option
              v-for="p in products"
              :key="p.id"
              :label="`${p.productCode} / ${p.productName}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划数量" prop="planQty">
          <el-input-number v-model="form.planQty" :min="1" :max="999999" style="width: 180px" />
        </el-form-item>
        <el-form-item label="外部单号">
          <el-input v-model="form.externalOrderNo" placeholder="ERP 订单号，选填（第 3 周接 ERP 集成）" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 180px">
            <el-option v-for="(label, code) in PRIORITY" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划时间">
          <el-date-picker
            v-model="planRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="计划开始"
            end-placeholder="计划结束"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉：基本信息 + 任务明细 + 追溯时间线 -->
    <el-drawer
      v-model="detailVisible"
      :title="detail ? `工单详情 ${detail.workOrderNo}` : '工单详情'"
      size="720px"
      destroy-on-close
    >
      <template v-if="detail">
        <div class="drawer-actions">
          <el-button link type="primary" @click="gotoReports">
            <el-icon><DataLine /></el-icon>&nbsp;查看本工单报工记录
          </el-button>
        </div>
        <el-descriptions :column="2" border size="small" class="detail-desc">
          <el-descriptions-item label="状态">
            <el-tag :type="tagTypeOf(detail.status)">{{ labelOf(WORK_ORDER_STATUS, detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="产品">{{ detail.productNameSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="计划数量">{{ detail.planQty }}</el-descriptions-item>
          <el-descriptions-item label="完成/合格/不良">
            {{ detail.completedQty }} / {{ detail.goodQty }} / {{ detail.defectQty }}
          </el-descriptions-item>
          <el-descriptions-item label="优先级">{{ labelOf(PRIORITY, detail.priority) }}</el-descriptions-item>
          <el-descriptions-item label="报工记录">{{ detail.reportCount ?? 0 }} 条</el-descriptions-item>
          <el-descriptions-item label="外部单号" :span="2">{{ detail.externalOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实际开工">{{ detail.actualStartTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实际完工">{{ detail.actualEndTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">工序任务（{{ detail.tasks?.length ?? 0 }}）</h4>
        <el-table :data="detail.tasks ?? []" stripe border size="small">
          <el-table-column prop="sequenceNo" label="序" width="50" />
          <el-table-column prop="processNameSnapshot" label="工序" min-width="130" show-overflow-tooltip />
          <el-table-column prop="workstationName" label="工位" min-width="100" show-overflow-tooltip />
          <el-table-column label="操作员" width="80">
            <template #default="{ row }">{{ row.operatorName || '-' }}</template>
          </el-table-column>
          <el-table-column label="完成/合格/不良" width="115">
            <template #default="{ row }">{{ row.completedQty }}/{{ row.goodQty }}/{{ row.defectQty }}</template>
          </el-table-column>
          <el-table-column label="状态" width="85">
            <template #default="{ row }">
              <el-tag :type="tagTypeOf(row.status)" size="small">{{ labelOf(TASK_STATUS, row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <h4 class="section-title">追溯时间线（{{ traces.length }}）</h4>
        <el-timeline class="trace-timeline">
          <el-timeline-item
            v-for="t in traces"
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
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { productApi, traceApi, workOrderApi } from '@/api'
import type { Product, TraceRecord, WorkOrder, WorkOrderQuery, WorkOrderSave } from '@/api/types'
import { ACTION_TYPE, PRIORITY, TASK_STATUS, WORK_ORDER_STATUS, labelOf, tagTypeOf } from '@/constants/dict'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const rows = ref<WorkOrder[]>([])
const total = ref('0')
const query = reactive<WorkOrderQuery>({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

async function load() {
  loading.value = true
  try {
    const page = await workOrderApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
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
  query.status = ''
  query.pageNum = 1
  load()
}

function percentOf(row: WorkOrder): number {
  return row.planQty > 0 ? Math.round((row.completedQty / row.planQty) * 100) : 0
}

// 产品下拉（新建工单用；后端创建时会校验产品启用 + 自动解析生效 BOM/路线）
const products = ref<Product[]>([])

async function loadProducts() {
  const page = await productApi.page({ pageNum: 1, pageSize: 100, status: 'ENABLED' })
  products.value = page.records
}

// ---------- 新建/编辑 ----------
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<WorkOrderSave>({
  productId: '',
  planQty: 1,
  externalOrderNo: '',
  priority: 'NORMAL',
  remark: '',
})
const planRange = ref<[string, string] | null>(null)

const formRules: FormRules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  planQty: [{ required: true, message: '请输入计划数量', trigger: 'blur' }],
}

function openDialog(row?: WorkOrder) {
  editingId.value = row?.id ?? null
  if (row) {
    Object.assign(form, {
      productId: row.productId,
      planQty: row.planQty,
      externalOrderNo: row.externalOrderNo ?? '',
      priority: row.priority,
      remark: row.remark ?? '',
    })
    planRange.value = row.planStartTime && row.planEndTime ? [row.planStartTime, row.planEndTime] : null
  } else {
    Object.assign(form, { productId: '', planQty: 1, externalOrderNo: '', priority: 'NORMAL', remark: '' })
    planRange.value = null
  }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload: WorkOrderSave = {
      ...form,
      externalOrderNo: form.externalOrderNo || undefined,
      remark: form.remark || undefined,
      planStartTime: planRange.value?.[0],
      planEndTime: planRange.value?.[1],
    }
    if (editingId.value) {
      await workOrderApi.update(editingId.value, payload)
      ElMessage.success('工单修改成功')
    } else {
      await workOrderApi.create(payload)
      ElMessage.success('工单创建成功（草稿状态，下发后生成工序任务）')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 下发 / 取消 ----------
async function handleRelease(row: WorkOrder) {
  await ElMessageBox.confirm(
    `确定下发工单「${row.workOrderNo}」吗？将按其工艺路线生成工序任务。`,
    '提示',
    { type: 'warning' },
  )
  await workOrderApi.release(row.id)
  ElMessage.success('下发成功，已生成工序任务')
  load()
}

async function handleCancel(row: WorkOrder) {
  await ElMessageBox.confirm(
    `确定取消工单「${row.workOrderNo}」吗？未完成的工序任务将一并取消。`,
    '警告',
    { type: 'warning' },
  )
  await workOrderApi.cancel(row.id)
  ElMessage.success('取消成功')
  load()
}

// ---------- 详情抽屉 ----------
const detailVisible = ref(false)
const detail = ref<WorkOrder | null>(null)
const traces = ref<TraceRecord[]>([])

async function openDetail(row: WorkOrder) {
  detailVisible.value = true
  detail.value = await workOrderApi.detail(row.id)
  traces.value = await traceApi.listByWorkOrder(row.id)
}

/** 跳转报工记录页并携带工单 ID 过滤 */
function gotoReports() {
  if (!detail.value) return
  router.push({ path: '/reports', query: { workOrderId: detail.value.id } })
}

/** 追溯明细 JSON 转可读文本（如 {"taskCount":13} -> taskCount=13） */
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

onMounted(() => {
  load()
  loadProducts()
})
</script>

<style scoped>
.drawer-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.good-qty {
  color: #67c23a;
  font-weight: 600;
}

.defect-qty {
  color: #f56c6c;
  font-weight: 600;
}

.section-title {
  margin: 18px 0 10px;
}

.trace-timeline {
  padding-left: 4px;
}

.trace-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.trace-task {
  color: #909399;
  font-size: 12px;
}

.trace-detail {
  width: 100%;
  color: #606266;
  font-size: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 4px 8px;
}
</style>
