<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="(label, code) in TASK_STATUS" :key="code" :label="label" :value="code" />
      </el-select>
      <el-select
        v-model="query.workstationId"
        placeholder="工位"
        clearable
        filterable
        style="width: 200px"
        @change="handleSearch"
      >
        <el-option
          v-for="w in workstations"
          :key="w.id"
          :label="`${w.workstationCode} / ${w.workstationName}`"
          :value="w.id"
        />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-tag type="info" effect="plain">任务由工单下发自动生成，请到「生产工单」页操作下发</el-tag>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
      <el-table-column label="工序" min-width="150">
        <template #default="{ row }">
          <span class="seq-tag">#{{ row.sequenceNo }}</span>{{ row.processNameSnapshot }}
        </template>
      </el-table-column>
      <el-table-column prop="workstationName" label="工位" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作员" width="90">
        <template #default="{ row }">{{ row.operatorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="完成/合格/不良" width="120">
        <template #default="{ row }">{{ row.completedQty }}/{{ row.goodQty }}/{{ row.defectQty }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.status)">{{ labelOf(TASK_STATUS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质检" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.needInspection" type="warning" size="small">需质检</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="255" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING'"
            v-permission="'production:task:assign'"
            link
            type="primary"
            @click="openAssign(row)"
          >派工</el-button>
          <el-button
            v-if="row.status === 'ASSIGNED'"
            v-permission="'production:task:start'"
            link
            type="success"
            @click="handleAction('start', row)"
          >开工</el-button>
          <el-button
            v-if="row.status === 'RUNNING'"
            v-permission="'production:task:pause'"
            link
            type="warning"
            @click="handleAction('pause', row)"
          >暂停</el-button>
          <el-button
            v-if="row.status === 'PAUSED'"
            v-permission="'production:task:resume'"
            link
            type="success"
            @click="handleAction('resume', row)"
          >继续</el-button>
          <el-button
            v-if="row.status === 'RUNNING'"
            v-permission="'production:report:create'"
            link
            type="primary"
            @click="openReport(row)"
          >报工</el-button>
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

    <!-- 派工弹窗 -->
    <el-dialog v-model="assignVisible" title="派工" width="480px" destroy-on-close>
      <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-width="90px">
        <el-form-item label="任务">
          <span>{{ currentTask?.taskNo }}（{{ currentTask?.processNameSnapshot }}）</span>
        </el-form-item>
        <el-form-item label="操作员" prop="operatorId">
          <el-select v-model="assignForm.operatorId" placeholder="选择操作员" filterable style="width: 100%">
            <el-option
              v-for="u in users"
              :key="u.id"
              :label="`${u.realName || u.username}（${u.username}）`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工位覆盖">
          <el-select
            v-model="assignForm.workstationId"
            placeholder="默认沿用路线工位，可不选"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="w in workstations"
              :key="w.id"
              :label="`${w.workstationCode} / ${w.workstationName}`"
              :value="w.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleAssign">确定派工</el-button>
      </template>
    </el-dialog>

    <!-- 报工弹窗 -->
    <el-dialog v-model="reportVisible" title="报工" width="480px" destroy-on-close>
      <el-form ref="reportFormRef" :model="reportForm" :rules="reportRules" label-width="100px">
        <el-form-item label="任务">
          <span>
            {{ currentTask?.taskNo }}（{{ currentTask?.processNameSnapshot }}）
            剩余 {{ (currentTask?.planQty ?? 0) - (currentTask?.completedQty ?? 0) }}
          </span>
        </el-form-item>
        <el-form-item label="报工数量" prop="reportQty">
          <el-input-number v-model="reportForm.reportQty" :min="1" style="width: 180px" />
        </el-form-item>
        <el-form-item label="合格数量" prop="goodQty">
          <el-input-number v-model="reportForm.goodQty" :min="0" style="width: 180px" />
        </el-form-item>
        <el-form-item label="不良数量" prop="defectQty">
          <el-input-number v-model="reportForm.defectQty" :min="0" style="width: 180px" />
        </el-form-item>
        <el-form-item label="生产批次号">
          <el-input v-model="reportForm.productBatchNo" placeholder="选填，如 BATCH-20260823" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reportForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleReport">确定报工</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi, reportApi, taskApi, workstationApi } from '@/api'
import type { OperationTask, TaskAssign, TaskQuery, UserOption, WorkReportSave, Workstation } from '@/api/types'
import { TASK_STATUS, labelOf, tagTypeOf } from '@/constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref<OperationTask[]>([])
const total = ref('0')
const query = reactive<TaskQuery>({ pageNum: 1, pageSize: 10, status: '', workstationId: '' })

const workstations = ref<Workstation[]>([])
const users = ref<UserOption[]>([])

async function load() {
  loading.value = true
  try {
    const page = await taskApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      workstationId: query.workstationId || undefined,
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
  query.status = ''
  query.workstationId = ''
  query.pageNum = 1
  load()
}

// ---------- 开工/暂停/继续 ----------
const ACTION_TEXT: Record<string, string> = { start: '开工', pause: '暂停', resume: '继续' }

async function handleAction(action: 'start' | 'pause' | 'resume', row: OperationTask) {
  saving.value = true
  try {
    if (action === 'start') await taskApi.start(row.id)
    else if (action === 'pause') await taskApi.pause(row.id)
    else await taskApi.resume(row.id)
    ElMessage.success(`${ACTION_TEXT[action]}成功`)
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 派工 ----------
const assignVisible = ref(false)
const assignFormRef = ref<FormInstance>()
const currentTask = ref<OperationTask | null>(null)
const assignForm = reactive<TaskAssign>({ operatorId: '', workstationId: '' })

const assignRules: FormRules = {
  operatorId: [{ required: true, message: '请选择操作员', trigger: 'change' }],
}

function openAssign(row: OperationTask) {
  currentTask.value = row
  Object.assign(assignForm, { operatorId: '', workstationId: '' })
  assignVisible.value = true
}

async function handleAssign() {
  const valid = await assignFormRef.value?.validate().catch(() => false)
  if (!valid || !currentTask.value) return
  saving.value = true
  try {
    await taskApi.assign(currentTask.value.id, {
      operatorId: assignForm.operatorId,
      workstationId: assignForm.workstationId || undefined,
    })
    ElMessage.success('派工成功')
    assignVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 报工 ----------
const reportVisible = ref(false)
const reportFormRef = ref<FormInstance>()
const reportForm = reactive<WorkReportSave>({
  taskId: '',
  reportQty: 1,
  goodQty: 1,
  defectQty: 0,
  productBatchNo: '',
  remark: '',
})

const reportRules: FormRules = {
  reportQty: [
    { required: true, message: '请输入报工数量', trigger: 'blur' },
    {
      validator: (_rule, _value, callback) => {
        if (reportForm.goodQty + reportForm.defectQty !== reportForm.reportQty) {
          callback(new Error('报工数量必须等于合格数量加不良数量'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  goodQty: [{ required: true, message: '请输入合格数量', trigger: 'blur' }],
  defectQty: [{ required: true, message: '请输入不良数量', trigger: 'blur' }],
}

function openReport(row: OperationTask) {
  currentTask.value = row
  Object.assign(reportForm, {
    taskId: row.id,
    reportQty: 1,
    goodQty: 1,
    defectQty: 0,
    productBatchNo: '',
    remark: '',
  })
  reportVisible.value = true
}

async function handleReport() {
  const valid = await reportFormRef.value?.validate().catch(() => false)
  if (!valid || !currentTask.value) return
  if (reportForm.goodQty + reportForm.defectQty !== reportForm.reportQty) {
    ElMessage.warning('报工数量必须等于合格数量加不良数量')
    return
  }
  saving.value = true
  try {
    await reportApi.create({
      ...reportForm,
      productBatchNo: reportForm.productBatchNo || undefined,
      remark: reportForm.remark || undefined,
    })
    ElMessage.success('报工成功')
    reportVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load()
  const [wsPage, userList] = await Promise.all([
    workstationApi.page({ pageNum: 1, pageSize: 100, status: 'ENABLED' }),
    authApi.users(),
  ])
  workstations.value = wsPage.records
  users.value = userList
})
</script>

<style scoped>
.seq-tag {
  display: inline-block;
  min-width: 30px;
  margin-right: 6px;
  color: #909399;
  font-size: 12px;
}
</style>
