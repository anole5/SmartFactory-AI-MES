<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="外部订单号/产品名"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="handleSearch">
        <el-option
          v-for="(label, code) in EXTERNAL_ORDER_STATUS"
          :key="code"
          :label="label"
          :value="code"
        />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-button v-permission="'erp:order:create'" type="primary" @click="openDialog">
        <el-icon><Plus /></el-icon>&nbsp;模拟下单
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="externalOrderNo" label="外部订单号" min-width="170" />
      <el-table-column prop="productNameSnapshot" label="产品" min-width="180" show-overflow-tooltip />
      <el-table-column prop="planQty" label="计划数量" width="90" />
      <el-table-column label="优先级" width="80">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.priority)" effect="plain">{{ labelOf(PRIORITY, row.priority) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="计划时间" width="200">
        <template #default="{ row }">
          <span v-if="row.planStartTime || row.planEndTime">{{ row.planStartTime || '-' }} 至 {{ row.planEndTime || '-' }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="tagTypeOf(row.status)">{{ labelOf(EXTERNAL_ORDER_STATUS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联工单" width="90">
        <template #default="{ row }">{{ row.workOrderId ? `#${row.workOrderId}` : '-' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING'"
            v-permission="'erp:order:to-work-order'"
            link
            type="success"
            @click="handleToWorkOrder(row)"
          >转工单</el-button>
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

    <!-- 模拟下单弹窗（ERP 系统下单 → 本系统一键转工单） -->
    <el-dialog v-model="dialogVisible" title="模拟下单（ERP 推单）" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="产品" prop="productId">
          <el-select
            v-model="form.productId"
            placeholder="选择产品（转工单时自动解析其生效 BOM/工艺路线）"
            filterable
            style="width: 100%"
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
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 180px">
            <el-option v-for="(label, code) in PRIORITY" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划时间">
          <el-date-picker
            v-model="planRange"
            type="daterange"
            range-separator="至"
            start-placeholder="计划开始"
            end-placeholder="计划结束"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">提交订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { erpOrderApi, productApi } from '@/api'
import type { ErpOrder, ErpOrderQuery, ErpOrderSave, Product } from '@/api/types'
import { EXTERNAL_ORDER_STATUS, PRIORITY, labelOf, tagTypeOf } from '@/constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref<ErpOrder[]>([])
const total = ref('0')
const query = reactive<ErpOrderQuery>({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

async function load() {
  loading.value = true
  try {
    const page = await erpOrderApi.page({
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

// ---------- 模拟下单 ----------
const products = ref<Product[]>([])

async function loadProducts() {
  const page = await productApi.page({ pageNum: 1, pageSize: 100, status: 'ENABLED' })
  products.value = page.records
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ErpOrderSave>({
  productId: '',
  planQty: 1,
  priority: 'NORMAL',
  remark: '',
})
const planRange = ref<[string, string] | null>(null)

const formRules: FormRules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  planQty: [{ required: true, message: '请输入计划数量', trigger: 'blur' }],
}

function openDialog() {
  Object.assign(form, { productId: '', planQty: 1, priority: 'NORMAL', remark: '' })
  planRange.value = null
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await erpOrderApi.create({
      ...form,
      priority: form.priority || undefined,
      remark: form.remark || undefined,
      planStartTime: planRange.value?.[0],
      planEndTime: planRange.value?.[1],
    })
    ElMessage.success('下单成功（待转工单状态，转工单后生成生产工单）')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 一键转工单 ----------
async function handleToWorkOrder(row: ErpOrder) {
  await ElMessageBox.confirm(
    `确定将外部订单「${row.externalOrderNo}」转为生产工单吗？将按产品 ${row.productNameSnapshot} 生成计划 ${row.planQty} 台的工单。`,
    '提示',
    { type: 'warning' },
  )
  await erpOrderApi.toWorkOrder(row.id)
  ElMessage.success('转工单成功，工单已创建（草稿状态，请到生产工单页下发）')
  load()
}

onMounted(() => {
  load()
  loadProducts()
})
</script>
