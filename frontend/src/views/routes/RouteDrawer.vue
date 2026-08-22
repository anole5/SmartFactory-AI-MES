<template>
  <el-drawer
    :model-value="modelValue"
    :title="routeId ? '编辑工艺路线（仅草稿可编辑）' : '新建工艺路线'"
    size="900px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
      <el-form-item label="产品" prop="productId">
        <el-select
          v-model="form.productId"
          placeholder="选择产品（仅启用产品可维护工艺路线）"
          filterable
          style="width: 100%"
          :disabled="!!routeId"
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
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="工艺路线备注说明" />
      </el-form-item>
    </el-form>

    <!-- 步骤编辑表格 -->
    <div class="steps-header">
      <h4>工艺步骤（顺序 = 数组顺序，可用上移/下移调整）</h4>
      <el-button type="primary" plain size="small" @click="addStep">
        <el-icon><Plus /></el-icon>&nbsp;添加步骤
      </el-button>
    </div>
    <el-table :data="steps" stripe border size="small">
      <el-table-column label="序号" width="70">
        <template #default="{ $index }">{{ $index + 1 }}</template>
      </el-table-column>
      <el-table-column label="工序" min-width="220">
        <template #default="{ row }">
          <el-select v-model="row.processId" placeholder="选择工序" filterable style="width: 100%">
            <el-option
              v-for="p in processes"
              :key="p.id"
              :label="`${p.processCode} / ${p.processName}`"
              :value="p.id"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="默认工位" min-width="200">
        <template #default="{ row }">
          <el-select v-model="row.workstationId" placeholder="可不选" clearable filterable style="width: 100%">
            <el-option
              v-for="w in workstations"
              :key="w.id"
              :label="`${w.workstationCode} / ${w.workstationName}`"
              :value="w.id"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="质检" width="80">
        <template #default="{ row }">
          <el-switch v-model="row.needInspection" />
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="120">
        <template #default="{ row }">
          <el-input v-model="row.remark" placeholder="选填" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ $index }">
          <el-button link :disabled="$index === 0" @click="moveStep($index, -1)">
            <el-icon><Top /></el-icon>上移
          </el-button>
          <el-button link :disabled="$index === steps.length - 1" @click="moveStep($index, 1)">
            <el-icon><Bottom /></el-icon>下移
          </el-button>
          <el-button link type="danger" @click="removeStep($index)">删除</el-button>
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
import { routeApi } from '@/api'
import type { Process, Product, RouteSave, Workstation } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  routeId: string | null
  products: Product[]
  processes: Process[]
  workstations: Workstation[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

/** 步骤行前端本地结构（needInspection 默认 true，可切） */
interface StepRow {
  processId: string
  workstationId: string | null
  needInspection: boolean
  remark: string
}

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<RouteSave>({
  productId: '',
  version: 'V1',
  remark: '',
  steps: [],
})

const steps = ref<StepRow[]>([])

const formRules: FormRules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
}

function addStep() {
  steps.value.push({ processId: '', workstationId: null, needInspection: true, remark: '' })
}

function removeStep(index: number) {
  steps.value.splice(index, 1)
}

function moveStep(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= steps.value.length) return
  const [row] = steps.value.splice(index, 1)
  steps.value.splice(target, 0, row)
}

// 打开时初始化：编辑则拉详情回填，新建则给一个空步骤
watch(
  () => props.modelValue,
  async (now) => {
    if (!now) return
    formRef.value?.clearValidate()
    if (props.routeId) {
      const route = await routeApi.detail(props.routeId)
      Object.assign(form, {
        productId: route.productId,
        version: route.version ?? 'V1',
        remark: route.remark ?? '',
      })
      steps.value = (route.steps ?? []).map((s) => ({
        processId: s.processId,
        workstationId: s.workstationId ?? null,
        needInspection: s.needInspection,
        remark: s.remark ?? '',
      }))
    } else {
      Object.assign(form, { productId: '', version: 'V1', remark: '' })
      steps.value = []
      addStep()
    }
  },
)

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (steps.value.length === 0) {
    ElMessage.warning('请至少添加一个工艺步骤')
    return
  }
  if (steps.value.some((s) => !s.processId)) {
    ElMessage.warning('存在未选择工序的步骤行')
    return
  }
  saving.value = true
  try {
    // 整单提交：步骤数组顺序即工艺顺序，后端按序生成 sequenceNo 并回填快照
    const payload: RouteSave = {
      productId: form.productId,
      version: form.version || 'V1',
      remark: form.remark || undefined,
      steps: steps.value.map((s) => ({
        processId: s.processId,
        workstationId: s.workstationId || null,
        needInspection: s.needInspection,
        remark: s.remark || undefined,
      })),
    }
    if (props.routeId) {
      await routeApi.update(props.routeId, payload)
      ElMessage.success('工艺路线修改成功')
    } else {
      await routeApi.create(payload)
      ElMessage.success('工艺路线创建成功（草稿状态，激活后生效）')
    }
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.steps-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 10px;
}

.steps-header h4 {
  margin: 0;
}
</style>
