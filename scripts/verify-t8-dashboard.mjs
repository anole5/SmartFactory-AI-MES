// T8 生产看板聚合接口验证：summary/work-orders/quality/equipment 四端点 + 权限
// 注意：全局 Jackson Long→String，数值断言用 Number(...) 比较
// 运行：node scripts/verify-t8-dashboard.mjs（后端须已启动）

const BASE = 'http://localhost:8080/api';
let pass = 0, fail = 0;
const ok = (name, cond, extra = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'} ${name} ${extra}`);
  cond ? pass++ : fail++;
};

const call = async (method, path, body, token) => {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* 非 JSON */ }
  return { status: res.status, body: json };
};
const post = (p, b, t) => call('POST', p, b, t);
const get = (p, t) => call('GET', p, undefined, t);

const admin = await post('/auth/login', { username: 'admin', password: 'admin123' });
const adminToken = admin.body.data?.token;
const operator = await post('/auth/login', { username: 'operator', password: 'operator123' });
const operatorToken = operator.body.data?.token;
const planning = await post('/auth/login', { username: 'planning', password: 'planning123' });
const planningToken = planning.body.data?.token;
ok('admin/operator/planning 登录成功', !!adminToken && !!operatorToken && !!planningToken);

// 1. summary：数值合法 + 良率 null 或百分比 + 设备分布四状态
let equipmentTotal = 0;
{
  const r = await get('/dashboard/summary', adminToken);
  const s = r.body.data;
  ok('summary code=0', r.body.code === 0 && !!s, `status=${r.status}`);
  ok('今日产量/报工数/不良数 ≥0',
    Number(s.todayOutputQty) >= 0 && Number(s.todayReportCount) >= 0 && Number(s.todayDefectQty) >= 0,
    JSON.stringify({ out: s.todayOutputQty, rep: s.todayReportCount, def: s.todayDefectQty }));
  ok('今日良率 null 或 0-100 百分比',
    s.todayYieldRate === null || (typeof s.todayYieldRate === 'number' && s.todayYieldRate >= 0 && s.todayYieldRate <= 100),
    `rate=${s.todayYieldRate}`);
  ok('进行中工单数/未关闭异常数 ≥0',
    Number(s.inProgressWorkOrderCount) >= 0 && Number(s.openExceptionCount) >= 0,
    JSON.stringify({ ip: s.inProgressWorkOrderCount, ex: s.openExceptionCount }));
  const counts = s.equipmentStatusCounts ?? [];
  ok('设备状态分布四状态全量填充',
    counts.length === 4 && ['RUNNING', 'IDLE', 'STOPPED', 'MAINTENANCE'].every(st =>
      counts.some(c => c.status === st && Number(c.count) >= 0)),
    JSON.stringify(counts));
  equipmentTotal = counts.reduce((sum, c) => sum + Number(c.count), 0);
}

// 2. work-orders：数组 + 字段合法 + 进度百分比 0-100
{
  const r = await get('/dashboard/work-orders', adminToken);
  const list = r.body.data ?? [];
  ok('work-orders code=0 + 数组', r.body.code === 0 && Array.isArray(r.body.data), `got ${list.length}`);
  ok('工单进度字段合法（状态 RELEASED/IN_PROGRESS、进度 0-100）',
    list.every(w => ['RELEASED', 'IN_PROGRESS'].includes(w.status)
      && Number(w.planQty) > 0 && Number(w.progressPercent) >= 0 && Number(w.progressPercent) <= 100
      && !!w.workOrderNo),
    JSON.stringify(list[0] ?? {}));
}

// 3. quality：整体良率 + 工序良率非空 + 不良分布数组
{
  const r = await get('/dashboard/quality', adminToken);
  const q = r.body.data;
  ok('quality code=0', r.body.code === 0 && !!q, `status=${r.status}`);
  ok('整体良率 null 或 0-100', q.overallYieldRate === null
    || (typeof q.overallYieldRate === 'number' && q.overallYieldRate >= 0 && q.overallYieldRate <= 100),
    `rate=${q.overallYieldRate}`);
  const byProcess = q.processYields ?? [];
  ok('工序良率非空 + 字段完整',
    byProcess.length >= 1 && byProcess.every(p => !!p.processName && Number(p.goodQty) + Number(p.defectQty) > 0),
    JSON.stringify(byProcess.slice(0, 3)));
  const dist = q.defectDistribution ?? [];
  ok('不良分布数组 + 编码/数量字段', Array.isArray(dist) && dist.length >= 1
    && dist.every(d => !!d.defectCode && Number(d.count) > 0),
    JSON.stringify(dist.slice(0, 3)));
}

// 4. equipment：列表 ≥10 + 分布合计 = 列表总数
{
  const r = await get('/dashboard/equipment', adminToken);
  const eq = r.body.data;
  const list = eq?.equipment ?? [];
  ok('equipment code=0 + 列表 ≥10 行', r.body.code === 0 && list.length >= 10, `got ${list.length}`);
  ok('设备行字段完整（编码/名称/状态/工位名）',
    list.every(e => !!e.equipmentCode && !!e.equipmentName && !!e.status && !!e.workstationName),
    JSON.stringify(list[0] ?? {}));
  const counts = eq?.statusCounts ?? [];
  const sum = counts.reduce((a, c) => a + Number(c.count), 0);
  ok('状态分布合计 = 设备总数', sum === list.length && sum === equipmentTotal,
    `distSum=${sum} list=${list.length} summaryDistSum=${equipmentTotal}`);
}

// 5. 权限：operator/planning 有 production:dashboard:query（菜单 205）可查
{
  const r1 = await get('/dashboard/summary', operatorToken);
  ok('operator 查看板汇总 200', r1.status === 200 && r1.body.code === 0, `status=${r1.status}`);
  const r2 = await get('/dashboard/quality', planningToken);
  ok('planning 查看板质量 200', r2.status === 200 && r2.body.code === 0, `status=${r2.status}`);
}

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
