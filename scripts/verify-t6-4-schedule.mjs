// T6-4 生产排程契约验证：POST /production/schedule/run + GET /production/schedule/gantt
// 场景：HIGH（2 台，planStart 10:00）+ NORMAL（1 台，planStart 09:00）两单建单+下发（不做派工/开工/报工）
// 断言：
// 1) run 前 26 任务计划时间全空、当日 gantt 无本脚本任务（未 run 空）
// 2) operator run/gantt 403（schedule:run 仅 admin/planning）
// 3) planning run 200（taskCount>=26，含库中其他活跃工单）
// 4) run 后 26 任务计划时间落库：同工位 HIGH 先于 NORMAL、组内串行不重叠(end<=next start)
// 5) AGING 时长 = 120 分钟 × planQty（A=240 / B=120）
// 6) 当日 gantt 含本脚本 26 任务且字段回填（工单号/工位名/优先级/状态）
// 7) 远期日期 gantt 不含本脚本任务（窗口交集口径）
// 8) 重跑 run 计划时间完全一致（覆盖即幂等）
// 9) 收尾：两单取消（级联任务 CANCELLED，不残留活跃工单）
// 运行：node scripts/verify-t6-4-schedule.mjs（后端须在跑）

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

const admin = await login('admin', 'admin123')
const planning = await login('planning', 'planning123')
const operator = await login('operator', 'operator123')
ok('三角色登录成功', !!admin.data?.token && !!planning.data?.token && !!operator.data?.token)

// —— 建单 + 下发（不派工/开工/报工）——
const mk = async (priority, planQty, planStartTime) => {
  const wo = await call('POST', '/production/work-orders', planning.data.token, {
    productId: 1, planQty, priority, planStartTime,
    planEndTime: '2026-08-23 18:00:00', remark: `T4 排程验证 ${priority}`,
  })
  await call('POST', `/production/work-orders/${wo.body.data}/release`, planning.data.token, {})
  const detail = (await call('GET', `/production/work-orders/${wo.body.data}`, planning.data.token)).body.data
  return detail
}
const woA = await mk('HIGH', 2, '2026-08-23 10:00:00')
const woB = await mk('NORMAL', 1, '2026-08-23 09:00:00')
const tasksA = woA?.tasks ?? []
const tasksB = woB?.tasks ?? []
const myIds = [...tasksA, ...tasksB].map(t => String(t.id))
ok('HIGH(2台)+NORMAL(1台) 两单建单+下发 26 任务', tasksA.length === 13 && tasksB.length === 13, `A=${tasksA.length} B=${tasksB.length}`)
ok('run 前 26 任务计划时间全空',
  [...tasksA, ...tasksB].every(t => !t.planStartTime && !t.planEndTime))

const g0 = await call('GET', `/production/schedule/gantt?date=${DAY}`, planning.data.token)
ok('run 前当日 gantt 不含本脚本任务',
  g0.status === 200 && !(g0.body.data ?? []).some(t => myIds.includes(String(t.taskId))),
  `gantt rows=${g0.body.data?.length}`)

// —— 权限边界 ——
const opRun = await call('POST', '/production/schedule/run', operator.data.token, {})
ok('operator 执行排程 → 403', opRun.status === 403, `status=${opRun.status}`)
const opGantt = await call('GET', `/production/schedule/gantt?date=${DAY}`, operator.data.token)
ok('operator 查甘特图 → 403（schedule:query 仅 admin/planning）', opGantt.status === 403, `status=${opGantt.status}`)

// —— 执行排程 ——
const r1 = await call('POST', '/production/schedule/run', planning.data.token, {})
ok('planning 执行排程 200 且 taskCount>=26（含库中其他活跃工单）',
  r1.status === 200 && Number(r1.body.data?.taskCount) >= 26,
  `woCount=${r1.body.data?.workOrderCount} taskCount=${r1.body.data?.taskCount} runAt=${r1.body.data?.runAt}`)

const afterA = (await call('GET', `/production/work-orders/${woA.id}`, planning.data.token)).body.data.tasks
const afterB = (await call('GET', `/production/work-orders/${woB.id}`, planning.data.token)).body.data.tasks
const all = [...afterA, ...afterB]
ok('run 后 26 任务计划时间全部落库', all.every(t => !!t.planStartTime && !!t.planEndTime))

// 同工位：HIGH 先于 NORMAL（两单同产品同路线 → 同工位序列一一对应）
const aFirst = afterA.every((ta) => {
  const tb = afterB.find(b => String(b.workstationId) === String(ta.workstationId))
  return !tb || new Date(ta.planStartTime) < new Date(tb.planStartTime)
})
ok('同工位 HIGH 先于 NORMAL', aFirst, '')

// 组内串行不重叠：两单 26 任务按工位分组，start 排序后 end <= next start
const byWs = new Map()
for (const t of all) {
  const k = String(t.workstationId ?? 'NULL')
  if (!byWs.has(k)) byWs.set(k, [])
  byWs.get(k).push(t)
}
let noOverlap = true
for (const group of byWs.values()) {
  group.sort((x, y) => new Date(x.planStartTime) - new Date(y.planStartTime))
  for (let i = 1; i < group.length; i++) {
    if (new Date(group[i - 1].planEndTime) > new Date(group[i].planStartTime)) { noOverlap = false }
  }
}
ok('同工位任务串行不重叠（end <= next start）', noOverlap)

// AGING 时长 = 120 × planQty
const agingA = afterA.find(t => t.processCodeSnapshot === 'AGING')
const agingB = afterB.find(t => t.processCodeSnapshot === 'AGING')
const durMin = (t) => (new Date(t.planEndTime) - new Date(t.planStartTime)) / 60000
ok('AGING 时长 = 120×planQty（A=240min / B=120min）',
  !!agingA && !!agingB && durMin(agingA) === 240 && durMin(agingB) === 120,
  `A=${agingA ? durMin(agingA) : '?'} B=${agingB ? durMin(agingB) : '?'}`)

// —— gantt 数据 ——
const g1 = await call('GET', `/production/schedule/gantt?date=${DAY}`, planning.data.token)
const myGantt = (g1.body.data ?? []).filter(t => myIds.includes(String(t.taskId)))
ok('当日 gantt 含本脚本 26 任务', myGantt.length === 26, `got ${myGantt.length}`)
ok('gantt 行字段回填（工单号/工位名/优先级/状态/逾期标记）',
  myGantt.every(t => !!t.workOrderNo && !!t.workstationName && !!t.priority && !!t.status
    && typeof t.isOverdue === 'boolean'),
  JSON.stringify(myGantt[0] ?? {}))
const gFar = await call('GET', '/production/schedule/gantt?date=2026-09-01', planning.data.token)
ok('远期日期 gantt 不含本脚本任务（窗口交集口径）',
  !(gFar.body.data ?? []).some(t => myIds.includes(String(t.taskId))),
  `rows=${gFar.body.data?.length}`)

// —— 重跑幂等 ——
await call('POST', '/production/schedule/run', planning.data.token, {})
const after2A = (await call('GET', `/production/work-orders/${woA.id}`, planning.data.token)).body.data.tasks
const after2B = (await call('GET', `/production/work-orders/${woB.id}`, planning.data.token)).body.data.tasks
const sig = (ts) => ts.map(t => `${t.planStartTime}~${t.planEndTime}`).sort().join('|')
ok('重跑 run 计划时间完全一致（覆盖即幂等）',
  sig(afterA) === sig(after2A) && sig(afterB) === sig(after2B))

// —— 收尾：取消两单（级联任务，不残留活跃工单）——
const cA = await call('PUT', `/production/work-orders/${woA.id}/cancel`, planning.data.token, {})
const cB = await call('PUT', `/production/work-orders/${woB.id}/cancel`, planning.data.token, {})
ok('收尾：两单取消成功（级联 CANCELLED）', cA.status === 200 && cB.status === 200,
  `A=${cA.status} B=${cB.status}`)

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
