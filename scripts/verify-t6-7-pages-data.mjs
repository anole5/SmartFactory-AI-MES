// T6-7 前端两页数据契约验证（无头替代浏览器截图冒烟的自动化部分）：
// 用调度页/报表页完全相同的请求形状（GET gantt?date=、summary?type=&date=、POST schedule/run）
// 校验两页渲染所依赖的字段全部存在且类型正确；页面视觉冒烟（横道/红色逾期/导出落盘）
// 由用户在 npm run dev 下按 README 演示路径复核。
// 场景：2 台工单下发（不派工不报工）→ planning 执行排程 → gantt 今日 26 条
// → 同工位串行不重叠 → 远期 0 条（切日期）→ isOverdue 与时间公式一致
// → summary 三粒度字段契约 → operator run 403 → 取消两单收尾
// 运行：node scripts/verify-t6-7-pages-data.mjs（后端须在跑）

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

// 本地时间串（后端 LocalDateTime 序列化为 "yyyy-MM-dd HH:mm:ss" 本地时区，勿用 UTC 的 toISOString）
const pad2 = (n) => String(n).padStart(2, '0')
const _now = new Date()
const nowStr = `${_now.getFullYear()}-${pad2(_now.getMonth() + 1)}-${pad2(_now.getDate())} ${pad2(_now.getHours())}:${pad2(_now.getMinutes())}:${pad2(_now.getSeconds())}`

const admin = await login('admin', 'admin123')
const planning = await login('planning', 'planning123')
const operator = await login('operator', 'operator123')
ok('三角色登录成功', !!admin.data?.token && !!planning.data?.token && !!operator.data?.token)

// —— 建单 ×2 + 下发（不派工/开工/报工，模拟待排程工单） ——
const woIds = []
for (const remark of ['T7 页面验证-1', 'T7 页面验证-2']) {
  const woRes = await call('POST', '/production/work-orders', planning.data.token, {
    productId: 1, planQty: 1, priority: 'NORMAL', remark,
  })
  if (woRes.status !== 200) { ok(`建单 200（${remark}）`, false, `status=${woRes.status}`) }
  const id = woRes.body.data
  woIds.push(id)
  const rel = await call('POST', `/production/work-orders/${id}/release`, planning.data.token, {})
  ok(`下发 200（${remark}）`, rel.status === 200, `status=${rel.status}`)
}
ok('两单创建并下发成功', woIds.length === 2, `ids=${woIds.join(',')}`)

// —— 执行排程 ——
const runRes = await call('POST', '/production/schedule/run', planning.data.token, {})
const run = runRes.body.data
ok('planning 执行排程 200 且覆盖 26 道任务（13×2 单）',
  runRes.status === 200 && run && Number(run.taskCount) === 26 && Number(run.workOrderCount) >= 2,
  `taskCount=${run?.taskCount} workOrderCount=${run?.workOrderCount}`)
const opRun = await call('POST', '/production/schedule/run', operator.data.token, {})
ok('operator 执行排程 403（页面 v-permission 同步隐藏按钮）', opRun.status === 403, `status=${opRun.status}`)

// —— gantt 今日：本两单 26 条 + 页面依赖字段 + 不重叠 ——
// 注意：DB 可能有历史验证残留（取消工单保留旧计划列，后端口径「完成/取消任务保留旧值不重算」），
// 断言范围收敛到本次两单（T8 干净重放后全量一致）
const DAY = `${_now.getFullYear()}-${pad2(_now.getMonth() + 1)}-${pad2(_now.getDate())}`
const g = await call('GET', `/production/schedule/gantt?date=${DAY}`, planning.data.token)
const gantt = g.body.data ?? []
const mine = gantt.filter(t => woIds.includes(String(t.workOrderId)))
ok('gantt 今日含本两单 26 条（每单 13 道全部入排）',
  g.status === 200 && mine.length === 26, `mine=${mine.length} total=${gantt.length}`)
const fieldOk = mine.every(t =>
  t.taskId && t.taskNo && t.workOrderId && t.workOrderNo && t.sequenceNo >= 1
    && t.planStartTime && t.planEndTime && t.planQty >= 1 && typeof t.isOverdue === 'boolean'
    && typeof t.priority === 'string' && typeof t.status === 'string')
ok('gantt 行字段契约（页面渲染依赖字段齐全）', fieldOk,
  fieldOk ? '' : JSON.stringify(mine[0]))
ok('gantt 覆盖本两单 2 个工单（图例数据源）', new Set(mine.map(t => t.workOrderId)).size === 2, '')

// 同工位串行：按工位分组排序后，前一任务计划完工 <= 后一任务计划开工（横道视觉不重叠）
const wsGroups = new Map()
for (const t of mine) {
  const key = t.workstationName || '未分配工位'
  if (!wsGroups.has(key)) wsGroups.set(key, [])
  wsGroups.get(key).push(t)
}
const serial = [...wsGroups.values()].every(group => {
  const sorted = [...group].sort((a, b) => (a.planStartTime < b.planStartTime ? -1 : 1))
  return sorted.every((t, i) => i === 0 || sorted[i - 1].planEndTime <= t.planStartTime)
})
ok('同工位任务串行不重叠（含跨工单）', serial,
  `工位组=${wsGroups.size}`)

// isOverdue 与时间公式一致（红色横道数据源；PENDING 任务无完成态豁免）
const overdueOk = mine.every(t => t.isOverdue === (t.planEndTime < nowStr))
ok('isOverdue 与计划完工时间公式一致（红色横道数据源）', overdueOk,
  `overdue=${mine.filter(t => t.isOverdue).length}/${mine.length}`)

// —— 切日期：远期 0 条 ——
const far = await call('GET', '/production/schedule/gantt?date=2099-01-01', planning.data.token)
ok('gantt 远期日期 0 条（日期切换契约）', far.status === 200 && (far.body.data ?? []).length === 0,
  `len=${(far.body.data ?? []).length}`)

// —— 报表中心三粒度字段契约 ——
for (const [type, label] of [['day', '日'], ['week', '周'], ['month', '月']]) {
  const s = await call('GET', `/production/reports-center/summary?type=${type}&date=${DAY}`, planning.data.token)
  const d = s.body.data
  const rowsOk = Array.isArray(d?.rows) && d.rows.every(r =>
    r.groupKey && typeof r.goodQty === 'number' && typeof r.defectQty === 'number'
      && typeof r.reportCount === 'number' && typeof r.workOrderCount === 'number')
  const headOk = d && ['totalGoodQty', 'totalDefectQty', 'yieldRate', 'reportCount', 'workOrderCount',
    'rangeStart', 'rangeEnd', 'date'].every(k => d[k] !== undefined)
  ok(`${label}报字段契约（汇总卡片+明细表渲染依赖）`,
    s.status === 200 && headOk && rowsOk,
    `rows=${d?.rows?.length} range=${d?.rangeStart}~${d?.rangeEnd}`)
}

// —— 收尾：取消两单（不污染 T8 冒烟） ——
let cancelOk = true
for (const id of woIds) {
  const c = await call('PUT', `/production/work-orders/${id}/cancel`, planning.data.token, {})
  if (c.status !== 200) cancelOk = false
}
ok('取消两单收尾 200', cancelOk, '')

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
