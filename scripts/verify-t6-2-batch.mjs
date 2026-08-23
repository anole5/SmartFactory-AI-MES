// T6-2 物料批次主数据契约验证：GET /production/material-batches/page + POST /production/material-batches
// 1) 种子 12 批分页可见（total>=12，remainingQty = batchQty - usedQty 展示口径）
// 2) materialId=1 过滤：仅料 1 批次（种子 2 批）
// 3) admin 创建：batchNo 生成器 = MB+12 位流水，快照/usedQty=0/剩余量正确，total 各 +1
// 4) 物料不存在 409；operator 创建 403、列表 200（复用 trace:query）；planning 创建 200 且批次号不与 admin 重复
// 运行：node scripts/verify-t6-2-batch.mjs（后端须在跑）

const BASE = 'http://localhost:8080/api'
let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'} ${name} ${extra}`)
  cond ? pass++ : fail++
}

const login = async (username, password) => {
  const res = await fetch(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  return res.json()
}

const call = async (method, path, token, body) => {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { status: res.status, body: await res.json() }
}

const admin = await login('admin', 'admin123')
const planning = await login('planning', 'planning123')
const operator = await login('operator', 'operator123')
ok('admin/planning/operator 登录成功',
  !!admin.data?.token && !!planning.data?.token && !!operator.data?.token)

// —— 分页可见性 ——
const pageAll = await call('GET', '/production/material-batches/page?pageNum=1&pageSize=100', admin.data.token)
ok('批次分页 total>=12（种子 12 批）',
  pageAll.status === 200 && Number(pageAll.body.data.total) >= 12,
  `total=${pageAll.body.data.total}`)
const r0 = pageAll.body.data.records?.[0] ?? {}
ok('VO 剩余量口径 remainingQty = batchQty - usedQty',
  Number(r0.remainingQty) === Number(r0.batchQty) - Number(r0.usedQty),
  `batch=${r0.batchNo} batchQty=${r0.batchQty} usedQty=${r0.usedQty} remaining=${r0.remainingQty}`)

const m1Before = await call('GET', '/production/material-batches/page?pageNum=1&pageSize=100&materialId=1', admin.data.token)
ok('materialId=1 过滤：total>=2 且全为料 1',
  m1Before.status === 200 && Number(m1Before.body.data.total) >= 2
    && m1Before.body.data.records.every(r => String(r.materialId) === '1'),
  `total=${m1Before.body.data.total}`)

// —— admin 创建（生成器 MB 前缀）——
const created = await call('POST', '/production/material-batches', admin.data.token, {
  materialId: 1, batchQty: 100, supplier: '验证供应商', remark: 'T2 验证',
})
ok('admin 创建批次 200 且返回 id', created.status === 200 && !!created.body.data, `id=${created.body.data}`)

const allAfter = await call('GET', '/production/material-batches/page?pageNum=1&pageSize=100', admin.data.token)
ok('创建后分页 total = 原 + 1（落库生效）',
  Number(allAfter.body.data.total) === Number(pageAll.body.data.total) + 1,
  `before=${pageAll.body.data.total} after=${allAfter.body.data.total}`)
const newBatch = allAfter.body.data.records.find(r => String(r.id) === String(created.body.data))
ok('新批 batchNo=MB+12 位流水、快照/usedQty=0/剩余=100',
  !!newBatch && /^MB\d{12}$/.test(String(newBatch.batchNo))
    && newBatch.materialCodeSnapshot === 'PNL-LCD-55-4K'
    && Number(newBatch.usedQty) === 0 && Number(newBatch.remainingQty) === 100,
  newBatch ? `batchNo=${newBatch.batchNo}` : 'not found')

const m1After = await call('GET', '/production/material-batches/page?pageNum=1&pageSize=100&materialId=1', admin.data.token)
ok('materialId=1 过滤 total = 原 + 1',
  Number(m1After.body.data.total) === Number(m1Before.body.data.total) + 1,
  `before=${m1Before.body.data.total} after=${m1After.body.data.total}`)

// —— 校验与权限边界 ——
const notFound = await call('POST', '/production/material-batches', admin.data.token, {
  materialId: 99999, batchQty: 10,
})
ok('物料不存在 → 409', notFound.status === 409, `status=${notFound.status} msg=${notFound.body?.message}`)

const opCreate = await call('POST', '/production/material-batches', operator.data.token, {
  materialId: 1, batchQty: 10,
})
ok('operator 创建 → 403（无 material-batch:create）', opCreate.status === 403, `status=${opCreate.status}`)
const opPage = await call('GET', '/production/material-batches/page?pageNum=1&pageSize=5', operator.data.token)
ok('operator 列表 → 200（复用 production:trace:query）', opPage.status === 200, `status=${opPage.status}`)

const pCreate = await call('POST', '/production/material-batches', planning.data.token, {
  materialId: 2, batchQty: 50,
})
ok('planning 创建批次 → 200', pCreate.status === 200, `status=${pCreate.status}`)
const m2After = await call('GET', '/production/material-batches/page?pageNum=1&pageSize=100&materialId=2', planning.data.token)
const pBatch = m2After.body.data.records.find(r => String(r.id) === String(pCreate.body.data))
ok('planning 批次号 MB 流水且与 admin 不重复（唯一键生效）',
  !!pBatch && /^MB\d{12}$/.test(String(pBatch.batchNo)) && pBatch.batchNo !== newBatch.batchNo,
  pBatch ? `batchNo=${pBatch.batchNo}` : 'not found')

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
