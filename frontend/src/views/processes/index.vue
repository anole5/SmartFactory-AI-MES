<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="编码/名称"
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
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新增工序
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="processCode" label="工序编码" min-width="150" />
      <el-table-column prop="processName" label="工序名称" min-width="160" />
      <el-table-column label="质检" width="80">
        <template #default="{ row }">
          <el-tag :type="row.needInspection ? 'warning' : 'info'" size="small">
            {{ row.needInspection ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="standardMinutes" label="标准工时(分)" width="110" />
      <el-table-column prop="description" label="工序说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑工序' : '新增工序'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="工序编码" prop="processCode">
          <el-input v-model="form.processCode" placeholder="如 IQC" />
        </el-form-item>
        <el-form-item label="工序名称" prop="processName">
          <el-input v-model="form.processName" placeholder="如 来料检验" />
        </el-form-item>
        <el-form-item label="是否需要质检">
          <el-switch v-model="form.needInspection" />
        </el-form-item>
        <el-form-item label="标准工时(分)" prop="standardMinutes">
          <el-input-number v-model="form.standardMinutes" :min="0" :precision="1" :step="1" />
        </el-form-item>
        <el-form-item label="工序说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { processApi } from '@/api'
import type { Process, ProcessQuery, ProcessSave } from '@/api/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<Process[]>([])
const total = ref('0')

const query = reactive<ProcessQuery>({ pageNum: 1, pageSize: 10, keyword: '' })

async function load() {
  loading.value = true
  try {
    const page = await processApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
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
  query.keyword = ''
  query.pageNum = 1
  load()
}

// ---------- 新增/编辑 ----------
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<ProcessSave>({
  processCode: '',
  processName: '',
  needInspection: false,
  standardMinutes: 0,
  description: '',
})

const formRules: FormRules = {
  processCode: [{ required: true, message: '请输入工序编码', trigger: 'blur' }],
  processName: [{ required: true, message: '请输入工序名称', trigger: 'blur' }],
  standardMinutes: [{ required: true, message: '请输入标准工时', trigger: 'blur' }],
}

function openDialog(row?: Process) {
  editingId.value = row?.id ?? null
  if (row) {
    Object.assign(form, {
      processCode: row.processCode,
      processName: row.processName,
      needInspection: row.needInspection,
      standardMinutes: row.standardMinutes,
      description: row.description ?? '',
    })
  } else {
    Object.assign(form, {
      processCode: '',
      processName: '',
      needInspection: false,
      standardMinutes: 0,
      description: '',
    })
  }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await processApi.update(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await processApi.create({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 删除 ----------
async function handleDelete(row: Process) {
  await ElMessageBox.confirm(`确定删除工序「${row.processName}」吗？被工艺路线步骤引用的工序无法删除。`, '警告', {
    type: 'warning',
  })
  await processApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>
