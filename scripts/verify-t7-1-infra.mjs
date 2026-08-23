// T7-1 基础设施验证：TEI embedding / Qdrant 读写 / DeepSeek 原始流探测 / 后端回归
// 前置：qdrant 6333、TEI 8081 在线；后端已重启（带新增 bean）
// 运行：node scripts/verify-t7-1-infra.mjs（仓库根目录）
import fs from 'fs'
import { fileURLToPath } from 'url'
import path from 'path'

const TEI = 'http://localhost:8081'
const QD = 'http://localhost:6333'
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

// ------------------------------------------------------------
// 1. TEI embedding：1024 维（bge-large-zh-v1.5）
// ------------------------------------------------------------
{
  const r = await json(TEI + '/embed', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ inputs: ['烧录失败怎么处理'] }),
  })
  const vecs = Array.isArray(r.body) ? r.body : r.body?.embeddings
  ok('TEI /embed 200 且返回 1024 维向量',
    r.status === 200 && Array.isArray(vecs) && vecs.length === 1 && vecs[0].length === 1024,
    `status=${r.status} dims=${vecs?.[0]?.length}`)
}

// ------------------------------------------------------------
// 2. Qdrant：在线 + 既有课程集合只读不动
// ------------------------------------------------------------
{
  const r = await json(QD + '/collections')
  const names = (r.body?.result?.collections ?? []).map(c => c.name)
  ok('qdrant 在线且既有课程集合在场',
    r.status === 200 && names.includes('data-agent-column') && names.includes('data-agent-metric'),
    `collections=${names.join(',')}`)
  ok('未触碰既有集合（只读列表）', names.length >= 2, `count=${names.length}`)
}

// ------------------------------------------------------------
// 3. Qdrant 临时读写探针：建集合 -> upsert -> search -> 删集合（自建自清）
// ------------------------------------------------------------
{
  const PROBE = 'verify-t7-1-probe'
  await json(QD + `/collections/${PROBE}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ vectors: { size: 4, distance: 'Cosine' } }),
  })
  const up = await json(QD + `/collections/${PROBE}/points?wait=true`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      points: [{ id: 'c7e9b1a0-0000-4000-8000-000000000001', vector: [1, 0, 0, 0],
        payload: { doc_id: 1, doc_name: 'probe' } }],
    }),
  })
  ok('临时集合 upsert 200', up.status === 200, `status=${up.status}`)

  const s = await json(QD + `/collections/${PROBE}/points/search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ vector: [1, 0, 0, 0], limit: 5, with_payload: true }),
  })
  ok('临时集合 search 命中且 payload 原样返回',
    s.status === 200 && s.body?.result?.length === 1 && s.body.result[0].score > 0.99
      && s.body.result[0].payload?.doc_name === 'probe',
    `hits=${s.body?.result?.length} score=${s.body?.result?.[0]?.score}`)

  await json(QD + `/collections/${PROBE}`, { method: 'DELETE' })
  const after = await json(QD + '/collections')
  const names = (after.body?.result?.collections ?? []).map(c => c.name)
  ok('临时集合已删除（不残留）', !names.includes(PROBE), `collections=${names.join(',')}`)
}

// ------------------------------------------------------------
// 4. DeepSeek 原始流探测（key 从 gitignored application-local.yml 读取，仅本机）
// ------------------------------------------------------------
{
  const here = path.dirname(fileURLToPath(import.meta.url))
  const localYml = fs.readFileSync(path.join(here, '..', 'backend', 'src', 'main', 'resources', 'application-local.yml'), 'utf8')
  const m = localYml.match(/api-key:\s*(\S+)/)
  ok('application-local.yml 存在且含 api-key', !!m?.[1], m?.[1] ? 'key=***' : '未匹配')
  const res = await fetch('https://api.deepseek.com/chat/completions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${m?.[1] ?? ''}` },
    body: JSON.stringify({
      model: 'deepseek-v4-flash',
      messages: [{ role: 'user', content: '用一句话说"你好"' }],
      max_tokens: 50,
      stream: true,
    }),
  })
  const text = await res.text()
  const deltas = [...text.matchAll(/^data:\s*(.*)$/gm)]
    .map(x => x[1])
    .filter(d => d !== '[DONE]')
    .map(d => { try { return JSON.parse(d)?.choices?.[0]?.delta?.content ?? '' } catch { return '' } })
  ok('DeepSeek stream 原始探测 ≥1 delta 且以 [DONE] 收尾',
    res.status === 200 && deltas.join('').length > 0 && text.includes('[DONE]'),
    `status=${res.status} chunks=${deltas.length} text=${deltas.join('').slice(0, 20)}`)
}

// ------------------------------------------------------------
// 5. 后端回归：4 角色登录 + 旧非流式 ask 正常（新增 bean 不影响既有链路）
// ------------------------------------------------------------
{
  const login = async (username, password) => {
    const r = await json(BASE + '/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    return r.body?.data?.token
  }
  const admin = await login('admin', 'admin123')
  const operator = await login('operator', 'operator123')
  const planning = await login('planning', 'planning123')
  const qa = await login('qa', 'qa123')
  ok('admin/operator/planning/qa 登录成功', !!(admin && operator && planning && qa))

  const ask = await json(BASE + '/ai/knowledge/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${operator}` },
    body: JSON.stringify({ question: '烧录失败怎么处理' }),
  })
  ok('旧非流式 ask 回归 200', ask.status === 200 && ask.body?.code === 0, `status=${ask.status}`)
}

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
