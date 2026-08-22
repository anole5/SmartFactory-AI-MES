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
        <el-option v-for="(label, code) in MATERIAL_STATUS" :key="code" :label="label" :value="code" />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新增物料
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="materialCode" label="物料编码" min-width="170" />
      <el-table-column prop="materialName" label="物料名称" min-width="160" />
      <el-table-column prop="materialType" label="类型" width="110" />
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column label="批次追溯" width="90">
        <template #default="{ row }">
          <el-tag :type="row.traceRequired ? 'warning' : 'info'" size="small">
            {{ row.traceRequired ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.status)">{{ labelOf(MATERIAL_STATUS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
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
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑物料' : '新增物料'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="物料编码" prop="materialCode">
          <el-input v-model="form.materialCode" placeholder="如 PANEL-LG-55UHD-001" />
        </el-form-item>
        <el-form-item label="物料名称" prop="materialName">
          <el-input v-model="form.materialName" placeholder="如 LG 55 英寸 4K UHD 液晶面板" />
        </el-form-item>
        <el-form-item label="物料类型">
          <el-input v-model="form.materialType" placeholder="如 核心件/板卡/结构件/包材" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="如 片" style="width: 160px" />
        </el-form-item>
        <el-form-item label="批次追溯">
          <el-switch v-model="form.traceRequired" />
          <span class="form-tip">关键件（面板/主板/电源板）建议开启</span>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { materialApi } from '@/api'
import type { Material, MaterialQuery, MaterialSave } from '@/api/types'
import { MATERIAL_STATUS, labelOf, tagTypeOf } from '@/constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref<Material[]>([])
const total = ref('0')

const query = reactive<MaterialQuery>({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

async function load() {
  loading.value = true
  try {
    const page = await materialApi.page({
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
const form = reactive<MaterialSave>({
  materialCode: '',
  materialName: '',
  materialType: '',
  unit: '',
  traceRequired: false,
  remark: '',
})

const formRules: FormRules = {
  materialCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
}

function openDialog(row?: Material) {
  editingId.value = row?.id ?? null
  if (row) {
    Object.assign(form, {
      materialCode: row.materialCode,
      materialName: row.materialName,
      materialType: row.materialType ?? '',
      unit: row.unit ?? '',
      traceRequired: row.traceRequired,
      remark: row.remark ?? '',
    })
  } else {
    Object.assign(form, {
      materialCode: '',
      materialName: '',
      materialType: '',
      unit: '',
      traceRequired: false,
      remark: '',
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
      await materialApi.update(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await materialApi.create({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 启停用 / 删除 ----------
async function handleToggle(row: Material) {
  const target = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await ElMessageBox.confirm(
    `确定${target === 'ENABLED' ? '启用' : '停用'}物料「${row.materialName}」吗？`,
    '提示',
    { type: 'warning' },
  )
  await materialApi.changeStatus(row.id, target)
  ElMessage.success('操作成功')
  load()
}

async function handleDelete(row: Material) {
  await ElMessageBox.confirm(`确定删除物料「${row.materialName}」吗？被 BOM 明细引用的物料无法删除。`, '警告', {
    type: 'warning',
  })
  await materialApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
