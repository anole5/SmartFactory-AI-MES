// T7-4 前端流式契约验证：用与 frontend/src/api/sse.ts 同款解析逻辑重放 4 个流式端点，
// 断言前端依赖的协议事实（事件序 / answer===delta 拼接 / done 字段 / 401 错误体 / 停止按钮静默）。
// 前置：后端已重启（T2 代码在线）；前端 npm run build 已通过（vue-tsc 类型检查）
// 运行：node scripts/verify-t7-4-sse-contract.mjs（仓库根目录）
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
 * sse.ts streamPost 的同款解析逻辑（对照 frontend/src/api/sse.ts 逐段对齐）：
 * 按 \n\n 分块 → event:/data: 行解析 → flush 分发 meta/delta/done/error（未知事件忽略）
 * → EOF 无 done 且非 abort 视为"连接中断"走 onError → abort 静默。
 * 返回 { doneReceived, metas, deltas, done, errors }。
 */
async function streamPost(path, body, token, { abortAfterFirstDelta = false } = {}) {
  const controller = new AbortController()
  const result = { doneReceived: false, metas: [], deltas: [], done: null, errors: [] }
  let res
  try {
    res = await fetch(BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify(body),
      signal: controller.signal,
    })
  } catch {
    if (controller.signal.aborted) return { ...result, aborted: true } // 停止按钮：静默
    result.errors.push('网络异常，请检查后端服务')
    return result
  }
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    let message = `请求失败（HTTP ${res.status}）`
    try {
      const parsed = JSON.parse(text)
      if (parsed?.message) message = parsed.message
    } catch { /* 非 JSON 错误体 */ }
    result.errors.push(message)
    return result
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = 'message'
  const dataLines = []

  const flush = () => {
    const data = dataLines.join('\n').trim()
    dataLines.length = 0
    const eventName = currentEvent
    currentEvent = 'message'
    if (!data) return
    let parsed = null
    try { parsed = JSON.parse(data) } catch { return }
    if (!parsed) return
    if (eventName === 'meta') result.metas.push(parsed)
    else if (eventName === 'delta') result.deltas.push(String(parsed.content ?? ''))
    else if (eventName === 'done') { result.doneReceived = true; result.done = parsed }
    else if (eventName === 'error') result.errors.push(String(parsed.message ?? 'AI 服务异常'))
  }

  // 与 sse.ts 一致：读取循环整体 try/catch，abort 后 read() 拒绝走静默分支
  try {
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
      if (abortAfterFirstDelta && result.deltas.length >= 1) {
        // 模拟前端停止按钮：第一块 delta 到达后断开。
        // 浏览器中 abort() 恒不抛错；Node/undici 可能同步抛 AbortError——两者都视为已中止
        try {
          controller.abort()
        } catch { /* Node/undici 同步抛 AbortError，视为已中止 */ }
      }
    }
    if (!result.doneReceived && !controller.signal.aborted) {
      result.errors.push('连接中断，请重试')
    }
  } catch {
    if (controller.signal.aborted) return { ...result, aborted: true } // abort：静默结束
    result.errors.push('流式读取中断，请重试')
  }
  return result
}

const today = new Date().toISOString().slice(0, 10)
const admin = await login('admin', 'admin123')
const operator = await login('operator', 'operator123')
ok('登录成功', !!(admin && operator))

/** 契约断言（前端渲染依赖的事实） */
function assertContract(name, r, extra = {}) {
  const joined = r.deltas.join('')
  ok(`${name} doneReceived=true 且无 error`, r.doneReceived && r.errors.length === 0,
    `errors=${JSON.stringify(r.errors)}`)
  ok(`${name} 首事件 meta 且 intent=${extra.intent}`, r.metas[0]?.intent === extra.intent,
    `meta=${JSON.stringify(r.metas[0])}`)
  ok(`${name} done.answer === 全部 delta 拼接（打字机渲染正确性）`,
    !!r.done?.answer && r.done.answer === joined,
    `answerLen=${r.done?.answer?.length} joinedLen=${joined.length}`)
  for (const key of extra.doneKeys ?? []) {
    ok(`${name} done.${key} 存在`, r.done?.[key] !== undefined && r.done?.[key] !== null && r.done?.[key] !== '',
      `value=${JSON.stringify(r.done?.[key])?.slice(0, 60)}`)
  }
}

// ------------------------------------------------------------
// 1. 四端点契约重放
// ------------------------------------------------------------
const r1 = await streamPost('/ai/chat/stream', { question: '今天的生产日报' }, operator)
assertContract('chat/stream', r1, { intent: 'REPORT', doneKeys: ['recordId', 'summary'] })

const r2 = await streamPost('/ai/knowledge/ask/stream', { question: '烧录失败怎么处理' }, operator)
assertContract('knowledge/ask/stream', r2, { intent: 'KNOWLEDGE', doneKeys: ['recordId'] })

let exceptionId = null
{
  const create = await json(BASE + '/quality/exceptions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${admin}` },
    body: JSON.stringify({ description: 'T7-4 契约验证：功能测试声音异常', defectCode: 'NO_SOUND' }),
  })
  exceptionId = create.body?.data
}
const r3 = await streamPost('/ai/assistant/suggest/stream', { exceptionId }, operator)
assertContract('assistant/suggest/stream', r3, { intent: 'EXCEPTION', doneKeys: ['exceptionNo'] })

const r4 = await streamPost('/ai/daily/preview/stream', { reportDate: today }, operator)
assertContract('daily/preview/stream', r4, { intent: 'REPORT', doneKeys: ['summary', 'reportDate'] })

// ------------------------------------------------------------
// 2. 401 错误体：onError 收到后端 message（前端回退非流式的判据）
// ------------------------------------------------------------
{
  const r = await streamPost('/ai/chat/stream', { question: '你好' }, null)
  ok('无 token：未收到 done 且 onError 带后端 message',
    !r.doneReceived && r.errors.length === 1 && r.errors[0].length > 0 && !r.errors[0].includes('HTTP 401'),
    `errors=${JSON.stringify(r.errors)}`)
}

// ------------------------------------------------------------
// 3. 停止按钮语义：第一块 delta 后 abort → 静默结束，不触发 error/EOF 提示
// ------------------------------------------------------------
{
  const r = await streamPost('/ai/chat/stream', { question: '现在的产能情况如何' }, operator,
    { abortAfterFirstDelta: true })
  ok('中途 abort：静默结束（errors=0，无 EOF"连接中断"误报）',
    r.aborted === true && r.errors.length === 0,
    `aborted=${r.aborted} errors=${JSON.stringify(r.errors)} deltas=${r.deltas.length}`)
}

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
