<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="(label, code) in INSPECTION_TASK_STATUS" :key="code" :label="label" :value="code" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="任务单号 / 工单号"
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
      <el-tag type="info" effect="plain">质检任务由需质检工序报工完成时自动生成</el-tag>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="inspectionTaskNo" label="任务单号" min-width="150" />
      <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
      <el-table-column prop="processNameSnapshot" label="工序" min-width="110" />
      <el-table-column label="计划/已检" width="100">
        <template #default="{ row }">{{ row.inspectedQty }}/{{ row.planQty }}</template>
      </el-table-column>
      <el-table-column label="合格/不良" width="100">
        <template #default="{ row }">{{ row.goodQty }}/{{ row.defectQty }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.status)">{{ labelOf(INSPECTION_TASK_STATUS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质检员" width="100">
        <template #default="{ row }">{{ row.inspectorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING'"
            v-permission="'quality:inspection-task:start'"
            link
            type="primary"
            @click="handleStart(row)"
          >开始检验</el-button>
          <el-button
            v-if="row.status === 'INSPECTING'"
            v-permission="'quality:inspection-record:create'"
            link
            type="success"
            @click="openRecord(row)"
          >检验录入</el-button>
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

    <!-- 检验录入弹窗 -->
    <el-dialog v-model="recordVisible" title="检验录入" width="680px" destroy-on-close>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="任务单号">{{ currentTask?.inspectionTaskNo }}</el-descriptions-item>
        <el-descriptions-item label="工序">{{ currentTask?.processNameSnapshot }}</el-descriptions-item>
        <el-descriptions-item label="计划数">{{ currentTask?.planQty }}</el-descriptions-item>
        <el-descriptions-item label="已检数">{{ currentTask?.inspectedQty }}</el-descriptions-item>
        <el-descriptions-item label="待检数">{{ remaining }}</el-descriptions-item>
        <el-descriptions-item label="工单号">{{ currentTask?.workOrderNo }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="recordFormRef" :model="recordForm" :rules="recordRules" label-width="90px" class="record-form">
        <el-form-item label="合格数" prop="goodQty">
          <el-input-number v-model="recordForm.goodQty" :min="0" style="width: 180px" />
        </el-form-item>
        <el-form-item label="不良数" prop="defectQty">
          <el-input-number v-model="recordForm.defectQty" :min="0" style="width: 180px" />
        </el-form-item>
        <el-form-item label="不良明细">
          <div class="defect-box">
            <el-table :data="recordForm.defectItems" border size="small">
              <el-table-column label="不良类型" min-width="170">
                <template #default="{ row }">
                  <el-select v-model="row.defectCode" placeholder="选择不良类型" style="width: 100%">
                    <el-option v-for="(label, code) in DEFECT_CODES" :key="code" :label="label" :value="code" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.defectQty" :min="1" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="130">
                <template #default="{ row }">
                  <el-input v-model="row.remark" placeholder="选填" />
                </template>
              </el-table-column>
              <el-table-column width="60" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="recordForm.defectItems.splice($index, 1)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="defect-sum">
              <span>不良行数量合计：{{ defectSum }}（须等于不良数）</span>
              <el-button link type="primary" @click="addDefectRow">
                <el-icon><Plus /></el-icon>&nbsp;添加不良行
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="recordForm.remark" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleRecord">提交录入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { inspectionRecordApi, inspectionTaskApi } from '@/api'
import type { DefectItem, InspectionRecordSave, InspectionTask, InspectionTaskQuery } from '@/api/types'
import { DEFECT_CODES, INSPECTION_TASK_STATUS, labelOf, tagTypeOf } from '@/constants/dict'

/** 录入表单：不良行始终为数组，模板中直接操作 */
interface RecordForm extends InspectionRecordSave {
  defectItems: DefectItem[]
}

const loading = ref(false)
const saving = ref(false)
const rows = ref<InspectionTask[]>([])
const total = ref('0')
const query = reactive<InspectionTaskQuery>({ pageNum: 1, pageSize: 10, status: '', keyword: '' })

async function load() {
  loading.value = true
  try {
    const page = await inspectionTaskApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
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
  query.status = ''
  query.keyword = ''
  query.pageNum = 1
  load()
}

// ---------- 开始检验 ----------
async function handleStart(row: InspectionTask) {
  saving.value = true
  try {
    await inspectionTaskApi.start(row.id)
    ElMessage.success(`已开始检验，质检员：${row.inspectorName || '-'}`)
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 检验录入 ----------
const recordVisible = ref(false)
const recordFormRef = ref<FormInstance>()
const currentTask = ref<InspectionTask | null>(null)
const recordForm = reactive<RecordForm>({
  inspectionTaskId: '',
  goodQty: 0,
  defectQty: 0,
  defectItems: [],
  remark: '',
})

const remaining = computed(() => {
  if (!currentTask.value) return 0
  return Math.max(0, currentTask.value.planQty - currentTask.value.inspectedQty)
})

const defectSum = computed(() =>
  recordForm.defectItems.reduce((sum, item) => sum + (item.defectQty || 0), 0),
)

const recordRules: FormRules = {
  goodQty: [
    { required: true, message: '请输入合格数量', trigger: 'blur' },
    {
      validator: (_rule, _value, callback) => {
        if (recordForm.goodQty + recordForm.defectQty < 1) {
          callback(new Error('合格数与不良数不能同时为 0'))
        } else if (recordForm.goodQty + recordForm.defectQty > remaining.value) {
          callback(new Error(`超过待检数 ${remaining.value}`))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  defectQty: [{ required: true, message: '请输入不良数量', trigger: 'blur' }],
}

function addDefectRow() {
  recordForm.defectItems.push({ defectCode: '', defectQty: 1, remark: '' })
}

function openRecord(row: InspectionTask) {
  currentTask.value = row
  Object.assign(recordForm, {
    inspectionTaskId: row.id,
    goodQty: 0,
    defectQty: 0,
    defectItems: [] as DefectItem[],
    remark: '',
  })
  recordVisible.value = true
}

async function handleRecord() {
  const valid = await recordFormRef.value?.validate().catch(() => false)
  if (!valid || !currentTask.value) return
  if (recordForm.defectQty > 0) {
    const items = recordForm.defectItems
    if (items.length === 0) {
      ElMessage.warning('不良数大于 0 时请添加不良明细')
      return
    }
    if (items.some((item) => !item.defectCode)) {
      ElMessage.warning('请为每个不良行选择不良类型')
      return
    }
    if (defectSum.value !== recordForm.defectQty) {
      ElMessage.warning('不良行数量合计必须等于不良数')
      return
    }
  }
  saving.value = true
  try {
    await inspectionRecordApi.create({
      inspectionTaskId: recordForm.inspectionTaskId,
      goodQty: recordForm.goodQty,
      defectQty: recordForm.defectQty,
      defectItems: recordForm.defectQty > 0 ? recordForm.defectItems : undefined,
      remark: recordForm.remark || undefined,
    })
    ElMessage.success('检验录入成功')
    recordVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.record-form {
  margin-top: 12px;
}

.defect-box {
  width: 100%;
}

.defect-sum {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
