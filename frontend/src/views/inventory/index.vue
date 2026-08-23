<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-select v-model="query.itemType" placeholder="类型" clearable style="width: 110px" @change="handleSearch">
        <el-option v-for="(label, code) in ITEM_TYPE" :key="code" :label="label" :value="code" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="物料/产品编码或名称"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="spacer" />
      <el-button @click="openTxDrawer">
        <el-icon><Tickets /></el-icon>&nbsp;库存流水
      </el-button>
      <el-button v-permission="'wms:inventory:in'" type="primary" @click="openStockInDialog">
        <el-icon><Download /></el-icon>&nbsp;采购入库
      </el-button>
      <el-button v-permission="'wms:pick'" type="warning" @click="openPickDialog">
        <el-icon><Box /></el-icon>&nbsp;工单领料
      </el-button>
    </div>

    <!-- 库存表 -->
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.itemType === 'MATERIAL' ? 'primary' : 'success'" effect="plain">
            {{ labelOf(ITEM_TYPE, row.itemType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="itemCode" label="编码" min-width="140" />
      <el-table-column prop="itemName" label="名称" min-width="170" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column prop="qty" label="库存数量" width="100">
        <template #default="{ row }">
          <span :class="row.qty < 0 ? 'qty-low' : 'qty-normal'">{{ row.qty }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column prop="updatedAt" label="更新时间" width="160" />
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

    <!-- 采购入库弹窗 -->
    <el-dialog v-model="stockInVisible" title="采购入库" width="480px" destroy-on-close>
      <el-form ref="stockInFormRef" :model="stockInForm" :rules="stockInRules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="stockInForm.materialId" placeholder="选择入库物料" filterable style="width: 100%">
            <el-option
              v-for="m in materials"
              :key="m.id"
              :label="`${m.materialCode} / ${m.materialName}`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="入库数量" prop="qty">
          <el-input-number v-model="stockInForm.qty" :min="1" :max="999999" style="width: 180px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="stockInForm.remark" type="textarea" :rows="2" placeholder="选填，默认「采购入库」" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockInVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleStockIn">入库</el-button>
      </template>
    </el-dialog>

    <!-- 工单领料弹窗：按工单 BOM 关键物料（trace_required=1）自动领料，结果显示应领/实领 -->
    <el-dialog v-model="pickVisible" title="工单领料（按 BOM 关键物料自动领料）" width="620px" destroy-on-close>
      <el-form ref="pickFormRef" :model="pickForm" :rules="pickRules" label-width="100px">
        <el-form-item label="工单 ID" prop="workOrderId">
          <el-input-number v-model="pickForm.workOrderId" :min="1" :controls="false" style="width: 220px" />
          <div class="form-tip">ERP 推单工单开工前须完成关键物料足额领用，否则开工被 409 拦截</div>
        </el-form-item>
      </el-form>
      <div v-if="pickResult" class="pick-result">
        <div class="pick-result-title">
          领料成功：工单 {{ pickResult.workOrderNo }}，共 {{ pickResult.items.length }} 种关键物料
        </div>
        <el-table :data="pickResult.items" size="small" stripe border>
          <el-table-column prop="materialCode" label="物料编码" width="130" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" />
          <el-table-column prop="needQty" label="应领" width="70" />
          <el-table-column prop="actualPickedQty" label="本次实领" width="90" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="pickVisible = false">关闭</el-button>
        <el-button type="primary" :loading="picking" @click="handlePick">领料</el-button>
      </template>
    </el-dialog>

    <!-- 库存流水抽屉 -->
    <el-drawer v-model="txVisible" title="库存流水" size="860px" destroy-on-close>
      <div class="toolbar">
        <el-select v-model="txQuery.itemType" placeholder="类型" clearable style="width: 110px" @change="handleTxSearch">
          <el-option v-for="(label, code) in ITEM_TYPE" :key="code" :label="label" :value="code" />
        </el-select>
        <el-select v-model="txQuery.bizType" placeholder="业务类型" clearable style="width: 140px" @change="handleTxSearch">
          <el-option v-for="(label, code) in STOCK_BIZ_TYPE" :key="code" :label="label" :value="code" />
        </el-select>
        <el-input
          v-model="txQuery.workOrderId"
          placeholder="工单 ID"
          clearable
          style="width: 130px"
          @keyup.enter="handleTxSearch"
          @clear="handleTxSearch"
        />
        <el-button type="primary" @click="handleTxSearch">
          <el-icon><Search /></el-icon>&nbsp;查询
        </el-button>
      </div>
      <el-table v-loading="txLoading" :data="txRows" stripe border size="small">
        <el-table-column prop="txNo" label="流水号" width="170" />
        <el-table-column label="方向" width="70">
          <template #default="{ row }">
            <el-tag :type="tagTypeOf(row.txType)" size="small">{{ labelOf(STOCK_TX_TYPE, row.txType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务类型" width="100">
          <template #default="{ row }">{{ labelOf(STOCK_BIZ_TYPE, row.bizType) }}</template>
        </el-table-column>
        <el-table-column prop="itemCode" label="编码" width="130" />
        <el-table-column prop="itemName" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="数量" width="80">
          <template #default="{ row }">
            <span :class="row.txType === 'IN' ? 'qty-normal' : 'qty-low'">
              {{ row.txType === 'IN' ? '+' : '-' }}{{ row.qty }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="关联工单" width="90">
          <template #default="{ row }">{{ row.workOrderId ? `#${row.workOrderId}` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="160" />
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="txQuery.pageNum"
          v-model:page-size="txQuery.pageSize"
          :total="Number(txTotal)"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="loadTx"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { materialApi, wmsApi } from '@/api'
import type {
  InventoryItem,
  InventoryQuery,
  Material,
  PickResult,
  StockInSave,
  StockTx,
  StockTxQuery,
} from '@/api/types'
import { ITEM_TYPE, STOCK_BIZ_TYPE, STOCK_TX_TYPE, labelOf, tagTypeOf } from '@/constants/dict'

const loading = ref(false)
const rows = ref<InventoryItem[]>([])
const total = ref('0')
const query = reactive<InventoryQuery>({ pageNum: 1, pageSize: 10, itemType: '', keyword: '' })

async function load() {
  loading.value = true
  try {
    const page = await wmsApi.inventoryPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      itemType: query.itemType || undefined,
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
  query.itemType = ''
  query.keyword = ''
  query.pageNum = 1
  load()
}

// ---------- 采购入库 ----------
const materials = ref<Material[]>([])
const stockInVisible = ref(false)
const saving = ref(false)
const stockInFormRef = ref<FormInstance>()
const stockInForm = reactive<StockInSave>({ materialId: '', qty: 1, remark: '' })

const stockInRules: FormRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  qty: [{ required: true, message: '请输入入库数量', trigger: 'blur' }],
}

async function loadMaterials() {
  const page = await materialApi.page({ pageNum: 1, pageSize: 100, status: 'ENABLED' })
  materials.value = page.records
}

function openStockInDialog() {
  Object.assign(stockInForm, { materialId: '', qty: 1, remark: '' })
  stockInVisible.value = true
}

async function handleStockIn() {
  const valid = await stockInFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await wmsApi.stockIn({
      materialId: stockInForm.materialId,
      qty: stockInForm.qty,
      remark: stockInForm.remark || undefined,
    })
    ElMessage.success('入库成功')
    stockInVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 工单领料 ----------
const pickVisible = ref(false)
const picking = ref(false)
const pickFormRef = ref<FormInstance>()
const pickForm = reactive<{ workOrderId: number | undefined }>({ workOrderId: undefined })
const pickResult = ref<PickResult | null>(null)

const pickRules: FormRules = {
  workOrderId: [{ required: true, message: '请输入工单 ID', trigger: 'blur' }],
}

function openPickDialog() {
  pickForm.workOrderId = undefined
  pickResult.value = null
  pickVisible.value = true
}

async function handlePick() {
  const valid = await pickFormRef.value?.validate().catch(() => false)
  if (!valid || pickForm.workOrderId === undefined) return
  picking.value = true
  try {
    pickResult.value = await wmsApi.pick(String(pickForm.workOrderId))
    ElMessage.success('领料成功')
    load()
  } finally {
    picking.value = false
  }
}

// ---------- 库存流水抽屉 ----------
const txVisible = ref(false)
const txLoading = ref(false)
const txRows = ref<StockTx[]>([])
const txTotal = ref('0')
const txQuery = reactive<StockTxQuery>({
  pageNum: 1,
  pageSize: 20,
  workOrderId: '',
  itemType: '',
  bizType: '',
})

function openTxDrawer() {
  txVisible.value = true
  handleTxSearch()
}

async function loadTx() {
  txLoading.value = true
  try {
    const page = await wmsApi.txPage({
      pageNum: txQuery.pageNum,
      pageSize: txQuery.pageSize,
      workOrderId: txQuery.workOrderId || undefined,
      itemType: txQuery.itemType || undefined,
      bizType: txQuery.bizType || undefined,
    })
    txRows.value = page.records
    txTotal.value = page.total
  } finally {
    txLoading.value = false
  }
}

function handleTxSearch() {
  txQuery.pageNum = 1
  loadTx()
}

onMounted(() => {
  load()
  loadMaterials()
})
</script>

<style scoped>
.qty-normal {
  color: #67c23a;
  font-weight: 600;
}

.qty-low {
  color: #f56c6c;
  font-weight: 600;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

.pick-result {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px;
}

.pick-result-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #67c23a;
}
</style>
