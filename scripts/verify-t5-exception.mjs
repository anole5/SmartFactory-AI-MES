// T5 异常管理验证：不良生成异常单 + 状态机 OPEN→PROCESSING→CLOSED + 手工创建 + 权限边界 + 追溯
// 自包含可重跑：每次取一个 PENDING 质检任务新录一条不良走全流程；追溯按异常单号精确断言
// 运行：node scripts/verify-t5-exception.mjs（后端须已启动）

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

// 1. 取一个 PENDING 质检任务，录 1 条不良（自包含，每次消耗一个新任务）
const pending = await get('/quality/inspection-tasks/page?status=PENDING&pageSize=1', qaToken);
const task = pending.body.data?.records?.[0];
ok('存在 PENDING 质检任务', !!task, `task=${task?.inspectionTaskNo}`);
await put(`/quality/inspection-tasks/${task.id}/start`, {}, qaToken);
const rRec = await post('/quality/inspection-records',
  { inspectionTaskId: task.id, goodQty: 0, defectQty: 1,
    defectItems: [{ defectCode: 'FLOWER_SCREEN', defectQty: 1 }] }, qaToken);
ok('录入 1 不良 code=0', rRec.body.code === 0);
const defects = await get(`/quality/defects/page?workOrderId=${task.workOrderId}&defectCode=FLOWER_SCREEN&pageSize=20`, qaToken);
const defect = defects.body.data.records[0]; // 最新 = 本次不良
ok('最新不良单号格式 /^DEF\\d{12}$/', /^DEF\d{12}$/.test(defect.defectNo), defect.defectNo);

// 2. 不良生成异常单 → OPEN + 单号/来源/关联回填
const rToEx = await put(`/quality/defects/${defect.id}/to-exception`, {}, qaToken);
ok('不良生成异常单 code=0', rToEx.body.code === 0);
const exId = rToEx.body.data;
const exPage = await get(`/quality/exceptions/page?workOrderId=${task.workOrderId}&pageSize=50`, qaToken);
let ex = exPage.body.data.records.find(e => String(e.id) === String(exId));
ok('异常单 OPEN + 来源 DEFECT', ex?.status === 'OPEN' && ex?.sourceType === 'DEFECT',
  `s=${ex?.status} src=${ex?.sourceType}`);
ok('异常单号格式 /^EXP\\d{12}$/', /^EXP\d{12}$/.test(ex.exceptionNo), ex.exceptionNo);
ok('异常单回填不良单号/工单号', ex.defectNo === defect.defectNo && !!ex.workOrderNo,
  `${ex.defectNo}/${ex.workOrderNo}`);
ok('异常描述含不良单号', ex.description.includes(defect.defectNo), ex.description.slice(0, 60));

// 3. 重复生成 409；OPEN 直接 close 409
const rDup = await put(`/quality/defects/${defect.id}/to-exception`, {}, qaToken);
ok('同不良重复生成异常单 409', rDup.status === 409, JSON.stringify(rDup.body).slice(0, 80));
const rCloseOpen = await put(`/quality/exceptions/${exId}/close`, { resolveRemark: '已处理' }, qaToken);
ok('OPEN 直接关闭 409', rCloseOpen.status === 409, JSON.stringify(rCloseOpen.body).slice(0, 80));

// 4. 开始处理：OPEN -> PROCESSING + 处理人回填
const rProc = await put(`/quality/exceptions/${exId}/process`, {}, qaToken);
ok('开始处理 code=0', rProc.body.code === 0);
ex = (await get(`/quality/exceptions/page?workOrderId=${task.workOrderId}&pageSize=50`, qaToken))
  .body.data.records.find(e => String(e.id) === String(exId));
ok('处理后 PROCESSING + 处理人=qa', ex.status === 'PROCESSING' && String(ex.handlerId) === '4' && ex.handlerName === '王质检',
  `s=${ex.status} h=${ex.handlerId}/${ex.handlerName}`);
// 同状态幂等
const rProcAgain = await put(`/quality/exceptions/${exId}/process`, {}, qaToken);
ok('重复处理幂等 code=0', rProcAgain.body.code === 0);

// 5. 关闭：无处理结论 400；带结论 → CLOSED + resolvedAt；重复关闭幂等不覆盖
const rCloseNoRemark = await put(`/quality/exceptions/${exId}/close`, {}, qaToken);
ok('关闭无处理结论 400', rCloseNoRemark.status === 400, JSON.stringify(rCloseNoRemark.body).slice(0, 80));
const rClose = await put(`/quality/exceptions/${exId}/close`, { resolveRemark: '换用新面板后复测通过，工单恢复' }, qaToken);
ok('关闭 code=0', rClose.body.code === 0);
ex = (await get(`/quality/exceptions/page?workOrderId=${task.workOrderId}&pageSize=50`, qaToken))
  .body.data.records.find(e => String(e.id) === String(exId));
ok('关闭后 CLOSED + resolvedAt 回填', ex.status === 'CLOSED' && !!ex.resolvedAt, `s=${ex.status} t=${ex.resolvedAt}`);
ok('处理结论落库', ex.resolveRemark === '换用新面板后复测通过，工单恢复');
const rCloseAgain = await put(`/quality/exceptions/${exId}/close`, { resolveRemark: '重复关闭' }, qaToken);
ok('重复关闭幂等 code=0', rCloseAgain.body.code === 0);
ex = (await get(`/quality/exceptions/page?workOrderId=${task.workOrderId}&pageSize=50`, qaToken))
  .body.data.records.find(e => String(e.id) === String(exId));
ok('幂等关闭不覆盖原处理结论', ex.resolveRemark === '换用新面板后复测通过，工单恢复');

// 6. 手工创建（MANUAL）
const rManual = await post('/quality/exceptions',
  { description: '面板来料批次外观异常，手工升级处理', workOrderId: task.workOrderId }, qaToken);
ok('手工创建异常单 code=0', rManual.body.code === 0);
const manualId = rManual.body.data;
const manual = (await get(`/quality/exceptions/page?workOrderId=${task.workOrderId}&pageSize=50`, qaToken))
  .body.data.records.find(e => String(e.id) === String(manualId));
ok('手工异常单 MANUAL + OPEN', manual?.sourceType === 'MANUAL' && manual?.status === 'OPEN',
  `src=${manual?.sourceType} s=${manual?.status}`);

// 7. 权限边界：operator 无质量权限 403
const rOpToEx = await put(`/quality/defects/${defect.id}/to-exception`, {}, operatorToken);
ok('operator 生成异常单 403', rOpToEx.status === 403);
const rOpProc = await put(`/quality/exceptions/${manualId}/process`, {}, operatorToken);
ok('operator 处理异常 403', rOpProc.status === 403);
const rOpClose = await put(`/quality/exceptions/${manualId}/close`, { resolveRemark: 'x' }, operatorToken);
ok('operator 关闭异常 403', rOpClose.status === 403);
const rOpCreate = await post('/quality/exceptions', { description: '越权创建' }, operatorToken);
ok('operator 手工创建异常 403', rOpCreate.status === 403);

// 8. 追溯：按异常单号精确断言 CREATE/PROCESS/CLOSE 各 1 条（可重跑）
const traces = (await get(`/production/traces?workOrderId=${task.workOrderId}`, qaToken)).body.data;
const detailOf = t => { try { return JSON.parse(t.actionDetail); } catch { return {}; } };
ok('EXCEPTION_CREATE 追溯含本次不良生成异常单',
  traces.filter(t => t.actionType === 'EXCEPTION_CREATE' && detailOf(t).exceptionNo === ex.exceptionNo).length === 1);
ok('EXCEPTION_CREATE 追溯含手工异常单',
  traces.filter(t => t.actionType === 'EXCEPTION_CREATE' && detailOf(t).exceptionNo === manual.exceptionNo).length === 1);
ok('EXCEPTION_PROCESS 追溯 1 条',
  traces.filter(t => t.actionType === 'EXCEPTION_PROCESS' && detailOf(t).exceptionNo === ex.exceptionNo).length === 1);
ok('EXCEPTION_CLOSE 追溯 1 条',
  traces.filter(t => t.actionType === 'EXCEPTION_CLOSE' && detailOf(t).exceptionNo === ex.exceptionNo).length === 1);

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
