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
      <!-- 流式生成中显示停止按钮：断开 SSE，AI 侧随即中止生成 -->
      <el-button v-if="streaming" type="danger" @click="stop">
        <el-icon><VideoPause /></el-icon>&nbsp;停止
      </el-button>
      <el-button v-else type="primary" :loading="sending" :disabled="!input.trim()" @click="send(input)">
        <el-icon><Promotion /></el-icon>&nbsp;发送
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiChatApi, knowledgeApi } from '@/api'
import { streamPost } from '@/api/sse'
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
const streaming = ref(false)
const msgAreaRef = ref<HTMLElement>()
let abortController: AbortController | null = null

onMounted(() => {
  messages.value.push({
    role: 'ai',
    answer: '你好，我是 SmartFactory MES 的 AI 助手。\n\n可以问我：工厂整体生产情况、SOP 操作流程、异常故障处理建议（带上异常单号如 EXP202608230001）、生成当天生产日报。',
  })
})

async function send(text: string) {
  const question = text.trim()
  if (!question || sending.value || streaming.value) return
  input.value = ''
  messages.value.push({ role: 'user', answer: question })
  const aiMsg: ChatMsg = { role: 'ai', answer: '', loading: true }
  messages.value.push(aiMsg)
  sending.value = true
  streaming.value = true
  abortController = new AbortController()
  let gotEvent = false
  scrollToBottom()

  // 流式链路：meta{intent 先行} → delta 逐块追加（打字机）→ done 回填引用/recordId。
  // 首事件前失败（后端未起/网络断）自动回退非流式接口，保证不白屏。
  await streamPost('/ai/chat/stream', { question }, {
    onMeta: (meta) => {
      gotEvent = true
      aiMsg.intent = String(meta.intent ?? '')
    },
    onDelta: (content) => {
      gotEvent = true
      aiMsg.loading = false
      aiMsg.answer += content
      scrollToBottom()
    },
    onDone: (done) => {
      gotEvent = true
      aiMsg.loading = false
      aiMsg.answer = done.answer
      aiMsg.intent = done.intent ?? aiMsg.intent
      aiMsg.references = done.references
      aiMsg.summary = done.summary
      aiMsg.fallback = done.fallback
      aiMsg.recordId = done.recordId
    },
    onError: (message) => {
      if (gotEvent) {
        // 流中途失败：正文已出一部分，只提示不覆盖
        aiMsg.loading = false
        ElMessage.error(message)
        return
      }
      fallbackToNonStream(question, aiMsg)
    },
  }, abortController.signal)

  sending.value = false
  streaming.value = false
  abortController = null
  scrollToBottom()
}

/** 首事件前失败回退非流式接口（与第 4 周一致的一次性渲染） */
async function fallbackToNonStream(question: string, aiMsg: ChatMsg) {
  try {
    const res = await aiChatApi.chat(question)
    aiMsg.loading = false
    aiMsg.answer = res.answer
    aiMsg.intent = res.intent
    aiMsg.references = res.references
    aiMsg.summary = res.summary
    aiMsg.fallback = res.fallback
    aiMsg.recordId = res.recordId
  } catch {
    aiMsg.loading = false
    aiMsg.answer = '（网络异常，请稍后重试）'
    ElMessage.error('对话失败，请检查后端服务')
  } finally {
    scrollToBottom()
  }
}

/** 停止生成：断开 SSE 连接，后端 sink 取消并跳过落库 */
function stop() {
  abortController?.abort()
  abortController = null
  streaming.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.role === 'ai') {
    last.loading = false
    if (!last.answer) last.answer = '（已停止生成）'
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
