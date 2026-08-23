// T3 报工接入验证：质检任务生成 + SN 批量铸号守卫 + 工单取消级联
// API 可见断言：INSPECT_TASK 追溯 9 条 / 重复报工 409 不重复生成 / CANCEL 明细含质检任务数
// DB 可见断言（脚本末尾 SQL 复核提示）：质检任务行按工序分布、SN 归属最后报工、取消后质检任务 CANCELLED
// 运行：node scripts/verify-t3-hook.mjs（后端须已启动）

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

// 1. 工单 1：planQty=5 全链报工（第 1..12 道各报 5 良；第 13 道先报 3 再报 2，验证 SN 守卫）
const wo1 = await post('/production/work-orders', { productId: 1, planQty: 5 }, adminToken);
const wo1Id = wo1.body.data;
await post(`/production/work-orders/${wo1Id}/release`, {}, adminToken);
const tasks = (await get(`/production/tasks/for-work-order/${wo1Id}`, adminToken)).body.data;
ok('前置：下发 13 任务', tasks?.length === 13);

for (const t of tasks.slice(0, 12)) {
  await put(`/production/tasks/${t.id}/assign`, { operatorId: 2 }, adminToken);
  await put(`/production/tasks/${t.id}/start`, {}, adminToken);
  await post('/production/reports', { taskId: t.id, reportQty: 5, goodQty: 5, defectQty: 0 }, adminToken);
}
const lastTask = tasks[12];
await put(`/production/tasks/${lastTask.id}/assign`, { operatorId: 2 }, adminToken);
await put(`/production/tasks/${lastTask.id}/start`, {}, adminToken);
const partial = await post('/production/reports', { taskId: lastTask.id, reportQty: 3, goodQty: 3, defectQty: 0 }, adminToken);
ok('最后一道先报 3 code=0', partial.body.code === 0);
let lastFresh = (await get(`/production/tasks/for-work-order/${wo1Id}`, adminToken)).body.data[12];
ok('部分报工后任务仍 RUNNING（未铸号前提）', lastFresh.status === 'RUNNING' && lastFresh.completedQty === 3,
  `s=${lastFresh.status} c=${lastFresh.completedQty}`);
const final = await post('/production/reports', { taskId: lastTask.id, reportQty: 2, goodQty: 2, defectQty: 0 }, adminToken);
ok('最后一道补报 2 code=0（任务 COMPLETED）', final.body.code === 0);
const wo1Detail = (await get(`/production/work-orders/${wo1Id}`, adminToken)).body.data;
ok('工单自动 COMPLETED good=5', wo1Detail.status === 'COMPLETED' && wo1Detail.goodQty === 5,
  `s=${wo1Detail.status} g=${wo1Detail.goodQty}`);

// 2. 质检任务生成：9 个需质检工序各 1 条 INSPECT_TASK 追溯，单号格式 INP+日期+流水
let traces = (await get(`/production/traces?workOrderId=${wo1Id}`, adminToken)).body.data;
const inspectTraces = traces.filter(t => t.actionType === 'INSPECT_TASK');
ok('INSPECT_TASK 追溯恰 9 条（13 道中 9 道需质检）', inspectTraces.length === 9, `n=${inspectTraces.length}`);
ok('INSPECT_TASK 单号格式 /^INP\\d{12}$/',
  inspectTraces.every(t => /^INP\d{12}$/.test(JSON.parse(t.actionDetail).inspectionTaskNo)),
  inspectTraces.map(t => JSON.parse(t.actionDetail).inspectionTaskNo).join(','));
ok('REPORT 追溯 14 条（12 全量 + 2 分次）', traces.filter(t => t.actionType === 'REPORT').length === 14,
  `n=${traces.filter(t => t.actionType === 'REPORT').length}`);

// 3. 已完成任务重复报工 409，且不重复生成质检任务
const dup = await post('/production/reports', { taskId: lastTask.id, reportQty: 1, goodQty: 1, defectQty: 0 }, adminToken);
ok('已完成任务重复报工 409', dup.status === 409);
traces = (await get(`/production/traces?workOrderId=${wo1Id}`, adminToken)).body.data;
ok('重复报工被拒后 INSPECT_TASK 仍 9 条（不重复生成）',
  traces.filter(t => t.actionType === 'INSPECT_TASK').length === 9);

// 4. 工单 2：IQC 报工完成生成 1 个质检任务后取消 → 质检任务 CANCELLED + CANCEL 明细含数量
const wo2 = await post('/production/work-orders', { productId: 1, planQty: 5 }, adminToken);
const wo2Id = wo2.body.data;
await post(`/production/work-orders/${wo2Id}/release`, {}, adminToken);
const tasks2 = (await get(`/production/tasks/for-work-order/${wo2Id}`, adminToken)).body.data;
const iqc = tasks2[0];
await put(`/production/tasks/${iqc.id}/assign`, { operatorId: 2 }, adminToken);
await put(`/production/tasks/${iqc.id}/start`, {}, adminToken);
await post('/production/reports', { taskId: iqc.id, reportQty: 5, goodQty: 5, defectQty: 0 }, adminToken);
let traces2 = (await get(`/production/traces?workOrderId=${wo2Id}`, adminToken)).body.data;
ok('工单 2 IQC 完成后 INSPECT_TASK 恰 1 条', traces2.filter(t => t.actionType === 'INSPECT_TASK').length === 1);

const cancel = await put(`/production/work-orders/${wo2Id}/cancel`, {}, adminToken);
ok('工单 2 取消 code=0', cancel.body.code === 0);
traces2 = (await get(`/production/traces?workOrderId=${wo2Id}`, adminToken)).body.data;
const cancelTrace = traces2.find(t => t.actionType === 'CANCEL');
const cancelDetail = JSON.parse(cancelTrace.actionDetail);
ok('CANCEL 明细含 cancelledTaskCount=12', String(cancelDetail.cancelledTaskCount) === '12',
  `c=${cancelDetail.cancelledTaskCount}`);
ok('CANCEL 明细含 cancelledInspectionTaskCount=1', cancelDetail.cancelledInspectionTaskCount === 1,
  `c=${cancelDetail.cancelledInspectionTaskCount}`);

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
console.log(`\nSQL 复核提示（DB 侧断言）：
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes -e "
SELECT it.process_code_snapshot, it.status, it.plan_qty FROM mes_inspection_task it WHERE it.work_order_id=${wo1Id} ORDER BY it.id;   -- 预期 9 行：IQC/BLU_ASSY/PANEL_ASSY/SW_BURN/FUNC_TEST/AV_TEST/SAFETY_TEST/AGING/OQC 全 PENDING 且 plan_qty=5，无 MAIN_ASSY
SELECT report_id, COUNT(*) sn_cnt FROM mes_product_sn WHERE work_order_id=${wo1Id} GROUP BY report_id;   -- 预期单行 cnt=5（全部归属最后那次报工，证明分次报工不提前铸号）
SELECT sn FROM mes_product_sn WHERE work_order_id=${wo1Id} ORDER BY sn LIMIT 3;   -- 预期 /^SN\\d{12}$/
SELECT it.process_code_snapshot, it.status FROM mes_inspection_task it WHERE it.work_order_id=${wo2Id};   -- 预期 1 行 IQC 状态 CANCELLED
"`);
process.exit(fail > 0 ? 1 : 0);
