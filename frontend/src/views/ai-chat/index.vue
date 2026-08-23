<template>
  <div class="chat-page">
    <!-- 对话区 -->
    <div ref="msgAreaRef" class="chat-area">
      <el-empty v-if="messages.length === 0" description="向 AI 助手提问，一句话了解工厂全局" />
      <div v-for="(msg, i) in messages" :key="i" class="msg-row" :class="msg.role">
        <div class="bubble">
          <template v-if="msg.role === 'ai'">
            <div v-if="msg.loading" class="thinking">
              <el-icon class="is-loading"><Loading /></el-icon>&nbsp;AI 思考中…
            </div>
            <template v-else>
              <div class="msg-head">
                <el-tag v-if="msg.intent" size="small" effect="dark" type="primary">
                  {{ labelOf(AI_INTENT, msg.intent) }}
                </el-tag>
                <el-tag v-if="msg.fallback" size="small" type="warning" effect="plain">模板回答（AI 降级）</el-tag>
              </div>
              <div class="answer">{{ msg.answer }}</div>
              <div v-if="msg.references && msg.references.length" class="refs">
                <span class="refs-label">参考文档：</span>
                <el-tag v-for="r in msg.references" :key="r.docId" size="small" effect="plain">{{ r.docName }}</el-tag>
              </div>
              <el-collapse v-if="msg.summary" class="summary-box">
                <el-collapse-item title="数据来源（实时统计）">
                  <pre class="summary-text">{{ msg.summary }}</pre>
                </el-collapse-item>
              </el-collapse>
              <div v-if="msg.recordId" class="feedback">
                <span class="feedback-label">回答有帮助吗？</span>
                <el-button link type="primary" size="small" @click="handleFeedback(msg, true)">
                  <el-icon><Select /></el-icon>&nbsp;有用
                </el-button>
                <el-button link type="info" size="small" @click="handleFeedback(msg, false)">
                  <el-icon><CloseBold /></el-icon>&nbsp;无用
                </el-button>
                <span v-if="msg.feedbackSent" class="feedback-done">已反馈，感谢！</span>
              </div>
            </template>
          </template>
          <template v-else>
            <div class="answer">{{ msg.answer }}</div>
          </template>
        </div>
      </div>
    </div>

    <!-- 快捷问题 -->
    <div class="quick-questions">
      <el-tag
        v-for="q in quickQuestions"
        :key="q"
        class="quick-tag"
        effect="plain"
        @click="send(q)"
      >{{ q }}</el-tag>
    </div>

    <!-- 输入区 -->
    <div class="input-bar">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        maxlength="500"
        show-word-limit
        placeholder="问产量、问 SOP、报异常单号、要日报……例如：现在工厂整体情况怎么样"
        @keydown.enter.exact.prevent="send(input)"
      />
      <el-button type="primary" :loading="sending" :disabled="!input.trim()" @click="send(input)">
        <el-icon><Promotion /></el-icon>&nbsp;发送
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiChatApi, knowledgeApi } from '@/api'
import type { AiChatResult } from '@/api/types'
import { AI_INTENT, labelOf } from '@/constants/dict'

/** 快捷问题（演示剧本高频入口） */
const quickQuestions = [
  '现在工厂整体情况怎么样',
  '软件烧录的SOP流程是什么',
  '黑屏故障怎么排查',
  '生成今天的生产日报',
]

interface ChatMsg {
  role: 'user' | 'ai'
  answer: string
  intent?: string
  references?: AiChatResult['references']
  summary?: string
  fallback?: boolean
  recordId?: string
  loading?: boolean
  feedbackSent?: boolean
}

const messages = ref<ChatMsg[]>([])
const input = ref('')
const sending = ref(false)
const msgAreaRef = ref<HTMLElement>()

onMounted(() => {
  messages.value.push({
    role: 'ai',
    answer: '你好，我是 SmartFactory MES 的 AI 助手。\n\n可以问我：工厂整体生产情况、SOP 操作流程、异常故障处理建议（带上异常单号如 EXP202608230001）、生成当天生产日报。',
  })
})

async function send(text: string) {
  const question = text.trim()
  if (!question || sending.value) return
  input.value = ''
  messages.value.push({ role: 'user', answer: question })
  messages.value.push({ role: 'ai', answer: '', loading: true })
  sending.value = true
  scrollToBottom()
  try {
    const res = await aiChatApi.chat(question)
    const last = messages.value[messages.value.length - 1]!
    last.loading = false
    last.answer = res.answer
    last.intent = res.intent
    last.references = res.references
    last.summary = res.summary
    last.fallback = res.fallback
    last.recordId = res.recordId
  } catch {
    const last = messages.value[messages.value.length - 1]!
    last.loading = false
    last.answer = '（网络异常，请稍后重试）'
    ElMessage.error('对话失败，请检查后端服务')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

async function handleFeedback(msg: ChatMsg, useful: boolean) {
  if (!msg.recordId || msg.feedbackSent) return
  try {
    await knowledgeApi.feedback(msg.recordId, useful)
    msg.feedbackSent = true
  } catch {
    ElMessage.error('反馈提交失败')
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = msgAreaRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chat-area {
  flex: 1;
  overflow-y: auto;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #ebeef5;
}

.msg-row {
  display: flex;
  margin-bottom: 12px;
}

.msg-row.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.7;
}

.msg-row.ai .bubble {
  background: #f4f6fb;
  border: 1px solid #e4e9f2;
}

.msg-row.user .bubble {
  background: #409eff;
  color: #fff;
}

.thinking {
  display: flex;
  align-items: center;
  color: #909399;
}

.msg-head {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
}

.answer {
  white-space: pre-wrap;
  word-break: break-word;
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

.summary-box {
  margin-top: 8px;
}

.summary-text {
  margin: 0;
  font-size: 12px;
  color: #606266;
  white-space: pre-wrap;
  font-family: inherit;
}

.feedback {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.feedback-label {
  color: #909399;
  font-size: 12px;
  margin-right: 4px;
}

.feedback-done {
  color: #67c23a;
  font-size: 12px;
  margin-left: 6px;
}

.quick-questions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.quick-tag {
  cursor: pointer;
}

.input-bar {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  background: #fff;
  border-radius: 8px;
  padding: 10px;
  border: 1px solid #ebeef5;
}

.input-bar .el-button {
  height: 56px;
}
</style>
