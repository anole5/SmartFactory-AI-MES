<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="(label, code) in EXCEPTION_STATUS" :key="code" :label="label" :value="code" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="异常单号 / 工单号"
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
      <el-button v-permission="'quality:exception:create'" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>&nbsp;新建异常单
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="exceptionNo" label="异常单号" min-width="150" />
      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          <el-tag effect="plain">{{ labelOf(EXCEPTION_SOURCE_TYPE, row.sourceType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="workOrderNo" label="工单号" min-width="165" />
      <el-table-column prop="defectNo" label="不良单号" min-width="150" />
      <el-table-column label="不良类型" width="150">
        <template #default="{ row }">{{ labelOf(DEFECT_CODES, row.defectCode) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.status)">{{ labelOf(EXCEPTION_STATUS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理人" width="100">
        <template #default="{ row }">{{ row.handlerName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="resolveRemark" label="处理结论" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'OPEN'"
            v-permission="'quality:exception:process'"
            link
            type="primary"
            @click="handleProcess(row)"
          >处理</el-button>
          <el-button
            v-if="row.status === 'PROCESSING'"
            v-permission="'quality:exception:close'"
            link
            type="success"
            @click="openClose(row)"
          >关闭</el-button>
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

    <!-- 新建异常单弹窗 -->
    <el-dialog v-model="createVisible" title="新建异常单" width="480px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="描述" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="异常现象描述" />
        </el-form-item>
        <el-form-item label="工单号">
          <el-input v-model="createForm.workOrderId" placeholder="选填，关联工单便于追溯" />
        </el-form-item>
        <el-form-item label="不良类型">
          <el-select v-model="createForm.defectCode" placeholder="选填" clearable style="width: 100%">
            <el-option v-for="(label, code) in DEFECT_CODES" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 关闭异常单弹窗 -->
    <el-dialog v-model="closeVisible" title="关闭异常单" width="480px" destroy-on-close>
      <el-form ref="closeFormRef" :model="closeForm" :rules="closeRules" label-width="90px">
        <el-form-item label="异常单号">
          <span>{{ currentException?.exceptionNo }}</span>
        </el-form-item>
        <el-form-item label="处理结论" prop="resolveRemark">
          <el-input v-model="closeForm.resolveRemark" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="关闭时必填处理结论" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleClose">确认关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { exceptionApi } from '@/api'
import type { ExceptionOrder, ExceptionQuery, ExceptionSave } from '@/api/types'
import { DEFECT_CODES, EXCEPTION_SOURCE_TYPE, EXCEPTION_STATUS, labelOf, tagTypeOf } from '@/constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref<ExceptionOrder[]>([])
const total = ref('0')
const query = reactive<ExceptionQuery>({ pageNum: 1, pageSize: 10, status: '', keyword: '' })

async function load() {
  loading.value = true
  try {
    const page = await exceptionApi.page({
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

// ---------- 新建异常单 ----------
const createVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<ExceptionSave>({ description: '', workOrderId: '', defectCode: '' })

const createRules: FormRules = {
  description: [{ required: true, message: '请输入异常描述', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(createForm, { description: '', workOrderId: '', defectCode: '' })
  createVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const exceptionId = await exceptionApi.create({
      description: createForm.description,
      workOrderId: createForm.workOrderId || undefined,
      defectCode: createForm.defectCode || undefined,
    })
    ElMessage.success(`异常单已创建（ID: ${exceptionId}）`)
    createVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 处理 ----------
async function handleProcess(row: ExceptionOrder) {
  saving.value = true
  try {
    await exceptionApi.process(row.id)
    ElMessage.success('已接单处理')
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 关闭 ----------
const closeVisible = ref(false)
const closeFormRef = ref<FormInstance>()
const currentException = ref<ExceptionOrder | null>(null)
const closeForm = reactive<{ resolveRemark: string }>({ resolveRemark: '' })

const closeRules: FormRules = {
  resolveRemark: [{ required: true, message: '请输入处理结论', trigger: 'blur' }],
}

function openClose(row: ExceptionOrder) {
  currentException.value = row
  closeForm.resolveRemark = ''
  closeVisible.value = true
}

async function handleClose() {
  const valid = await closeFormRef.value?.validate().catch(() => false)
  if (!valid || !currentException.value) return
  saving.value = true
  try {
    await exceptionApi.close(currentException.value.id, closeForm.resolveRemark)
    ElMessage.success('异常单已关闭')
    closeVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
