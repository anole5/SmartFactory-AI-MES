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
        <el-option v-for="(label, code) in EQUIPMENT_STATUS" :key="code" :label="label" :value="code" />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-tag type="info" effect="plain">设备状态每 15s 由系统随机漂移</el-tag>
      <el-button v-permission="'master:equipment:create'" type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新增设备
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="equipmentCode" label="设备编码" min-width="170" />
      <el-table-column prop="equipmentName" label="设备名称" min-width="160" />
      <el-table-column prop="model" label="型号" min-width="130" show-overflow-tooltip />
      <el-table-column label="所属工位" min-width="130">
        <template #default="{ row }">{{ row.workstationName || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.status)">{{ labelOf(EQUIPMENT_STATUS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'master:equipment:update'" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button v-permission="'master:equipment:status'" link type="warning" @click="openStatus(row)">状态切换</el-button>
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
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑设备' : '新增设备'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="设备编码" prop="equipmentCode">
          <el-input v-model="form.equipmentCode" placeholder="如 EQ-BOARD-CHECK-01" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="设备名称" prop="equipmentName">
          <el-input v-model="form.equipmentName" placeholder="如 板卡检查机" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="form.model" placeholder="如 BM-2000" />
        </el-form-item>
        <el-form-item label="所属工位">
          <el-select v-model="form.workstationId" placeholder="选填" clearable filterable style="width: 100%">
            <el-option
              v-for="w in workstations"
              :key="w.id"
              :label="`${w.workstationCode} / ${w.workstationName}`"
              :value="w.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 状态切换弹窗 -->
    <el-dialog v-model="statusVisible" title="切换设备状态" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="设备">
          <span>{{ currentRow?.equipmentCode }}（{{ currentRow?.equipmentName }}）</span>
        </el-form-item>
        <el-form-item label="目标状态">
          <el-radio-group v-model="targetStatus">
            <el-radio v-for="(label, code) in EQUIPMENT_STATUS" :key="code" :value="code">{{ label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleStatusSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { equipmentApi, workstationApi } from '@/api'
import type { Equipment, EquipmentQuery, EquipmentSave, Workstation } from '@/api/types'
import { EQUIPMENT_STATUS, labelOf, tagTypeOf } from '@/constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref<Equipment[]>([])
const total = ref('0')

const query = reactive<EquipmentQuery>({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

async function load() {
  loading.value = true
  try {
    const page = await equipmentApi.page({
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
const form = reactive<EquipmentSave>({
  equipmentCode: '',
  equipmentName: '',
  model: '',
  workstationId: null,
  remark: '',
})

const formRules: FormRules = {
  equipmentCode: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  equipmentName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
}

function openDialog(row?: Equipment) {
  editingId.value = row?.id ?? null
  if (row) {
    Object.assign(form, {
      equipmentCode: row.equipmentCode,
      equipmentName: row.equipmentName,
      model: row.model ?? '',
      workstationId: row.workstationId ?? null,
      remark: row.remark ?? '',
    })
  } else {
    Object.assign(form, { equipmentCode: '', equipmentName: '', model: '', workstationId: null, remark: '' })
  }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await equipmentApi.update(editingId.value, {
        ...form,
        workstationId: form.workstationId || null,
        remark: form.remark || undefined,
      })
      ElMessage.success('修改成功')
    } else {
      await equipmentApi.create({
        ...form,
        workstationId: form.workstationId || null,
        remark: form.remark || undefined,
      })
      ElMessage.success('创建成功（默认状态：运行）')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 状态切换 ----------
const statusVisible = ref(false)
const currentRow = ref<Equipment | null>(null)
const targetStatus = ref('')

function openStatus(row: Equipment) {
  currentRow.value = row
  targetStatus.value = row.status
  statusVisible.value = true
}

async function handleStatusSave() {
  if (!currentRow.value) return
  saving.value = true
  try {
    await equipmentApi.changeStatus(currentRow.value.id, targetStatus.value)
    ElMessage.success('状态已切换')
    statusVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const workstations = ref<Workstation[]>([])

onMounted(async () => {
  load()
  const wsPage = await workstationApi.page({ pageNum: 1, pageSize: 100, status: 'ENABLED' })
  workstations.value = wsPage.records
})
</script>
