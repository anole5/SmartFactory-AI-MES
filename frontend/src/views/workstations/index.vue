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
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="(label, code) in WORKSTATION_STATUS" :key="code" :label="label" :value="code" />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新增工位
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="workstationCode" label="工位编码" min-width="150" />
      <el-table-column prop="workstationName" label="工位名称" min-width="150" />
      <el-table-column prop="equipmentCode" label="设备编码" min-width="150" />
      <el-table-column prop="equipmentName" label="设备名称" min-width="150" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.status)">{{ labelOf(WORKSTATION_STATUS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link :type="row.status === 'ENABLED' ? 'warning' : 'success'" @click="handleToggle(row)">
            {{ row.status === 'ENABLED' ? '停用' : '启用' }}
          </el-button>
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
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑工位' : '新增工位'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="工位编码" prop="workstationCode">
          <el-input v-model="form.workstationCode" placeholder="如 WS-SMT-01" />
        </el-form-item>
        <el-form-item label="工位名称" prop="workstationName">
          <el-input v-model="form.workstationName" placeholder="如 SMT 贴片 1 号工位" />
        </el-form-item>
        <el-form-item label="设备编码">
          <el-input v-model="form.equipmentCode" placeholder="如 EQ-SMT-01" />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="form.equipmentName" placeholder="如 三星 SM481 贴片机" />
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
import { workstationApi } from '@/api'
import type { Workstation, WorkstationQuery, WorkstationSave } from '@/api/types'
import { WORKSTATION_STATUS, labelOf, tagTypeOf } from '@/constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref<Workstation[]>([])
const total = ref('0')

const query = reactive<WorkstationQuery>({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

async function load() {
  loading.value = true
  try {
    const page = await workstationApi.page({
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

// ---------- 新增/编辑 ----------
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<WorkstationSave>({
  workstationCode: '',
  workstationName: '',
  equipmentCode: '',
  equipmentName: '',
})

const formRules: FormRules = {
  workstationCode: [{ required: true, message: '请输入工位编码', trigger: 'blur' }],
  workstationName: [{ required: true, message: '请输入工位名称', trigger: 'blur' }],
}

function openDialog(row?: Workstation) {
  editingId.value = row?.id ?? null
  if (row) {
    Object.assign(form, {
      workstationCode: row.workstationCode,
      workstationName: row.workstationName,
      equipmentCode: row.equipmentCode ?? '',
      equipmentName: row.equipmentName ?? '',
    })
  } else {
    Object.assign(form, { workstationCode: '', workstationName: '', equipmentCode: '', equipmentName: '' })
  }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await workstationApi.update(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await workstationApi.create({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 启停用 / 删除 ----------
async function handleToggle(row: Workstation) {
  const target = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await ElMessageBox.confirm(
    `确定${target === 'ENABLED' ? '启用' : '停用'}工位「${row.workstationName}」吗？`,
    '提示',
    { type: 'warning' },
  )
  await workstationApi.changeStatus(row.id, target)
  ElMessage.success('操作成功')
  load()
}

async function handleDelete(row: Workstation) {
  await ElMessageBox.confirm(`确定删除工位「${row.workstationName}」吗？被工艺路线步骤引用的工位无法删除。`, '警告', {
    type: 'warning',
  })
  await workstationApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>
