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
        <el-option v-for="(label, code) in PRODUCT_STATUS" :key="code" :label="label" :value="code" />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新增产品
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="productCode" label="产品编码" min-width="170" />
      <el-table-column prop="productName" label="产品名称" min-width="160" />
      <el-table-column prop="productType" label="类型" width="110" />
      <el-table-column prop="specification" label="规格型号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.status)">{{ labelOf(PRODUCT_STATUS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
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
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑产品' : '新增产品'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="产品编码" prop="productCode">
          <el-input v-model="form.productCode" placeholder="如 TV-AOC-55U4K-001" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="form.productName" placeholder="如 AOC 55 英寸 4K 智能电视" />
        </el-form-item>
        <el-form-item label="产品类型">
          <el-input v-model="form.productType" placeholder="如 智能电视" />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="form.specification" placeholder="如 55U4K" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="如 台" style="width: 160px" />
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
import { productApi } from '@/api'
import type { Product, ProductQuery, ProductSave } from '@/api/types'
import { PRODUCT_STATUS, labelOf, tagTypeOf } from '@/constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref<Product[]>([])
const total = ref('0')

const query = reactive<ProductQuery>({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

async function load() {
  loading.value = true
  try {
    const page = await productApi.page({
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
const form = reactive<ProductSave>({
  productCode: '',
  productName: '',
  productType: '',
  specification: '',
  unit: '',
})

const formRules: FormRules = {
  productCode: [{ required: true, message: '请输入产品编码', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
}

function openDialog(row?: Product) {
  editingId.value = row?.id ?? null
  if (row) {
    Object.assign(form, {
      productCode: row.productCode,
      productName: row.productName,
      productType: row.productType ?? '',
      specification: row.specification ?? '',
      unit: row.unit ?? '',
    })
  } else {
    Object.assign(form, { productCode: '', productName: '', productType: '', specification: '', unit: '' })
  }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await productApi.update(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await productApi.create({ ...form })
      ElMessage.success('创建成功（新产品默认为停用，启用后才能维护 BOM/工艺路线）')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 启停用 / 删除 ----------
async function handleToggle(row: Product) {
  const target = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const action = target === 'ENABLED' ? '启用' : '停用'
  await ElMessageBox.confirm(
    `确定${action}产品「${row.productName}」吗？${target === 'DISABLED' ? '存在生效中的 BOM/工艺路线时会被拒绝。' : ''}`,
    '提示',
    { type: 'warning' },
  )
  await productApi.changeStatus(row.id, target)
  ElMessage.success(`${action}成功`)
  load()
}

async function handleDelete(row: Product) {
  await ElMessageBox.confirm(`确定删除产品「${row.productName}」吗？被 BOM/工艺路线引用的产品无法删除。`, '警告', {
    type: 'warning',
  })
  await productApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>
