// T7-3 向量索引 + 双路召回验证：reindex / 语义探针 / 写路径向量同步 / 幂等 / 权限 / 流式回归
// 前置：qdrant 6333、TEI 8081 在线；后端已重启（含 T3 代码）
// 运行：node scripts/verify-t7-3-vector.mjs（仓库根目录）
import { execSync } from 'child_process'

const BASE = 'http://localhost:8080/api'
const QD = 'http://localhost:6333'
const COLLECTION = 'mes-knowledge-sections'

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

const admin = await login('admin', 'admin123')
const operator = await login('operator', 'operator123')
ok('admin/operator 登录成功', !!(admin && operator))

const ask = (question, token = operator) => json(BASE + '/ai/knowledge/ask', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  body: JSON.stringify({ question }),
})

// ------------------------------------------------------------
// 1. reindex：4 文档全量入库，返回 {docCount, sectionCount}
// ------------------------------------------------------------
let reindexVO = null
{
  const r = await json(BASE + '/ai/knowledge/reindex', {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin}` },
  })
  reindexVO = r.body?.data
  ok('reindex 200 且 {docCount=4, sectionCount≥4}',
    r.status === 200 && r.body?.code === 0
      && reindexVO?.docCount === 4 && reindexVO?.sectionCount >= 4,
    `status=${r.status} vo=${JSON.stringify(reindexVO)}`)
}

// ------------------------------------------------------------
// 2. Qdrant 直查：points_count 与 reindex 一致，data-agent-* 未触碰
// ------------------------------------------------------------
{
  const r = await json(QD + `/collections/${COLLECTION}`)
  const pointsCount = r.body?.result?.points_count
  ok('qdrant points_count === reindex.sectionCount',
    r.status === 200 && pointsCount === reindexVO?.sectionCount,
    `points=${pointsCount} sections=${reindexVO?.sectionCount}`)

  const all = await json(QD + '/collections')
  const names = (all.body?.result?.collections ?? []).map(c => c.name)
  ok('既有课程集合 data-agent-column/metric 仍在（未触碰）',
    names.includes('data-agent-column') && names.includes('data-agent-metric'),
    `collections=${names.join(',')}`)
}

// ------------------------------------------------------------
// 3. 语义探针：词面零重叠问法 → 向量通道命中预期文档（fallback=false）
// ------------------------------------------------------------
const probes = [
  { q: '程序写不进芯片，要怎么弄？', expect: '烧录' },
  { q: '开机后屏幕全暗，没有任何画面显示', expect: '黑屏' },
  { q: '长时间通电测试时机器自己重新启动', expect: '老化' },
  { q: '左右喇叭都不出声，电视是坏的？', expect: '功能测试' },
]
for (const p of probes) {
  const r = await ask(p.q)
  const refs = r.body?.data?.references ?? []
  ok(`语义探针「${p.q}」向量命中 ${p.expect} 文档且 fallback=false`,
    r.status === 200 && r.body?.code === 0 && r.body?.data?.fallback === false
      && refs.some(x => x?.docName?.includes(p.expect)),
    `refs=${JSON.stringify(refs)} fallback=${r.body?.data?.fallback}`)
}

// ------------------------------------------------------------
// 4. 关键词问法：references[0] 仍为烧录文档（关键词排序保持）
// ------------------------------------------------------------
{
  const r = await ask('烧录失败怎么处理')
  const refs = r.body?.data?.references ?? []
  ok('关键词问法 references[0] 含烧录（排序保持）',
    r.status === 200 && r.body?.code === 0 && refs.length > 0 && refs[0]?.docName?.includes('烧录'),
    `refs=${JSON.stringify(refs)}`)
}

// ------------------------------------------------------------
// 5. 写路径：create → 自动 upsert 向量点；update DISABLED → 点删除；SQL 清理测试文档
// ------------------------------------------------------------
let testDocId = null
{
  const create = await json(BASE + '/ai/knowledge/docs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${admin}` },
    body: JSON.stringify({
      docName: 'T7-3 向量同步验证文档',
      docType: 'FAULT_GUIDE',
      keywords: '钎焊,回焊炉,锡膏',
      content: '## 钎焊工艺验证\n钎焊工序使用回焊炉进行，锡膏印刷后过炉。\n## 常见问题\n锡膏印刷不良时先检查钢网张力。',
    }),
  })
  ok('create 测试文档成功', create.status === 200 && create.body?.code === 0 && !!create.body?.data,
    `status=${create.status} body=${JSON.stringify(create.body)?.slice(0, 120)}`)
  testDocId = create.body?.data

  // qdrant scroll 按 doc_id 过滤查点（Long 被后端序列化为字符串，此处转回数字与 payload 整数对齐）
  const scroll = await json(QD + `/collections/${COLLECTION}/points/scroll`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      filter: { must: [{ key: 'doc_id', match: { value: Number(testDocId) } }] },
      limit: 20,
    }),
  })
  const pts = scroll.body?.result?.points ?? []
  ok('create 后 qdrant 出现该文档向量点（自动 upsert）', pts.length >= 1,
    `points=${pts.length}`)

  // update → DISABLED：点应删除
  const disable = await json(BASE + `/ai/knowledge/docs/${testDocId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${admin}` },
    body: JSON.stringify({
      docName: 'T7-3 向量同步验证文档',
      docType: 'FAULT_GUIDE',
      keywords: '钎焊,回焊炉,锡膏',
      content: '## 钎焊工艺验证\n钎焊工序使用回焊炉进行，锡膏印刷后过炉。',
      status: 'DISABLED',
    }),
  })
  ok('update DISABLED 成功', disable.status === 200 && disable.body?.code === 0,
    `status=${disable.status}`)

  await new Promise(resolve => setTimeout(resolve, 1500)) // 等 qdrant 删除生效
  const scroll2 = await json(QD + `/collections/${COLLECTION}/points/scroll`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      filter: { must: [{ key: 'doc_id', match: { value: Number(testDocId) } }] },
      limit: 20,
    }),
  })
  const pts2 = scroll2.body?.result?.points ?? []
  ok('DISABLED 后向量点已删除', pts2.length === 0, `points=${pts2.length}`)
}

// ------------------------------------------------------------
// 6. reindex 幂等：重复调用点数不变
// ------------------------------------------------------------
{
  const r2 = await json(BASE + '/ai/knowledge/reindex', {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin}` },
  })
  ok('重复 reindex 幂等（docCount/sectionCount 一致）',
    r2.status === 200 && r2.body?.data?.docCount === reindexVO?.docCount
      && r2.body?.data?.sectionCount === reindexVO?.sectionCount,
    `vo=${JSON.stringify(r2.body?.data)}`)
  const r = await json(QD + `/collections/${COLLECTION}`)
  ok('重复 reindex 后 points_count 不变',
    r.body?.result?.points_count === reindexVO?.sectionCount,
    `points=${r.body?.result?.points_count}`)
}

// ------------------------------------------------------------
// 7. 权限：operator reindex 403；测试文档 SQL 清理
// ------------------------------------------------------------
{
  const rop = await json(BASE + '/ai/knowledge/reindex', {
    method: 'POST',
    headers: { Authorization: `Bearer ${operator}` },
  })
  ok('operator reindex -> 403', rop.status === 403, `status=${rop.status}`)

  if (testDocId != null) {
    execSync(
      `docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes -e "DELETE FROM mes_knowledge_doc WHERE id = ${testDocId}"`,
      { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
    )
    const check = await json(BASE + `/ai/knowledge/docs/${testDocId}`, {
      headers: { Authorization: `Bearer ${admin}` },
    })
    ok('测试文档已从库中清理', check.status === 200 && check.body?.code !== 0 || check.status !== 200,
      `status=${check.status} code=${check.body?.code}`)
  }
}

// ------------------------------------------------------------
// 8. ask/stream 回归：流式问答走双路召回仍契约完整
// ------------------------------------------------------------
{
  const controller = new AbortController()
  const res = await fetch(BASE + '/ai/knowledge/ask/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${operator}` },
    body: JSON.stringify({ question: '屏幕不亮了，开机没画面' }),
    signal: controller.signal,
  })
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buf = '', done = null
  const chunks = []
  while (true) {
    const { value, done: rd } = await reader.read()
    if (rd) break
    buf += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    let idx
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const block = buf.slice(0, idx)
      buf = buf.slice(idx + 2)
      let ev = ''
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) ev = line.slice(6).trim()
        else if (line.startsWith('data:') && ev === 'delta') chunks.push(JSON.parse(line.slice(5)).content ?? '')
        else if (line.startsWith('data:') && ev === 'done') done = JSON.parse(line.slice(5))
      }
    }
  }
  ok('ask/stream 语义问法：done.answer===delta 拼接且 references 命中黑屏文档',
    res.status === 200 && !!done && done.answer === chunks.join('')
      && Array.isArray(done.references) && done.references.some(x => x?.docName?.includes('黑屏')),
    `status=${res.status} refs=${JSON.stringify(done?.references)}`)
}

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
