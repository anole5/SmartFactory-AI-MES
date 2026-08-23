<template>
  <div class="assistant-page">
    <el-card shadow="never">
      <template #header><span class="card-title">异常处理建议</span></template>

      <!-- 选择异常单 -->
      <div class="picker">
        <el-select
          v-model="exceptionId"
          filterable
          placeholder="选择异常单（未关闭优先）"
          style="width: 100%"
          @change="handleSelect"
        >
          <el-option
            v-for="e in exceptions"
            :key="e.id"
            :label="`${e.exceptionNo}｜${e.defectCode ? labelOf(DEFECT_CODES, e.defectCode) + '｜' : ''}${e.description}`"
            :value="e.id"
          />
        </el-select>
        <el-button @click="loadExceptions">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
      </div>

      <!-- 已保存建议回显 -->
      <el-alert
        v-if="savedSuggestion"
        title="该异常单已保存过 AI 处理建议"
        type="success"
        :closable="false"
        style="margin: 10px 0"
      />

      <!-- 生成建议 -->
      <div class="action-row">
        <el-button
          v-permission="'ai:assistant:generate'"
          type="primary"
          :loading="generating"
          :disabled="!exceptionId"
          @click="handleGenerate"
        >
          <el-icon><MagicStick /></el-icon>&nbsp;生成处理建议
        </el-button>
        <el-tag v-if="suggestion && suggestion.fallback" type="warning" effect="plain">模板建议（AI 降级）</el-tag>
        <span v-if="generating" class="generating-tip">pro 档模型深度推理中，约 5~20 秒…</span>
      </div>

      <!-- 建议正文（可编辑） -->
      <el-input
        v-model="suggestionText"
        type="textarea"
        :rows="12"
        placeholder="选择异常单后点击「生成处理建议」，AI 会结合知识库故障手册给出排查方向与处理步骤"
        style="margin-top: 10px"
      />

      <!-- 保存回写 -->
      <div class="action-row" style="margin-top: 10px">
        <el-button
          v-permission="'ai:assistant:save'"
          type="success"
          :loading="saving"
          :disabled="!exceptionId || !suggestionText.trim()"
          @click="handleSave"
        >
          <el-icon><CircleCheck /></el-icon>&nbsp;保存回写异常单
        </el-button>
        <span class="save-tip">保存后写入异常单「AI 处理建议」并记录追溯（权限：admin / 质检员）</span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { assistantApi, exceptionApi } from '@/api'
import type { ExceptionOrder, ExceptionSuggestion } from '@/api/types'
import { DEFECT_CODES, labelOf } from '@/constants/dict'

const exceptions = ref<ExceptionOrder[]>([])
const exceptionId = ref('')
const suggestion = ref<ExceptionSuggestion>()
const suggestionText = ref('')
const savedSuggestion = ref(false)
const generating = ref(false)
const saving = ref(false)

async function loadExceptions() {
  // 未关闭异常优先：直接查全量，前端排序（OPEN > PROCESSING > CLOSED）
  const page = await exceptionApi.page({ pageNum: 1, pageSize: 50 })
  const rank: Record<string, number> = { OPEN: 0, PROCESSING: 1, CLOSED: 2 }
  exceptions.value = [...page.records].sort(
    (a, b) => (rank[a.status] ?? 9) - (rank[b.status] ?? 9),
  )
}

async function handleSelect() {
  suggestion.value = undefined
  suggestionText.value = ''
  savedSuggestion.value = false
  if (!exceptionId.value) return
  const saved = await assistantApi.getSuggestion(exceptionId.value)
  if (saved.suggestion) {
    savedSuggestion.value = true
    suggestionText.value = saved.suggestion
  }
}

async function handleGenerate() {
  if (!exceptionId.value || generating.value) return
  generating.value = true
  try {
    suggestion.value = await assistantApi.suggest(exceptionId.value)
    suggestionText.value = suggestion.value.suggestion ?? ''
  } catch {
    ElMessage.error('建议生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

async function handleSave() {
  if (!exceptionId.value || saving.value) return
  saving.value = true
  try {
    await assistantApi.save(exceptionId.value, suggestionText.value.trim())
    savedSuggestion.value = true
    ElMessage.success('已回写异常单 AI 处理建议')
  } catch {
    ElMessage.error('保存失败（请确认有保存权限）')
  } finally {
    saving.value = false
  }
}

onMounted(loadExceptions)
</script>

<style scoped>
.assistant-page {
  max-width: 860px;
}

.card-title {
  font-weight: 600;
}

.picker {
  display: flex;
  gap: 8px;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.generating-tip {
  color: #909399;
  font-size: 12px;
}

.save-tip {
  color: #909399;
  font-size: 12px;
}
</style>
