<template>
  <div class="knowledge-page">
    <!-- 左侧：文档列表 -->
    <div class="left-panel">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="文档名"
          clearable
          style="width: 160px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-select v-model="query.docType" placeholder="类型" clearable style="width: 130px" @change="load">
          <el-option v-for="(label, code) in KNOWLEDGE_DOC_TYPE" :key="code" :label="label" :value="code" />
        </el-select>
        <el-button type="primary" @click="load">
          <el-icon><Search /></el-icon>&nbsp;查询
        </el-button>
        <el-button v-permission="'ai:knowledge:create'" type="primary" plain @click="openCreate">
          <el-icon><Plus /></el-icon>&nbsp;新建文档
        </el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        height="calc(100vh - 260px)"
        highlight-current-row
        @row-click="selectDoc"
      >
        <el-table-column prop="docName" label="文档名" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ labelOf(KNOWLEDGE_DOC_TYPE, row.docType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键词" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ labelOf(KNOWLEDGE_DOC_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'ai:knowledge:update'" link type="primary" @click.stop="openEdit(row)">
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
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
    </div>

    <!-- 右侧：文档详情 + SOP 问答 -->
    <div class="right-panel">
      <el-card v-if="current" shadow="never">
        <template #header>
          <div class="card-head">
            <span class="doc-title">{{ current.docName }}</span>
            <div class="doc-meta">
              <el-tag size="small" effect="plain">{{ labelOf(KNOWLEDGE_DOC_TYPE, current.docType) }}</el-tag>
              <el-tag size="small" :type="current.status === 'ENABLED' ? 'success' : 'info'">
                {{ labelOf(KNOWLEDGE_DOC_STATUS, current.status) }}
              </el-tag>
            </div>
          </div>
        </template>
        <div class="doc-keywords">关键词：{{ current.keywords || '-' }}</div>
        <pre class="doc-content">{{ current.content }}</pre>
      </el-card>
      <el-empty v-else description="点击左侧文档查看内容" />

      <el-card shadow="never" class="ask-card">
        <template #header><span class="card-title">SOP 问答</span></template>
        <el-input
          v-model="question"
          placeholder="例如：烧录时报 BURN_FAIL 怎么处理？"
          maxlength="500"
          @keyup.enter.exact="handleAsk"
        />
        <el-button type="primary" :loading="asking" style="margin-top: 8px; width: 100%" @click="handleAsk">
          <el-icon><ChatDotRound /></el-icon>&nbsp;提问
        </el-button>
        <template v-if="askResult">
          <el-alert
            v-if="askResult.fallback"
            title="AI 服务暂不可用，已返回模板回答"
            type="warning"
            :closable="false"
            style="margin-top: 10px"
          />
          <div class="ask-answer">{{ askResult.answer }}</div>
          <div v-if="askResult.references.length" class="refs">
            <span class="refs-label">参考文档：</span>
            <el-tag v-for="r in askResult.references" :key="r.docId" size="small" effect="plain">{{ r.docName }}</el-tag>
          </div>
          <div v-if="askResult.recordId" class="feedback">
            <el-button link type="primary" size="small" @click="handleFeedback(true)">有用</el-button>
            <el-button link type="info" size="small" @click="handleFeedback(false)">无用</el-button>
            <span v-if="feedbackSent" class="feedback-done">已反馈，感谢！</span>
          </div>
        </template>
      </el-card>
    </div>

    <!-- 新建/编辑文档弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑文档' : '新建文档'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="文档名" prop="docName">
          <el-input v-model="form.docName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" prop="docType">
          <el-select v-model="form.docType" style="width: 100%">
            <el-option v-for="(label, code) in KNOWLEDGE_DOC_TYPE" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" prop="keywords">
          <el-input v-model="form.keywords" maxlength="500" show-word-limit placeholder="逗号分隔，用于检索召回，如：烧录,软件烧录,BURN_FAIL" />
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, code) in KNOWLEDGE_DOC_STATUS" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="用 ## 二级标题分段落，检索按段落召回" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="255" />
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
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { knowledgeApi } from '@/api'
import { streamPost } from '@/api/sse'
import type { AiAskResult, KnowledgeDoc, KnowledgeDocQuery, KnowledgeDocSave } from '@/api/types'
import { KNOWLEDGE_DOC_STATUS, KNOWLEDGE_DOC_TYPE, labelOf } from '@/constants/dict'

const loading = ref(false)
const asking = ref(false)
const saving = ref(false)
const rows = ref<KnowledgeDoc[]>([])
const total = ref('0')
const query = reactive<KnowledgeDocQuery>({ pageNum: 1, pageSize: 10, keyword: '', docType: '' })

const current = ref<KnowledgeDoc>()
const question = ref('')
const askResult = ref<AiAskResult>()
const feedbackSent = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await knowledgeApi.docsPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      docType: query.docType || undefined,
    })
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function selectDoc(row: KnowledgeDoc) {
  current.value = await knowledgeApi.docsDetail(row.id)
  question.value = ''
  askResult.value = undefined
  feedbackSent.value = false
}

async function handleAsk() {
  const q = question.value.trim()
  if (!q || asking.value) return
  asking.value = true
  askResult.value = { answer: '', references: [], fallback: false, recordId: undefined }
  feedbackSent.value = false
  let gotEvent = false

  // 流式问答：delta 逐块追加（打字机），done 回填引用/recordId；
  // 首事件前失败自动回退非流式接口（后端降级不白屏）
  await streamPost('/ai/knowledge/ask/stream', { question: q }, {
    onMeta: () => {
      gotEvent = true
    },
    onDelta: (content) => {
      gotEvent = true
      if (askResult.value) askResult.value.answer += content
    },
    onDone: (done) => {
      gotEvent = true
      if (!askResult.value) return
      askResult.value.answer = done.answer
      askResult.value.references = done.references ?? []
      askResult.value.fallback = done.fallback === true
      askResult.value.recordId = done.recordId
    },
    onError: (message) => {
      if (gotEvent) {
        ElMessage.error(message)
        return
      }
      fallbackAsk(q)
    },
  })
  asking.value = false
}

/** 首事件前失败回退非流式问答 */
async function fallbackAsk(q: string) {
  try {
    askResult.value = await knowledgeApi.ask(q)
  } catch {
    askResult.value = undefined
    ElMessage.error('问答失败，请稍后重试')
  }
}

async function handleFeedback(useful: boolean) {
  if (!askResult.value?.recordId || feedbackSent.value) return
  try {
    await knowledgeApi.feedback(askResult.value.recordId, useful)
    feedbackSent.value = true
  } catch {
    ElMessage.error('反馈提交失败')
  }
}

// ---------- 新建/编辑 ----------
const dialogVisible = ref(false)
const editing = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<KnowledgeDocSave>({ docName: '', docType: 'SOP', keywords: '', content: '', status: 'ENABLED', remark: '' })
const formRules: FormRules = {
  docName: [{ required: true, message: '文档名必填', trigger: 'blur' }],
  docType: [{ required: true, message: '类型必选', trigger: 'change' }],
  content: [{ required: true, message: '内容必填', trigger: 'blur' }],
}

function openCreate() {
  editing.value = false
  Object.assign(form, { docName: '', docType: 'SOP', keywords: '', content: '', status: 'ENABLED', remark: '' })
  dialogVisible.value = true
}

function openEdit(row: KnowledgeDoc) {
  editing.value = true
  Object.assign(form, {
    docName: row.docName, docType: row.docType, keywords: row.keywords,
    content: row.content, status: row.status, remark: row.remark ?? '',
  })
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editing.value && current.value) {
      await knowledgeApi.docsUpdate(current.value.id, { ...form })
      ElMessage.success('文档已更新')
    } else {
      await knowledgeApi.docsCreate({ ...form })
      ElMessage.success('文档已创建')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 校验失败/后端错误由拦截器提示
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.knowledge-page {
  display: flex;
  gap: 12px;
}

.left-panel {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #ebeef5;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.right-panel {
  width: 430px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.doc-title {
  font-weight: 600;
  font-size: 15px;
}

.doc-meta {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.doc-keywords {
  color: #909399;
  font-size: 12px;
  margin-bottom: 8px;
}

.doc-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.8;
  color: #303133;
  font-family: inherit;
  max-height: 300px;
  overflow-y: auto;
}

.ask-card {
  background: #fff;
}

.card-title {
  font-weight: 600;
}

.ask-answer {
  margin-top: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.8;
  background: #f4f6fb;
  border-radius: 6px;
  padding: 10px;
}

.refs {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.refs-label {
  color: #909399;
  font-size: 12px;
}

.feedback {
  margin-top: 8px;
}

.feedback-done {
  color: #67c23a;
  font-size: 12px;
  margin-left: 6px;
}
</style>
