// T8 报工验证：校验链负例（未开工/零数量/数量≠合格+不良/超额/已完成/后道超前道 409）
// + CAS 累计回写 + 达标自动结转 + 最后一道回写工单 COMPLETED
// + 报工记录/追溯时间线查询 + 工单详情报工统计 + 权限边界
// 运行：node scripts/verify-t8-report.mjs（后端须已启动）

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
  return { status: res.status, body: await res.json() };
};
const post = (p, b, t) => call('POST', p, b, t);
const put = (p, b, t) => call('PUT', p, b, t);
const get = (p, t) => call('GET', p, undefined, t);

const admin = await post('/auth/login', { username: 'admin', password: 'admin123' });
const adminToken = admin.body.data?.token;
const operator = await post('/auth/login', { username: 'operator', password: 'operator123' });
const operatorToken = operator.body.data?.token;
const planning = await post('/auth/login', { username: 'planning', password: 'planning123' });
const planningToken = planning.body.data?.token;

// 1. 建单 + 下发，取任务
const wo = await post('/production/work-orders', { productId: 1, planQty: 10 }, adminToken);
const woId = wo.body.data;
await post(`/production/work-orders/${woId}/release`, {}, adminToken);
const tasks = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data;
ok('前置：下发生成 13 任务', tasks?.length === 13);
const task1 = tasks[0], task2 = tasks[1];

// 2. 校验链负例：未开工 / 零数量 / 数量≠合格+不良
const r1 = await post('/production/reports', { taskId: task1.id, reportQty: 3, goodQty: 3, defectQty: 0 }, adminToken);
ok('未开工报工 409', r1.status === 409, JSON.stringify(r1.body).slice(0, 80));
const r2 = await post('/production/reports', { taskId: task1.id, reportQty: 0, goodQty: 0, defectQty: 0 }, adminToken);
ok('零数量报工 409', r2.status === 409, JSON.stringify(r2.body).slice(0, 80));

// 3. 派工 + 开工后再试数量不匹配
await put(`/production/tasks/${task1.id}/assign`, { operatorId: 2 }, adminToken);
await put(`/production/tasks/${task1.id}/start`, {}, adminToken);
const r3 = await post('/production/reports', { taskId: task1.id, reportQty: 5, goodQty: 3, defectQty: 1 }, adminToken);
ok('数量≠合格+不良 409', r3.status === 409, JSON.stringify(r3.body).slice(0, 80));

// 4. 正常报工 + 任务累计回写
const r4 = await post('/production/reports', { taskId: task1.id, reportQty: 5, goodQty: 4, defectQty: 1 }, adminToken);
ok('正常报工 code=0', r4.body.code === 0);
let t1 = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data[0];
ok('任务累计回写 5/4/1 仍 RUNNING',
  t1.completedQty === 5 && t1.goodQty === 4 && t1.defectQty === 1 && t1.status === 'RUNNING',
  `c=${t1.completedQty} g=${t1.goodQty} d=${t1.defectQty} s=${t1.status}`);

// 5. 超额报工 409 + 累计不被污染
const r5 = await post('/production/reports', { taskId: task1.id, reportQty: 6, goodQty: 4, defectQty: 2 }, adminToken);
ok('超额报工 409', r5.status === 409, JSON.stringify(r5.body).slice(0, 90));
t1 = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data[0];
ok('超额被拒后累计不变', t1.completedQty === 5, `c=${t1.completedQty}`);

// 6. 补足报工 → 任务达标自动 COMPLETED + end_time 回填
const r6 = await post('/production/reports', { taskId: task1.id, reportQty: 5, goodQty: 4, defectQty: 1 }, adminToken);
ok('补足报工 code=0', r6.body.code === 0);
t1 = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data[0];
ok('任务达标自动 COMPLETED + end_time 回填', t1.status === 'COMPLETED' && !!t1.endTime, `s=${t1.status}`);
ok('任务累计 good=8/defect=2', t1.goodQty === 8 && t1.defectQty === 2, `g=${t1.goodQty} d=${t1.defectQty}`);

// 7. 已完成任务再报工 409
const r7 = await post('/production/reports', { taskId: task1.id, reportQty: 1, goodQty: 1, defectQty: 0 }, adminToken);
ok('已完成任务报工 409', r7.status === 409);

// 8. 后道超前道校验：task2 合格 9 > 前道 8
await put(`/production/tasks/${task2.id}/assign`, { operatorId: 2 }, adminToken);
await put(`/production/tasks/${task2.id}/start`, {}, adminToken);
const r8 = await post('/production/reports', { taskId: task2.id, reportQty: 9, goodQty: 9, defectQty: 0 }, adminToken);
ok('后道合格 9 超前道 8 → 409', r8.status === 409, JSON.stringify(r8.body).slice(0, 90));

// 9. 后道合格 = 前道（边界值）通过
const r9 = await post('/production/reports', { taskId: task2.id, reportQty: 10, goodQty: 8, defectQty: 2 }, adminToken);
ok('后道合格=前道（边界）报工 code=0', r9.body.code === 0);
const t2 = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data[1];
ok('task2 达标 COMPLETED good=8', t2.status === 'COMPLETED' && t2.goodQty === 8, `s=${t2.status} g=${t2.goodQty}`);

// 10. 第 3..13 道全流程（派工→开工→报工 10 台：8 合格 2 不良）
let loopOk = true;
for (const t of tasks.slice(2)) {
  const a = await put(`/production/tasks/${t.id}/assign`, { operatorId: 2 }, adminToken);
  const s = await put(`/production/tasks/${t.id}/start`, {}, adminToken);
  const r = await post('/production/reports', { taskId: t.id, reportQty: 10, goodQty: 8, defectQty: 2 }, adminToken);
  if (a.body.code !== 0 || s.body.code !== 0 || r.body.code !== 0) loopOk = false;
}
ok('第 3..13 道全流程报工 code=0', loopOk);

// 11. 最后一道回写工单：COMPLETED + 数量 = 最后一道累计
const woDetail = await get(`/production/work-orders/${woId}`, adminToken);
ok('工单自动 COMPLETED', woDetail.body.data.status === 'COMPLETED', `s=${woDetail.body.data.status}`);
ok('工单回写 completed=10/good=8/defect=2',
  woDetail.body.data.completedQty === 10 && woDetail.body.data.goodQty === 8 && woDetail.body.data.defectQty === 2,
  `c=${woDetail.body.data.completedQty} g=${woDetail.body.data.goodQty} d=${woDetail.body.data.defectQty}`);
ok('工单 actual_end_time 回填', !!woDetail.body.data.actualEndTime);
ok('工单详情 reportCount=14', String(woDetail.body.data.reportCount) === '14', `n=${woDetail.body.data.reportCount}`);

// 12. 报工记录分页 + VO 回填
const reports = await get(`/production/reports/page?workOrderId=${woId}&pageSize=100`, adminToken);
ok('报工记录 total=14', String(reports.body.data.total) === '14', `total=${reports.body.data.total}`);
const firstReport = reports.body.data.records[0];
ok('报工记录回填工单号/任务号/工序/报工人',
  !!firstReport.workOrderNo && !!firstReport.taskNo && !!firstReport.processNameSnapshot && !!firstReport.operatorName,
  `${firstReport.workOrderNo}/${firstReport.taskNo}/${firstReport.processNameSnapshot}/${firstReport.operatorName}`);

// 13. 追溯时间线：REPORT 14 条 + CREATE/RELEASE + 操作人名回填
const traces = await get(`/production/traces?workOrderId=${woId}`, adminToken);
const reportTraces = traces.body.data.filter(t => t.actionType === 'REPORT');
ok('追溯 REPORT 14 条', reportTraces.length === 14, `n=${reportTraces.length}`);
ok('追溯含 CREATE/RELEASE', traces.body.data.some(t => t.actionType === 'CREATE') && traces.body.data.some(t => t.actionType === 'RELEASE'));
ok('追溯操作人名回填', traces.body.data.every(t => !!t.operatorName));

// 14. 权限边界：planning 无报工权限 403；operator 有权限（业务 409 而非 403）
const rp = await post('/production/reports', { taskId: task1.id, reportQty: 1, goodQty: 1, defectQty: 0 }, planningToken);
ok('planning 报工 403', rp.status === 403);
const ro = await post('/production/reports', { taskId: task1.id, reportQty: 1, goodQty: 1, defectQty: 0 }, operatorToken);
ok('operator 报工放行（业务 409 已完成）', ro.status === 409, JSON.stringify(ro.body).slice(0, 80));

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
console.log(`\nSQL 复核提示：
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes -e "SELECT COUNT(*) AS report_cnt FROM mes_work_report WHERE work_order_id=${woId}; SELECT status, completed_qty, good_qty, defect_qty, actual_end_time FROM mes_work_order WHERE id=${woId};"`);
process.exit(fail > 0 ? 1 : 0);
