// T6-5 报表中心契约验证：GET /production/reports-center/summary + /export
// 场景：5 台工单全链报工（13 道 × 5 合格，0 不良）→ 日/周/月三粒度聚合 + Excel 导出
// 断言：
// 1) day 汇总增量精确（合格 +5 / 报工数 +13 / 不良 +0），良率与汇总数一致
// 2) day 明细含来料检验工序行（goodQty>=5，工序分组生效）
// 3) week 窗口=本周一 00:00 起 7 天、rows 含今天日期行；month 窗口=自然月、rows 含今天
// 4) type 非法 → 400
// 5) export 200 + Content-Type spreadsheetml + PK 魔数 + filename*=UTF-8''
// 6) operator summary/export 403、qa export 403
// 运行：node scripts/verify-t6-5-report.mjs（后端须在跑）

const BASE = 'http://localhost:8080/api'
const DAY = '2026-08-23'
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

const callRaw = async (path, token) => {
  const res = await fetch(BASE + path, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  return { status: res.status, headers: res.headers, buf: Buffer.from(await res.arrayBuffer()) }
}

const admin = await login('admin', 'admin123')
const planning = await login('planning', 'planning123')
const operator = await login('operator', 'operator123')
const qa = await login('qa', 'qa123')
ok('四角色登录成功',
  !!admin.data?.token && !!planning.data?.token && !!operator.data?.token && !!qa.data?.token)

// —— 基线 + 5 台工单全链报工 ——
const before = await call('GET', `/production/reports-center/summary?type=day&date=${DAY}`, planning.data.token)
ok('day 汇总基线 200', before.status === 200, `status=${before.status}`)
const b = before.body.data

const woRes = await call('POST', '/production/work-orders', planning.data.token, {
  productId: 1, planQty: 5, priority: 'NORMAL', remark: 'T5 报表验证',
})
const woId = woRes.body.data
await call('POST', `/production/work-orders/${woId}/release`, planning.data.token, {})
const tasks = (await call('GET', `/production/work-orders/${woId}`, planning.data.token)).body.data.tasks
let chainOk = tasks.length === 13
for (const t of tasks) {
  const asg = await call('PUT', `/production/tasks/${t.id}/assign`, admin.data.token, { operatorId: 2 })
  const st = await call('PUT', `/production/tasks/${t.id}/start`, operator.data.token, {})
  const rp = await call('POST', '/production/reports', operator.data.token,
    { taskId: t.id, reportQty: 5, goodQty: 5, defectQty: 0 })
  if (asg.status !== 200 || st.status !== 200 || rp.status !== 200) { chainOk = false }
}
ok('5 台工单全链报工（13 道 × 5 合格）', chainOk)

// —— day 增量精确 ——
const after = await call('GET', `/production/reports-center/summary?type=day&date=${DAY}`, planning.data.token)
const a = after.body.data
ok('day 汇总增量精确：合格 +65（13 道×5）/ 报工数 +13 / 不良 +0',
  Number(a.totalGoodQty) === Number(b.totalGoodQty) + 65
    && Number(a.reportCount) === Number(b.reportCount) + 13
    && Number(a.totalDefectQty) === Number(b.totalDefectQty),
  `before good=${b.totalGoodQty} after=${a.totalGoodQty}; reports ${b.reportCount}->${a.reportCount}`)
const total = Number(a.totalGoodQty) + Number(a.totalDefectQty)
const expYield = total === 0 ? 0 : Math.round(Number(a.totalGoodQty) * 10000 / total) / 100
ok('day 良率与汇总数一致（保留 2 位）', Number(a.yieldRate) === expYield, `yieldRate=${a.yieldRate} exp=${expYield}`)
ok('day 明细含来料检验工序行且合格>=5（工序分组生效）',
  (a.rows ?? []).some(r => r.groupKey === '来料检验' && Number(r.goodQty) >= 5),
  `rows=${(a.rows ?? []).map(r => `${r.groupKey}:${r.goodQty}`).join(',')}`)

// —— week / month 窗口与含当天 ——
const wk = (await call('GET', `/production/reports-center/summary?type=week&date=${DAY}`, planning.data.token)).body.data
ok('week 窗口 = 本周一 00:00 起 7 天（2026-08-17 ~ 08-24）',
  wk.rangeStart === '2026-08-17 00:00:00' && wk.rangeEnd === '2026-08-24 00:00:00',
  `start=${wk.rangeStart} end=${wk.rangeEnd}`)
ok('week 明细含今天日期行且合格>=5',
  (wk.rows ?? []).some(r => r.groupKey === DAY && Number(r.goodQty) >= 5),
  `rows=${(wk.rows ?? []).map(r => r.groupKey).join(',')}`)
const mo = (await call('GET', `/production/reports-center/summary?type=month&date=${DAY}`, planning.data.token)).body.data
ok('month 窗口 = 自然月（08-01 ~ 09-01）且明细含今天',
  mo.rangeStart === '2026-08-01 00:00:00' && mo.rangeEnd === '2026-09-01 00:00:00'
    && (mo.rows ?? []).some(r => r.groupKey === DAY),
  `start=${mo.rangeStart} end=${mo.rangeEnd} rows=${(mo.rows ?? []).map(r => r.groupKey).join(',')}`)
ok('week/month 总量 >= day 总量（聚合口径一致性）',
  Number(wk.totalGoodQty) >= Number(a.totalGoodQty) && Number(mo.totalGoodQty) >= Number(a.totalGoodQty),
  `day=${a.totalGoodQty} week=${wk.totalGoodQty} month=${mo.totalGoodQty}`)

const bad = await call('GET', '/production/reports-center/summary?type=xxx', planning.data.token)
ok('type 非法 → 400', bad.status === 400, `status=${bad.status} msg=${bad.body?.message}`)

// —— 导出（裸文件流，不包 ApiResult）——
const x = await callRaw(`/production/reports-center/export?type=day&date=${DAY}`, planning.data.token)
ok('export 200 + Content-Type spreadsheetml',
  x.status === 200 && (x.headers.get('content-type') ?? '').includes('spreadsheetml'),
  `status=${x.status} ct=${x.headers.get('content-type')}`)
ok('export 文件名 Content-Disposition filename*=UTF-8',
  (x.headers.get('content-disposition') ?? '').includes("filename*=UTF-8''"),
  x.headers.get('content-disposition'))
ok('export 文件体 PK 魔数（xlsx = zip 容器）',
  x.buf.length > 100 && x.buf[0] === 0x50 && x.buf[1] === 0x4B, `bytes=${x.buf.length}`)

// —— 权限边界 ——
const opSum = await call('GET', '/production/reports-center/summary?type=day', operator.data.token)
ok('operator 查汇总 → 403（report:center:query 仅 admin/planning）', opSum.status === 403, `status=${opSum.status}`)
const opExp = await callRaw('/production/reports-center/export?type=day', operator.data.token)
ok('operator 导出 → 403', opExp.status === 403, `status=${opExp.status}`)
const qaExp = await callRaw('/production/reports-center/export?type=day', qa.data.token)
ok('qa 导出 → 403', qaExp.status === 403, `status=${qaExp.status}`)

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
