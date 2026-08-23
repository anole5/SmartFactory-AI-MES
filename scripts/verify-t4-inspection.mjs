// T4 质检服务验证：开始检验 + 检验录入校验链 + 分次录入 CAS + 不良明细落库 + 权限边界
// 前置：verify-t3-hook.mjs 已跑过（存在工单含 9 个 PENDING 质检任务）；运行：node scripts/verify-t4-inspection.mjs
// 说明：ID 字段全局 Long→String，断言用 String(...) 比较

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

const qa = await post('/auth/login', { username: 'qa', password: 'qa123' });
const qaToken = qa.body.data?.token;
const operator = await post('/auth/login', { username: 'operator', password: 'operator123' });
const operatorToken = operator.body.data?.token;
ok('qa 登录成功', qa.body.code === 0 && !!qaToken);

// 1. 找到 verify-t3 留下工单的质检任务（workOrderId=7，9 条），固定取 IQC 任务跑全流程（可重跑）
const page = await get('/quality/inspection-tasks/page?workOrderId=7&pageSize=20', qaToken);
ok('质检任务分页 total=9', String(page.body.data.total) === '9', `total=${page.body.data.total}`);
const task = page.body.data.records.find(t => t.processCodeSnapshot === 'IQC');
ok('IQC 质检任务存在且 PENDING', !!task && task.status === 'PENDING', `s=${task?.status}`);
ok('任务 VO 回填工单号', !!task?.workOrderNo, task?.workOrderNo);

// 2. 权限边界：operator 无质检权限 403
const rStart403 = await put(`/quality/inspection-tasks/${task.id}/start`, {}, operatorToken);
ok('operator 开始检验 403', rStart403.status === 403);

// 3. PENDING 直接录入 409
const rDirect = await post('/quality/inspection-records',
  { inspectionTaskId: task.id, goodQty: 2, defectQty: 0 }, qaToken);
ok('PENDING 直接录入 409', rDirect.status === 409, JSON.stringify(rDirect.body).slice(0, 80));

// 4. 开始检验：PENDING -> INSPECTING + 质检员回填
const rStart = await put(`/quality/inspection-tasks/${task.id}/start`, {}, qaToken);
ok('开始检验 code=0', rStart.body.code === 0);
let detail = (await get(`/quality/inspection-tasks/${task.id}`, qaToken)).body.data;
ok('开始后 status=INSPECTING', detail.status === 'INSPECTING', `s=${detail.status}`);
ok('质检员回填 = qa(id=4)', String(detail.inspectorId) === '4' && detail.inspectorName === '王质检',
  `id=${detail.inspectorId} name=${detail.inspectorName}`);
ok('开始时间回填', !!detail.startTime);
const firstStartTime = detail.startTime;
// 同状态幂等：再次开始不报错、不重置开始时间
const rStartAgain = await put(`/quality/inspection-tasks/${task.id}/start`, {}, qaToken);
ok('重复开始幂等 code=0', rStartAgain.body.code === 0);
detail = (await get(`/quality/inspection-tasks/${task.id}`, qaToken)).body.data;
ok('幂等开始不重置开始时间', detail.startTime === firstStartTime);

// 5. 录入校验链负例
const rZero = await post('/quality/inspection-records',
  { inspectionTaskId: task.id, goodQty: 0, defectQty: 0 }, qaToken);
ok('合格+不良=0 录入 409', rZero.status === 409, JSON.stringify(rZero.body).slice(0, 80));
const rMismatch = await post('/quality/inspection-records',
  { inspectionTaskId: task.id, goodQty: 0, defectQty: 2,
    defectItems: [{ defectCode: 'FLOWER_SCREEN', defectQty: 1 }] }, qaToken);
ok('不良行合计≠不良数量 409', rMismatch.status === 409, JSON.stringify(rMismatch.body).slice(0, 90));

// 6. 分次录入：先 2 良（仍 INSPECTING），再 2 良 1 不良（达 5 自动 COMPLETED）
const r1 = await post('/quality/inspection-records',
  { inspectionTaskId: task.id, goodQty: 2, defectQty: 0 }, qaToken);
ok('第一次录入 2 良 code=0', r1.body.code === 0);
detail = (await get(`/quality/inspection-tasks/${task.id}`, qaToken)).body.data;
ok('第一次后仍 INSPECTING 累计 2/0/0',
  detail.status === 'INSPECTING' && detail.inspectedQty === 2 && detail.goodQty === 2 && detail.defectQty === 0,
  `s=${detail.status} i=${detail.inspectedQty} g=${detail.goodQty} d=${detail.defectQty}`);

const r2 = await post('/quality/inspection-records',
  { inspectionTaskId: task.id, goodQty: 2, defectQty: 1,
    defectItems: [{ defectCode: 'FLOWER_SCREEN', defectQty: 1 }] }, qaToken);
ok('第二次录入 2 良 1 不良 code=0', r2.body.code === 0);
detail = (await get(`/quality/inspection-tasks/${task.id}`, qaToken)).body.data;
ok('达标自动 COMPLETED 累计 5/4/1',
  detail.status === 'COMPLETED' && detail.inspectedQty === 5 && detail.goodQty === 4 && detail.defectQty === 1,
  `s=${detail.status} i=${detail.inspectedQty} g=${detail.goodQty} d=${detail.defectQty}`);
ok('完成时间回填', !!detail.endTime);

// 7. 质检记录列表：2 条 + 质检员名称回填
const records = (await get(`/quality/inspection-tasks/${task.id}/records`, qaToken)).body.data;
ok('质检记录 2 条', records?.length === 2, `n=${records?.length}`);
ok('记录单号格式 /^INS\\d{12}$/',
  records.every(r => /^INS\d{12}$/.test(r.inspectionRecordNo)),
  records.map(r => r.inspectionRecordNo).join(','));
ok('记录质检员回填王质检', records.every(r => r.inspectorName === '王质检'));

// 8. 不良记录：最新一条为本次 IQC 录入产生 + 单号格式 + 工单号/工序快照回填
const defects = await get('/quality/defects/page?workOrderId=7&pageSize=20', qaToken);
ok('不良分页有记录（total≥1）', Number(defects.body.data.total) >= 1, `total=${defects.body.data.total}`);
const defect = defects.body.data.records[0]; // id 倒序 = 最新 = 本次 IQC 的不良
ok('不良单号格式 /^DEF\\d{12}$/', /^DEF\d{12}$/.test(defect.defectNo), defect.defectNo);
ok('不良码 FLOWER_SCREEN + 工单号/工序快照回填',
  defect.defectCode === 'FLOWER_SCREEN' && !!defect.workOrderNo
  && defect.processCodeSnapshot === 'IQC' && defect.processNameSnapshot === '来料检验',
  `${defect.defectCode}/${defect.workOrderNo}/${defect.processCodeSnapshot}`);

// 9. 追溯（按本次任务的工序任务 ID 限定，可重跑）：INSPECT 2 条 + DEFECT 1 条
const traces = (await get('/production/traces?workOrderId=7', qaToken)).body.data;
const taskTraces = traces.filter(t => String(t.taskId) === String(task.operationTaskId));
ok('本工序追溯 INSPECT=2 DEFECT=1',
  taskTraces.filter(t => t.actionType === 'INSPECT').length === 2
  && taskTraces.filter(t => t.actionType === 'DEFECT').length === 1,
  `I=${taskTraces.filter(t => t.actionType === 'INSPECT').length} D=${taskTraces.filter(t => t.actionType === 'DEFECT').length}`);

// 10. COMPLETED 再录 409
const rAfter = await post('/quality/inspection-records',
  { inspectionTaskId: task.id, goodQty: 1, defectQty: 0 }, qaToken);
ok('COMPLETED 再录 409', rAfter.status === 409, JSON.stringify(rAfter.body).slice(0, 80));

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
