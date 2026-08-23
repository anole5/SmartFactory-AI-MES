// T7-2 SSE 流式后端验证：4 端点事件契约 / 记录落库 / created_by / 权限 / 旧接口回归
// 前置：后端已重启（含 T2 代码）
// 运行：node scripts/verify-t7-2-stream.mjs（仓库根目录）
import { execSync } from 'child_process'

const BASE = 'http://localhost:8080/api'

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'} ${name} ${extra}`)
  cond ? pass++ : fail++
}

const json = async (url, init) => {
  const res = await fetch(url, init)
  return { status: res.status, body: await res.json().catch(() => null) }
}

const login = async (username, password) => {
  const r = await json(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  return r.body?.data?.token
}

/**
 * SSE 流式读取（与前端 sse.ts 同款解析语义）：
 * 按 \n\n 分块 → event:/data: 行解析 → meta/delta/done/error 分拣，order 记录事件序。
 * 返回 { status, contentType, events }；非 200 时 events=null 并附 text。
 */
async function streamEvents(path, body, token, timeoutMs = 150000) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify(body),
      signal: controller.signal,
    })
    const contentType = res.headers.get('content-type') ?? ''
    if (res.status !== 200 || !res.body) {
      return { status: res.status, contentType, events: null, text: await res.text().catch(() => '') }
    }
    const events = { meta: [], delta: [], done: null, error: [], order: [] }
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = 'message'
    let dataLines = []
    const flush = () => {
      const data = dataLines.join('\n').trim()
      if (data && ['meta', 'delta', 'done', 'error'].includes(currentEvent)) {
        let parsed = null
        try { parsed = JSON.parse(data) } catch { /* 非 JSON 忽略 */ }
        events.order.push(currentEvent)
        if (currentEvent === 'meta') events.meta.push(parsed)
        else if (currentEvent === 'delta') events.delta.push(parsed)
        else if (currentEvent === 'done') events.done = parsed
        else events.error.push(parsed)
      }
      currentEvent = 'message'
      dataLines = []
    }
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) currentEvent = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
        }
        flush()
      }
    }
    return { status: res.status, contentType, events }
  } finally {
    clearTimeout(timer)
  }
}

/** 事件契约断言（事件序 + 答案拼接一致），返回 delta 拼接串 */
function assertContract(name, r, { expectMetaIntent, expectDoneIntent, doneExtras = [] }) {
  const ev = r.events
  ok(`${name} 200 + text/event-stream`,
    r.status === 200 && (r.contentType ?? '').includes('text/event-stream'),
    `status=${r.status} ct=${r.contentType}`)
  if (!ev) return ''
  const joined = (ev.delta ?? []).map(d => d?.content ?? '').join('')
  ok(`${name} 事件序 meta -> delta* -> done`,
    ev.order.length >= 3 && ev.order[0] === 'meta' && ev.order[ev.order.length - 1] === 'done'
      && ev.order.slice(1, -1).every(x => x === 'delta'),
    `order=${ev.order.join(',')}`)
  ok(`${name} meta.intent=${expectMetaIntent}`, ev.meta[0]?.intent === expectMetaIntent,
    `intent=${ev.meta[0]?.intent}`)
  ok(`${name} ≥1 delta 且 done.answer === delta 拼接`,
    ev.delta.length >= 1 && !!ev.done?.answer && ev.done.answer === joined,
    `deltas=${ev.delta.length} answerLen=${ev.done?.answer?.length} joinedLen=${joined.length}`)
  ok(`${name} delta 无 null 分块污染（推理中间分块被后端过滤）`,
    ev.delta.every(d => typeof d?.content === 'string' && d.content.length > 0 && d.content !== 'null'),
    `污染数=${ev.delta.filter(d => !d?.content || d.content === 'null').length}`)
  ok(`${name} done.intent=${expectDoneIntent}`, ev.done?.intent === expectDoneIntent,
    `intent=${ev.done?.intent}`)
  for (const key of doneExtras) {
    ok(`${name} done.${key} 存在`, ev.done?.[key] !== undefined && ev.done?.[key] !== null && ev.done?.[key] !== '',
      `value=${JSON.stringify(ev.done?.[key])?.slice(0, 60)}`)
  }
  ok(`${name} 无 error 事件`, ev.error.length === 0, JSON.stringify(ev.error).slice(0, 120))
  return joined
}

const today = new Date().toISOString().slice(0, 10)

// ------------------------------------------------------------
// 0. 登录
// ------------------------------------------------------------
const admin = await login('admin', 'admin123')
const operator = await login('operator', 'operator123')
const planning = await login('planning', 'planning123')
const qa = await login('qa', 'qa123')
ok('admin/operator/planning/qa 登录成功', !!(admin && operator && planning && qa))

// ------------------------------------------------------------
// 1. /ai/chat/stream：REPORT 意图（规则命中，免 LLM 分类波动）+ 落库
// ------------------------------------------------------------
const r1 = await streamEvents('/ai/chat/stream', { question: '今天的生产日报' }, operator)
assertContract('chat/stream[REPORT]', r1, {
  expectMetaIntent: 'REPORT', expectDoneIntent: 'REPORT',
  doneExtras: ['recordId', 'summary', 'reportDate'],
})
ok('chat/stream[REPORT] done.fallback=false', r1.events?.done?.fallback === false,
  `fallback=${r1.events?.done?.fallback}`)
const reportRecordId = r1.events?.done?.recordId

// ------------------------------------------------------------
// 2. /ai/chat/stream：EXCEPTION 规则命中但无单号 -> 知识库兜底（intent 归 KNOWLEDGE）
// ------------------------------------------------------------
const r2 = await streamEvents('/ai/chat/stream', { question: '烧录失败怎么处理' }, operator)
assertContract('chat/stream[EXCEPTION->KNOWLEDGE]', r2, {
  expectMetaIntent: 'EXCEPTION', expectDoneIntent: 'KNOWLEDGE',
  doneExtras: ['recordId'],
})
ok('chat/stream[EXCEPTION->KNOWLEDGE] done.references 命中烧录文档',
  Array.isArray(r2.events?.done?.references) && r2.events.done.references.length > 0
    && r2.events.done.references.some(x => x?.docName?.includes('烧录')),
  JSON.stringify(r2.events?.done?.references)?.slice(0, 120))

// ------------------------------------------------------------
// 3. /ai/knowledge/ask/stream：直接知识库流式问答
// ------------------------------------------------------------
const r3 = await streamEvents('/ai/knowledge/ask/stream', { question: '烧录失败怎么处理' }, operator)
assertContract('knowledge/ask/stream', r3, {
  expectMetaIntent: 'KNOWLEDGE', expectDoneIntent: 'KNOWLEDGE',
  doneExtras: ['recordId'],
})
ok('knowledge/ask/stream done.references 非空',
  Array.isArray(r3.events?.done?.references) && r3.events.done.references.length > 0,
  JSON.stringify(r3.events?.done?.references)?.slice(0, 120))

// ------------------------------------------------------------
// 4. /ai/assistant/suggest/stream：手工异常单 + pro 流式建议
// ------------------------------------------------------------
let exceptionId = null
{
  const create = await json(BASE + '/quality/exceptions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${admin}` },
    body: JSON.stringify({ description: 'T7-2 流式验证：老化过程花屏', defectCode: 'FLOWER_SCREEN' }),
  })
  ok('手工创建异常单成功', create.status === 200 && create.body?.code === 0 && !!create.body?.data,
    `status=${create.status} body=${JSON.stringify(create.body)?.slice(0, 120)}`)
  exceptionId = create.body?.data
}
const r4 = await streamEvents('/ai/assistant/suggest/stream', { exceptionId }, operator)
assertContract('assistant/suggest/stream', r4, {
  expectMetaIntent: 'EXCEPTION', expectDoneIntent: 'EXCEPTION',
  doneExtras: ['exceptionId', 'exceptionNo'],
})
ok('suggest/stream meta.exceptionId 与入参一致',
  r4.events?.meta[0]?.exceptionId === exceptionId,
  `meta=${r4.events?.meta[0]?.exceptionId} expect=${exceptionId}`)
ok('suggest/stream 不落问答记录（done 无 recordId）', r4.events?.done?.recordId === undefined,
  JSON.stringify(r4.events?.done)?.slice(0, 120))

// ------------------------------------------------------------
// 5. /ai/daily/preview/stream：日报流式预览
// ------------------------------------------------------------
const r5 = await streamEvents('/ai/daily/preview/stream', { reportDate: today }, operator)
assertContract('daily/preview/stream', r5, {
  expectMetaIntent: 'REPORT', expectDoneIntent: 'REPORT',
  doneExtras: ['summary', 'reportDate'],
})
ok('daily/preview/stream meta.reportDate 与入参一致',
  r5.events?.meta[0]?.reportDate === today,
  `meta=${r5.events?.meta[0]?.reportDate} expect=${today}`)

// ------------------------------------------------------------
// 6. 坑 1 断言：流式问答记录 created_by = 发起人（异步线程恢复 CurrentUserContext）
// ------------------------------------------------------------
if (reportRecordId != null) {
  const out = execSync(
    `docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes -e "SELECT created_by FROM mes_ai_qa_record WHERE id = ${reportRecordId}"`,
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
  ).trim()
  const createdBy = out.split('\n').pop()?.trim()
  // operator 用户种子 id=2（sql/01-seed 固定）
  ok('流式落库 created_by=2（operator，非 0）', createdBy === '2', `created_by=${createdBy}`)
}

// ------------------------------------------------------------
// 7. 权限边界：operator 200 / 无 token 401
// ------------------------------------------------------------
{
  const rop = await streamEvents('/ai/chat/stream', { question: '烧录失败怎么处理' }, operator)
  ok('operator chat/stream 200', rop.status === 200, `status=${rop.status}`)
  const rnone = await streamEvents('/ai/chat/stream', { question: '你好' }, null)
  ok('无 token chat/stream 401', rnone.status === 401, `status=${rnone.status}`)
  const rbad = await streamEvents('/ai/chat/stream', { question: '你好' }, 'invalid-token')
  ok('伪 token chat/stream 401', rbad.status === 401, `status=${rbad.status}`)
}

// ------------------------------------------------------------
// 8. 旧非流式接口回归（4 端点全 200）
// ------------------------------------------------------------
{
  const c1 = await json(BASE + '/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${operator}` },
    body: JSON.stringify({ question: '烧录失败怎么处理' }),
  })
  ok('旧 /ai/chat 回归 200', c1.status === 200 && c1.body?.code === 0, `status=${c1.status}`)

  const c2 = await json(BASE + '/ai/knowledge/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${operator}` },
    body: JSON.stringify({ question: '烧录失败怎么处理' }),
  })
  ok('旧 /ai/knowledge/ask 回归 200', c2.status === 200 && c2.body?.code === 0, `status=${c2.status}`)

  const c3 = await json(BASE + '/ai/assistant/suggest', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${operator}` },
    body: JSON.stringify({ exceptionId }),
  })
  ok('旧 /ai/assistant/suggest 回归 200', c3.status === 200 && c3.body?.code === 0, `status=${c3.status}`)

  const c4 = await json(BASE + '/ai/daily/preview', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${operator}` },
    body: JSON.stringify({ reportDate: today }),
  })
  ok('旧 /ai/daily/preview 回归 200', c4.status === 200 && c4.body?.code === 0, `status=${c4.status}`)
}

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
