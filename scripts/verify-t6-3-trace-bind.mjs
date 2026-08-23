// T6-3 关键件批次绑定与正反向追溯契约验证
// 场景：自建 1 台工单（planQty=1）13 道报工；首道内嵌绑定 6 种关键件批次，其余 12 道报工后
// 经补录接口绑定（双通道）；负例（批次不存在/物料不匹配/非关键件）打在报工事务上验证整单回滚
// 断言：
// 1) 负例报工 409 且报工/绑定/used_qty 零残留（事务回滚）
// 2) 补录幂等重放 200、同料换批 409
// 3) batch-sns 反向：绑定 13 行 / 工单 1 / SN 1 / usedQty=13
// 4) SN 正查 materialBatches 聚合去重 = 6 行（qtyUsed 求和 13）
// 5) 时间线 BATCH_BIND=78（13 道 × 6 料）
// 6) qa/planning 补录 403、operator 补录 200
// 运行：node scripts/verify-t6-3-trace-bind.mjs（后端须在跑）

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
const operator = await login('operator', 'operator123')
const planning = await login('planning', 'planning123')
const qa = await login('qa', 'qa123')
ok('四角色登录成功',
  !!admin.data?.token && !!operator.data?.token && !!planning.data?.token && !!qa.data?.token)

// 6 种关键件（trace_required=1）各取种子第一个批次
const KEY_ITEMS = [
  { materialId: 1, batchNo: 'MB202608230001' },
  { materialId: 2, batchNo: 'MB202608230003' },
  { materialId: 3, batchNo: 'MB202608230005' },
  { materialId: 4, batchNo: 'MB202608230007' },
  { materialId: 5, batchNo: 'MB202608230009' },
  { materialId: 20, batchNo: 'MB202608230011' },
]

// 非关键件负例用料 6：先由 admin 建一个料 6 批次（非关键件无种子批次）
const b6 = await call('POST', '/production/material-batches', admin.data.token,
  { materialId: 6, batchQty: 50, supplier: '验证供应商' })
const batchNo6 = b6.body.data
let m6 = await call('GET', `/production/material-batches/page?pageNum=1&pageSize=100&materialId=6`, admin.data.token)
const batch6 = (m6.body.data.records ?? []).find(r => String(r.id) === String(batchNo6))
ok('admin 建非关键件料 6 批次（负例素材）', b6.status === 200 && !!batch6, batch6?.batchNo)

// —— 建单 + 下发（1 台）——
const woRes = await call('POST', '/production/work-orders', planning.data.token, {
  productId: 1, planQty: 1, priority: 'NORMAL',
  planStartTime: '2026-08-23 09:00:00', planEndTime: '2026-08-23 18:00:00',
  remark: 'T3 批次绑定验证',
})
const woId = woRes.body.data
await call('POST', `/production/work-orders/${woId}/release`, planning.data.token, {})
const wo = (await call('GET', `/production/work-orders/${woId}`, planning.data.token)).body.data
const taskIds = (wo.tasks ?? []).map(t => t.id)
ok('建单+下发 13 道任务', taskIds.length === 13, `got ${taskIds.length}`)
for (const id of taskIds) {
  await call('PUT', `/production/tasks/${id}/assign`, admin.data.token, { operatorId: 2 })
}
await call('PUT', `/production/tasks/${taskIds[0]}/start`, operator.data.token, {})

// —— 负例：打在报工事务上（409 + 整单回滚零残留）——
const n1 = await call('POST', '/production/reports', operator.data.token,
  { taskId: taskIds[0], reportQty: 1, goodQty: 1, defectQty: 0,
    materialBatchBindings: [{ materialId: 1, batchNo: 'MB202609999999' }] })
ok('绑定批次不存在 → 409 且报工整单回滚', n1.status === 409, `status=${n1.status} msg=${n1.body?.message}`)

const n2 = await call('POST', '/production/reports', operator.data.token,
  { taskId: taskIds[0], reportQty: 1, goodQty: 1, defectQty: 0,
    materialBatchBindings: [{ materialId: 2, batchNo: 'MB202608230001' }] })
ok('批次与物料不匹配 → 409', n2.status === 409, `status=${n2.status} msg=${n2.body?.message}`)

const n3 = await call('POST', '/production/reports', operator.data.token,
  { taskId: taskIds[0], reportQty: 1, goodQty: 1, defectQty: 0,
    materialBatchBindings: [{ materialId: 6, batchNo: batch6.batchNo }] })
ok('非关键件绑定 → 409', n3.status === 409, `status=${n3.status} msg=${n3.body?.message}`)

const rp0 = await call('GET', `/production/reports/page?pageNum=1&pageSize=10&workOrderId=${woId}`, admin.data.token)
ok('负例后报工记录 0 条（事务回滚零残留）', Number(rp0.body.data.total) === 0, `total=${rp0.body.data.total}`)
const t1Fresh = (await call('GET', `/production/tasks/page?pageNum=1&pageSize=50`, admin.data.token)).body.data.records
  .find(t => String(t.id) === String(taskIds[0]))
ok('负例后 t1 仍 RUNNING 且累计 0', t1Fresh.status === 'RUNNING' && t1Fresh.completedQty === 0,
  `status=${t1Fresh.status} completed=${t1Fresh.completedQty}`)

// —— 正例：首道报工内嵌绑定 6 料 ——
const p1 = await call('POST', '/production/reports', operator.data.token,
  { taskId: taskIds[0], reportQty: 1, goodQty: 1, defectQty: 0, materialBatchBindings: KEY_ITEMS })
ok('首道报工内嵌绑定 6 关键件成功', p1.status === 200, `status=${p1.status}`)
const rp1 = await call('GET', `/production/reports/page?pageNum=1&pageSize=10&workOrderId=${woId}`, admin.data.token)
const report1Id = rp1.body.data.records[0]?.id
ok('首道报工落库（reportId 可取）', !!report1Id, `reportId=${report1Id}`)

// —— 补录通道：幂等重放 200 / 同料换批 409 ——
const idem = await call('POST', `/production/reports/${report1Id}/bind-batch`, operator.data.token,
  [{ materialId: 1, batchNo: 'MB202608230001' }])
ok('补录幂等重放（同报工同料同批）→ 200', idem.status === 200, `status=${idem.status}`)
const swap = await call('POST', `/production/reports/${report1Id}/bind-batch`, operator.data.token,
  [{ materialId: 1, batchNo: 'MB202608230002' }])
ok('补录同料换批 → 409', swap.status === 409, `status=${swap.status} msg=${swap.body?.message}`)

// —— t2..t13：报工不带绑定 → 补录 6 料（验证补录通道真正新增）——
let chainOk = true
for (let i = 1; i < 13; i++) {
  const st = await call('PUT', `/production/tasks/${taskIds[i]}/start`, operator.data.token, {})
  const rp = await call('POST', '/production/reports', operator.data.token,
    { taskId: taskIds[i], reportQty: 1, goodQty: 1, defectQty: 0 })
  if (st.status !== 200 || rp.status !== 200) { chainOk = false; break }
  const rpPage = await call('GET', `/production/reports/page?pageNum=1&pageSize=50&workOrderId=${woId}`, admin.data.token)
  const myReport = rpPage.body.data.records.find(r => String(r.taskId) === String(taskIds[i]))
  if (!myReport) { chainOk = false; break }
  const bind = await call('POST', `/production/reports/${myReport.id}/bind-batch`, operator.data.token, KEY_ITEMS)
  if (bind.status !== 200) { chainOk = false; break }
}
ok('t2..t13 报工 + 补录 6 料全部成功（补录通道新增 72 行）', chainOk, '')
const woDone = (await call('GET', `/production/work-orders/${woId}`, admin.data.token)).body.data
ok('工单 COMPLETED（1 台整机完成）', woDone.status === 'COMPLETED', `status=${woDone.status}`)

// —— 反向追溯：batch-sns ——
const rev = await call('GET', '/production/traces/batch-sns?batchNo=MB202608230001', admin.data.token)
ok('batch-sns 反向：绑定 13 行 / 工单 1 / SN 1 台',
  rev.status === 200 && (rev.body.data.bindings ?? []).length === 13
    && (rev.body.data.workOrders ?? []).length === 1
    && (rev.body.data.sns ?? []).length === 1,
  `bindings=${rev.body.data?.bindings?.length} wo=${rev.body.data?.workOrders?.length} sns=${rev.body.data?.sns?.length}`)
ok('batch-sns 批次台账：usedQty=13（13 道各耗 1 台）',
  Number(rev.body.data?.usedQty) === 13 && rev.body.data?.batchNo === 'MB202608230001',
  `usedQty=${rev.body.data?.usedQty}`)
const theSn = rev.body.data?.sns?.[0]?.sn
const revMiss = await call('GET', '/production/traces/batch-sns?batchNo=MB202609999999', admin.data.token)
ok('batch-sns 未知批次 → 404', revMiss.status === 404, `status=${revMiss.status}`)

// —— 正向追溯：SN 反查关键件批次聚合 ——
const fwd = await call('GET', `/production/traces/sn?sn=${theSn}`, admin.data.token)
const mbs = fwd.body.data?.materialBatches ?? []
ok('SN 正查 materialBatches 聚合去重 = 6 行（6 种关键件各 1 批）',
  mbs.length === 6, `got ${mbs.length}: ${mbs.map(m => m.materialCodeSnapshot).join(',')}`)
ok('SN 正查批次用量 qtyUsed 求和 = 13（13 道报工各耗 1 台）',
  mbs.every(m => Number(m.qtyUsed) === 13), JSON.stringify(mbs.map(m => `${m.batchNo}:${m.qtyUsed}`)))

// —— 追溯时间线：BATCH_BIND 计数 ——
const traces = (await call('GET', `/production/traces?workOrderId=${woId}`, admin.data.token)).body.data ?? []
const bindCount = traces.filter(t => t.actionType === 'BATCH_BIND').length
ok('时间线 BATCH_BIND = 78（13 道 × 6 料，重放幂等不重复写）', bindCount === 78, `got ${bindCount}`)

// —— 权限边界：qa/planning 补录 403、operator 200 ——
const qaBind = await call('POST', `/production/reports/${report1Id}/bind-batch`, qa.data.token,
  [{ materialId: 20, batchNo: 'MB202608230011' }])
ok('qa 补录 → 403', qaBind.status === 403, `status=${qaBind.status}`)
const plBind = await call('POST', `/production/reports/${report1Id}/bind-batch`, planning.data.token,
  [{ materialId: 20, batchNo: 'MB202608230011' }])
ok('planning 补录 → 403', plBind.status === 403, `status=${plBind.status}`)
const opBind = await call('POST', `/production/reports/${report1Id}/bind-batch`, operator.data.token,
  [{ materialId: 20, batchNo: 'MB202608230011' }])
ok('operator 补录（幂等重放）→ 200', opBind.status === 200, `status=${opBind.status}`)

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
