<template>
  <el-drawer
    :model-value="modelValue"
    :title="bomId ? '编辑 BOM（仅草稿可编辑）' : '新建 BOM'"
    size="860px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
      <el-form-item label="产品" prop="productId">
        <el-select
          v-model="form.productId"
          placeholder="选择产品（仅启用产品可维护 BOM）"
          filterable
          style="width: 100%"
          :disabled="!!bomId"
        >
          <el-option
            v-for="p in products"
            :key="p.id"
            :label="`${p.productCode} / ${p.productName}`"
            :value="p.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="版本">
        <el-input v-model="form.version" placeholder="默认 V1" style="width: 200px" />
      </el-form-item>
      <el-form-item label="生效日期">
        <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="BOM 备注说明" />
      </el-form-item>
    </el-form>

    <!-- 明细编辑表格 -->
    <div class="items-header">
      <h4>物料明细</h4>
      <el-button type="primary" plain size="small" @click="addItem">
        <el-icon><Plus /></el-icon>&nbsp;添加物料行
      </el-button>
    </div>
    <el-table :data="items" stripe border size="small">
      <el-table-column type="index" label="行号" width="60" />
      <el-table-column label="物料" min-width="240">
        <template #default="{ row }">
          <el-select v-model="row.materialId" placeholder="选择物料" filterable style="width: 100%">
            <el-option
              v-for="m in materials"
              :key="m.id"
              :label="`${m.materialCode} / ${m.materialName}`"
              :value="m.id"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="单位用量" width="140">
        <template #default="{ row }">
          <el-input-number v-model="row.requiredQty" :min="0.0001" :precision="4" :step="1" style="width: 120px" />
        </template>
      </el-table-column>
      <el-table-column label="损耗率(%)" width="140">
        <template #default="{ row }">
          <el-input-number v-model="row.lossRate" :min="0" :max="100" :precision="2" :step="0.5" style="width: 120px" />
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.remark" placeholder="选填" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70">
        <template #default="{ $index }">
          <el-button link type="danger" @click="removeItem($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存整单</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { bomApi } from '@/api'
import type { BomSave, Material, Product } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  bomId: string | null
  products: Product[]
  materials: Material[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

/** 明细行前端本地结构（materialId 为下拉选择值） */
interface ItemRow {
  materialId: string
  requiredQty: number
  lossRate: number
  remark: string
}

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<BomSave>({
  productId: '',
  version: 'V1',
  effectiveDate: '',
  remark: '',
  items: [],
})

const items = ref<ItemRow[]>([])

const formRules: FormRules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
}

function addItem() {
  items.value.push({ materialId: '', requiredQty: 1, lossRate: 0, remark: '' })
}

function removeItem(index: number) {
  items.value.splice(index, 1)
}

// 打开时初始化：编辑则拉详情回填，新建则给一行空明细
watch(
  () => props.modelValue,
  async (now) => {
    if (!now) return
    formRef.value?.clearValidate()
    if (props.bomId) {
      const bom = await bomApi.detail(props.bomId)
      Object.assign(form, {
        productId: bom.productId,
        version: bom.version ?? 'V1',
        effectiveDate: bom.effectiveDate ?? '',
        remark: bom.remark ?? '',
      })
      items.value = (bom.items ?? []).map((i) => ({
        materialId: i.materialId,
        requiredQty: Number(i.requiredQty),
        lossRate: Number(i.lossRate ?? 0),
        remark: i.remark ?? '',
      }))
    } else {
      Object.assign(form, { productId: '', version: 'V1', effectiveDate: '', remark: '' })
      items.value = []
      addItem()
    }
  },
)

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (items.value.length === 0) {
    ElMessage.warning('请至少添加一行物料明细')
    return
  }
  if (items.value.some((i) => !i.materialId)) {
    ElMessage.warning('存在未选择物料的明细行')
    return
  }
  const materialIds = new Set(items.value.map((i) => i.materialId))
  if (materialIds.size !== items.value.length) {
    ElMessage.warning('明细物料不能重复')
    return
  }
  saving.value = true
  try {
    // 整单提交：头 + 明细数组一次发给后端（后端事务内落库并回填快照）
    const payload: BomSave = {
      productId: form.productId,
      version: form.version || 'V1',
      effectiveDate: form.effectiveDate || undefined,
      remark: form.remark || undefined,
      items: items.value.map((i) => ({
        materialId: i.materialId,
        requiredQty: i.requiredQty,
        lossRate: i.lossRate,
        remark: i.remark || undefined,
      })),
    }
    if (props.bomId) {
      await bomApi.update(props.bomId, payload)
      ElMessage.success('BOM 修改成功')
    } else {
      await bomApi.create(payload)
      ElMessage.success('BOM 创建成功（草稿状态，激活后生效）')
    }
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.items-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 10px;
}

.items-header h4 {
  margin: 0;
}
</style>
