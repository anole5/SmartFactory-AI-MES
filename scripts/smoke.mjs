// SmartFactory-MES 冒烟测试脚本（Node 18+ 内置 fetch）
// 第 2 周版：真实登录（JWT）→ 第 1 周断言回归（基础资料）→ 生产执行主链路 + 权限边界 + BOM/路线升版
// 前置条件：干净库重放 00→04 后运行（种子产品 3 条等断言依赖干净状态）
// 运行后清理：Git Bash 执行 scripts/clean-smoke.sql 回到种子状态
const BASE = 'http://localhost:8080/api';
let pass = 0, fail = 0;
let ADMIN_TOKEN = null;
let OPERATOR_TOKEN = null;
let PLANNING_TOKEN = null;

// token 参数：默认 admin；显式传 null 表示不带 token
async function req(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  const t = token === undefined ? ADMIN_TOKEN : token;
  if (t) headers['Authorization'] = `Bearer ${t}`;
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body !== undefined && body !== null ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* 非 JSON */ }
  return { status: res.status, json };
}

function check(name, cond, detail) {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; console.log(`  FAIL  ${name}  => ${detail}`); }
}

// ------------------------------------------------------------
// 0. 真实登录（JWT + RBAC 权限集合下发）
// ------------------------------------------------------------
{
  const r1 = await req('POST', '/auth/login', { username: 'admin', password: 'admin123' }, null);
  ADMIN_TOKEN = r1.json?.data?.token;
  check('admin 登录成功并返回 token', r1.status === 200 && r1.json?.code === 0 && !!ADMIN_TOKEN,
    JSON.stringify(r1.json)?.slice(0, 150));
  check('admin 返回权限集合（含工单下发）',
    Array.isArray(r1.json?.data?.permissions) && r1.json.data.permissions.includes('production:work-order:release'),
    JSON.stringify(r1.json?.data?.permissions?.length));

  const r2 = await req('POST', '/auth/login', { username: 'operator', password: 'operator123' }, null);
  OPERATOR_TOKEN = r2.json?.data?.token;
  const opPerms = r2.json?.data?.permissions ?? [];
  check('operator 登录成功', r2.status === 200 && r2.json?.code === 0 && !!OPERATOR_TOKEN,
    JSON.stringify(r2.json)?.slice(0, 150));
  check('operator 权限含报工、不含工单下发',
    opPerms.includes('production:report:create') && !opPerms.includes('production:work-order:release'),
    JSON.stringify(opPerms));

  const r3 = await req('POST', '/auth/login', { username: 'planning', password: 'planning123' }, null);
  PLANNING_TOKEN = r3.json?.data?.token;
  check('planning 登录成功', r3.status === 200 && r3.json?.code === 0 && !!PLANNING_TOKEN,
    JSON.stringify(r3.json)?.slice(0, 150));

  const r4 = await req('POST', '/auth/login', { username: 'admin', password: 'wrong' }, null);
  check('密码错误 -> 401', r4.status === 401 && r4.json?.code === 401, `status=${r4.status}`);

  const r5 = await req('GET', '/master/products/page?pageNum=1&pageSize=10', null, null);
  check('无 token -> 401', r5.status === 401, `status=${r5.status}`);
}

// ------------------------------------------------------------
// 1. 产品分页：应返回种子数据（3 条，含 TV-AOC-55U4K-001）
// ------------------------------------------------------------
{
  const { status, json } = await req('GET', '/master/products/page?pageNum=1&pageSize=10');
  check('GET /master/products/page -> code:0', status === 200 && json?.code === 0,
    `status=${status} body=${JSON.stringify(json)?.slice(0, 200)}`);
  const records = json?.data?.records ?? [];
  check('种子产品 3 条', records.length === 3, `got ${records.length}`);
  check('主产品 TV-AOC-55U4K-001 存在且 ENABLED',
    records.some(r => r.productCode === 'TV-AOC-55U4K-001' && r.status === 'ENABLED'),
    JSON.stringify(records.map(r => r.productCode)));
}

// 2. 物料分页：20 种
{
  const { json } = await req('GET', '/master/materials/page?pageNum=1&pageSize=50');
  // 注意：JacksonConfig 将 Long 序列化为字符串（防 JS 丢精度），total 是 "20"
  check('物料 20 种', String(json?.data?.total) === '20', `total=${json?.data?.total}`);
}

// 3. 建产品 -> 重复编码 409
{
  const r1 = await req('POST', '/master/products',
    { productCode: 'T-001', productName: '测试产品', productType: '测试', specification: 'SP-1', unit: '台' });
  check('POST /master/products 创建成功', r1.status === 200 && r1.json?.code === 0,
    JSON.stringify(r1.json)?.slice(0, 150));
  const newId = r1.json?.data;
  const r2 = await req('POST', '/master/products',
    { productCode: 'T-001', productName: '重复', specification: 'X', unit: '台' });
  check('重复编码 -> 409', r2.status === 409 && r2.json?.code === 409,
    `status=${r2.status} body=${JSON.stringify(r2.json)?.slice(0, 150)}`);

  // 清理：删掉测试产品（新建成 DISABLED，无引用，可删）
  const r3 = await req('DELETE', `/master/products/${newId}`);
  check('删除测试产品', r3.json?.code === 0, JSON.stringify(r3.json));
}

// ------------------------------------------------------------
// 4. 测试产品 SMK-001 + BOM 升版（激活新版本自动作废旧版本）
//    注意：BOM 测试挂在专用测试产品上，避免激活时把种子 BOM 挤成 OBSOLETE
// ------------------------------------------------------------
let smkProductId = null;
{
  const r0 = await req('POST', '/master/products',
    { productCode: 'SMK-001', productName: '冒烟测试产品', productType: '测试', specification: 'SP-1', unit: '台' });
  check('创建测试产品 SMK-001', r0.json?.code === 0, JSON.stringify(r0.json)?.slice(0, 150));
  smkProductId = r0.json?.data;
  const r1 = await req('PUT', `/master/products/${smkProductId}/status`, { status: 'ENABLED' });
  check('启用测试产品（默认 DISABLED）', r1.json?.code === 0, JSON.stringify(r1.json));

  const r2 = await req('POST', '/master/boms', {
    productId: Number(smkProductId),
    version: 'V1',
    items: [
      { materialId: 1, requiredQty: 1, lossRate: 0.5, remark: '测试行1' },
      { materialId: 2, requiredQty: 2, lossRate: 0, remark: '测试行2' },
    ],
  });
  check('POST /master/boms 创建成功', r2.status === 200 && r2.json?.code === 0,
    JSON.stringify(r2.json)?.slice(0, 200));
  const bomV1Id = r2.json?.data;
  const r3 = await req('GET', `/master/boms/${bomV1Id}`);
  check('BOM 单号 = BOM+日期+4位流水（生成器）', /^BOM\d{12}$/.test(r3.json?.data?.bomNo ?? ''),
    r3.json?.data?.bomNo);
  const items = r3.json?.data?.items ?? [];
  check('BOM 详情含 2 行明细', items.length === 2, `got ${items.length}`);
  check('明细快照已回填', items.length > 0 && items.every(i => i.materialCodeSnapshot && i.materialNameSnapshot && i.unitSnapshot),
    JSON.stringify(items.map(i => i.materialCodeSnapshot)));
  check('明细行号 1..2', items.map(i => i.lineNo).join(',') === '1,2', JSON.stringify(items.map(i => i.lineNo)));

  const r4 = await req('PUT', `/master/boms/${bomV1Id}/status`, { status: 'ACTIVE' });
  check('BOM V1 DRAFT->ACTIVE 成功', r4.json?.code === 0, JSON.stringify(r4.json)?.slice(0, 150));
  const r5 = await req('PUT', `/master/boms/${bomV1Id}`,
    { productId: Number(smkProductId), items: [{ materialId: 1, requiredQty: 3 }] });
  check('ACTIVE BOM 编辑 -> 409', r5.json?.code === 409, JSON.stringify(r5.json)?.slice(0, 150));
  const r6 = await req('PUT', `/master/boms/${bomV1Id}/status`, { status: 'DRAFT' });
  check('ACTIVE->DRAFT 回退 -> 409', r6.json?.code === 409, JSON.stringify(r6.json)?.slice(0, 150));

  // 升版：V2 激活后 V1 自动 OBSOLETE（第 2 周补的 TODO）
  const r7 = await req('POST', '/master/boms', {
    productId: Number(smkProductId),
    version: 'V2',
    items: [{ materialId: 1, requiredQty: 2 }],
  });
  check('创建 BOM V2', r7.json?.code === 0, JSON.stringify(r7.json)?.slice(0, 150));
  const bomV2Id = r7.json?.data;
  const r8 = await req('PUT', `/master/boms/${bomV2Id}/status`, { status: 'ACTIVE' });
  check('BOM V2 激活成功', r8.json?.code === 0, JSON.stringify(r8.json)?.slice(0, 150));
  const v1After = (await req('GET', `/master/boms/${bomV1Id}`)).json?.data;
  const v2After = (await req('GET', `/master/boms/${bomV2Id}`)).json?.data;
  check('升版联动：V1 自动作废', v1After?.status === 'OBSOLETE', `V1 status=${v1After?.status}`);
  check('V2 保持 ACTIVE', v2After?.status === 'ACTIVE', `V2 status=${v2After?.status}`);

  const r9 = await req('PUT', `/master/boms/${bomV2Id}/status`, { status: 'FROZEN' });
  check('非法状态 -> 400', r9.json?.code === 400, JSON.stringify(r9.json)?.slice(0, 150));
}

// ------------------------------------------------------------
// 5. 工艺路线 3 步 + 升版联动（同 BOM）
// ------------------------------------------------------------
{
  const r1 = await req('POST', '/master/routes', {
    productId: Number(smkProductId),
    version: 'V1',
    steps: [
      { processId: 1, workstationId: 1 },
      { processId: 2, workstationId: 2, needInspection: true },
      { processId: 3, workstationId: null },
    ],
  });
  check('POST /master/routes 创建成功', r1.status === 200 && r1.json?.code === 0,
    JSON.stringify(r1.json)?.slice(0, 200));
  const routeV1Id = r1.json?.data;
  const r2 = await req('GET', `/master/routes/${routeV1Id}`);
  check('路线单号 = RT+日期+4位流水（生成器）', /^RT\d{12}$/.test(r2.json?.data?.routeNo ?? ''),
    r2.json?.data?.routeNo);
  const steps = r2.json?.data?.steps ?? [];
  check('路线详情含 3 步', steps.length === 3, `got ${steps.length}`);
  check('步骤快照已回填', steps.length > 0 && steps.every(s => s.processCodeSnapshot && s.processNameSnapshot),
    JSON.stringify(steps.map(s => s.processCodeSnapshot)));
  check('顺序号 1..3', steps.map(s => s.sequenceNo).join(',') === '1,2,3',
    JSON.stringify(steps.map(s => s.sequenceNo)));
  check('工位信息已填充', steps[0]?.workstationCode && steps[0]?.workstationName, JSON.stringify(steps[0]));

  const r3 = await req('PUT', `/master/routes/${routeV1Id}/status`, { status: 'ACTIVE' });
  check('路线 V1 激活成功', r3.json?.code === 0, JSON.stringify(r3.json)?.slice(0, 150));

  const r4 = await req('POST', '/master/routes', {
    productId: Number(smkProductId),
    version: 'V2',
    steps: [{ processId: 1, workstationId: 1 }],
  });
  check('创建路线 V2', r4.json?.code === 0, JSON.stringify(r4.json)?.slice(0, 150));
  const routeV2Id = r4.json?.data;
  const r5 = await req('PUT', `/master/routes/${routeV2Id}/status`, { status: 'ACTIVE' });
  check('路线 V2 激活成功', r5.json?.code === 0, JSON.stringify(r5.json)?.slice(0, 150));
  const v1After = (await req('GET', `/master/routes/${routeV1Id}`)).json?.data;
  check('升版联动：路线 V1 自动作废', v1After?.status === 'OBSOLETE', `V1 status=${v1After?.status}`);

  const r6 = await req('DELETE', `/master/products/${smkProductId}`);
  check('被 BOM/路线引用的产品禁删 -> 409', r6.status === 409 && r6.json?.code === 409,
    `status=${r6.status} body=${JSON.stringify(r6.json)?.slice(0, 150)}`);
}

// ------------------------------------------------------------
// 6. 权限边界：operator 无基础资料写权限、可查任务；planning 无报工权限
// ------------------------------------------------------------
{
  const r1 = await req('POST', '/master/products',
    { productCode: 'X-001', productName: '越权', specification: 'X', unit: '台' }, OPERATOR_TOKEN);
  check('operator 建产品 -> 403', r1.status === 403 && r1.json?.code === 403, `status=${r1.status}`);
  const r2 = await req('GET', '/production/tasks/page?pageNum=1&pageSize=5', null, OPERATOR_TOKEN);
  check('operator 查任务列表放行', r2.status === 200 && r2.json?.code === 0, `status=${r2.status}`);
}

// ------------------------------------------------------------
// 7. 生产执行主链路：建单 -> 下发 13 任务 -> 派工 -> 开工/暂停/继续 -> 报工 -> 工单完成
// ------------------------------------------------------------
let woId = null;
let taskIds = [];
{
  const r1 = await req('POST', '/production/work-orders', {
    productId: 1,
    planQty: 10,
    externalOrderNo: 'SMOKE-20260823',
    priority: 'NORMAL',
    planStartTime: '2026-08-23 09:00:00',
    planEndTime: '2026-08-23 18:00:00',
  });
  check('建单成功（DRAFT）', r1.status === 200 && r1.json?.code === 0, JSON.stringify(r1.json)?.slice(0, 200));
  woId = r1.json?.data;
  const wo = (await req('GET', `/production/work-orders/${woId}`)).json?.data;
  check('工单号 = WO+日期+4位流水（生成器）', /^WO\d{12}$/.test(wo?.workOrderNo ?? ''), wo?.workOrderNo);
  check('自动解析 ACTIVE BOM/路线 + 产品快照',
    String(wo?.bomId) === '1' && String(wo?.routeId) === '1' && !!wo?.productCodeSnapshot && !!wo?.productNameSnapshot,
    JSON.stringify({ bomId: wo?.bomId, routeId: wo?.routeId, productNameSnapshot: wo?.productNameSnapshot }));

  const r2 = await req('POST', `/production/work-orders/${woId}/release`, {});
  check('下发成功 -> RELEASED', r2.json?.code === 0, JSON.stringify(r2.json)?.slice(0, 150));
  const after = (await req('GET', `/production/work-orders/${woId}`)).json?.data;
  const tasks = after?.tasks ?? [];
  check('按路线生成 13 个工序任务', tasks.length === 13, `got ${tasks.length}`);
  check('任务序 1..13 且全部 PENDING',
    tasks.map(t => t.sequenceNo).join(',') === '1,2,3,4,5,6,7,8,9,10,11,12,13' && tasks.every(t => t.status === 'PENDING'),
    JSON.stringify(tasks.map(t => `${t.sequenceNo}:${t.status}`)));
  check('任务单号 = TASK+日期+4位流水（生成器）', tasks.every(t => /^TASK\d{12}$/.test(t.taskNo)),
    JSON.stringify(tasks.map(t => t.taskNo)));
  taskIds = tasks.map(t => t.id);

  const r3 = await req('POST', `/production/work-orders/${woId}/release`, {});
  check('重复下发 -> 409', r3.status === 409 && r3.json?.code === 409, `status=${r3.status}`);
  const r4 = await req('POST', `/production/work-orders/${woId}/release`, {}, OPERATOR_TOKEN);
  check('operator 下发工单 -> 403', r4.status === 403, `status=${r4.status}`);
}

// 8. 派工：13 个任务全部派给 operator
{
  let ok = true;
  for (const id of taskIds) {
    const r = await req('PUT', `/production/tasks/${id}/assign`, { operatorId: 2 });
    if (r.json?.code !== 0) { ok = false; break; }
  }
  check('13 个任务派工成功 -> ASSIGNED', ok, 'assign loop failed');
  const r2 = await req('PUT', `/production/tasks/${taskIds[0]}/assign`, { operatorId: 3 });
  check('重复派工 -> 409', r2.status === 409 && r2.json?.code === 409, `status=${r2.status}`);
}

// 9. 开工 / 暂停 / 继续
{
  const t1 = taskIds[0];
  const r1 = await req('PUT', `/production/tasks/${t1}/start`, {});
  check('开工成功 -> RUNNING', r1.json?.code === 0, JSON.stringify(r1.json)?.slice(0, 150));
  const wo = (await req('GET', `/production/work-orders/${woId}`)).json?.data;
  check('工单级联 IN_PROGRESS + 实际开工时间回填',
    wo?.status === 'IN_PROGRESS' && !!wo?.actualStartTime,
    JSON.stringify({ status: wo?.status, actualStartTime: wo?.actualStartTime }));
  const r2 = await req('PUT', `/production/tasks/${t1}/pause`, {});
  check('暂停 -> PAUSED', r2.json?.code === 0, JSON.stringify(r2.json)?.slice(0, 150));
  const r3 = await req('PUT', `/production/tasks/${t1}/resume`, {});
  check('继续 -> RUNNING', r3.json?.code === 0, JSON.stringify(r3.json)?.slice(0, 150));
}

// 10. 报工（operator 执行）：校验链负例 + 正例 + 前道校验
{
  const t1 = taskIds[0];
  const t2 = taskIds[1];
  const n1 = await req('POST', '/production/reports',
    { taskId: t2, reportQty: 5, goodQty: 5, defectQty: 0 }, OPERATOR_TOKEN);
  check('未开工任务报工 -> 409', n1.status === 409, `status=${n1.status}`);
  const n2 = await req('POST', '/production/reports',
    { taskId: t1, reportQty: 11, goodQty: 11, defectQty: 0 }, OPERATOR_TOKEN);
  check('超额报工 -> 409', n2.status === 409, `status=${n2.status}`);
  const n3 = await req('POST', '/production/reports',
    { taskId: t1, reportQty: 5, goodQty: 3, defectQty: 1 }, OPERATOR_TOKEN);
  check('合格+不良 != 报工数量 -> 409', n3.status === 409, `status=${n3.status}`);
  const n4 = await req('POST', '/production/reports',
    { taskId: t1, reportQty: 0, goodQty: 0, defectQty: 0 }, OPERATOR_TOKEN);
  check('零数量报工 -> 409', n4.status === 409, `status=${n4.status}`);

  await req('PUT', `/production/tasks/${t2}/start`, {});
  const n5 = await req('POST', '/production/reports',
    { taskId: t2, reportQty: 5, goodQty: 5, defectQty: 0 }, OPERATOR_TOKEN);
  check('后道合格超过前道 -> 409', n5.status === 409, `status=${n5.status}`);

  const p1 = await req('POST', '/production/reports',
    { taskId: t1, reportQty: 10, goodQty: 10, defectQty: 0, productBatchNo: 'SMOKE-BATCH-01' }, OPERATOR_TOKEN);
  check('operator 报工 10 台成功（t1 -> COMPLETED）', p1.json?.code === 0,
    JSON.stringify(p1.json)?.slice(0, 150));
  const n6 = await req('POST', '/production/reports',
    { taskId: t1, reportQty: 1, goodQty: 1, defectQty: 0 }, OPERATOR_TOKEN);
  check('已完成任务再报工 -> 409', n6.status === 409, `status=${n6.status}`);

  const p2 = await req('POST', '/production/reports',
    { taskId: t2, reportQty: 10, goodQty: 10, defectQty: 0 }, OPERATOR_TOKEN);
  check('t2 报满 10 台（前道=后道合格）成功', p2.json?.code === 0, JSON.stringify(p2.json)?.slice(0, 150));

  let ok = true;
  for (let i = 2; i < 13; i++) {
    const st = await req('PUT', `/production/tasks/${taskIds[i]}/start`, {});
    const rp = await req('POST', '/production/reports',
      { taskId: taskIds[i], reportQty: 10, goodQty: 10, defectQty: 0 }, OPERATOR_TOKEN);
    if (st.json?.code !== 0 || rp.json?.code !== 0) { ok = false; break; }
  }
  check('t3..t13 开工+报满全部成功', ok, 'chain loop failed');

  const p3 = await req('POST', '/production/reports',
    { taskId: taskIds[12], reportQty: 1, goodQty: 1, defectQty: 0 }, PLANNING_TOKEN);
  check('planning 无报工权限 -> 403', p3.status === 403, `status=${p3.status}`);
}

// 11. 工单完成状态 + 报工记录 + 追溯链核对
{
  const wo = (await req('GET', `/production/work-orders/${woId}`)).json?.data;
  check('最后一道完成后工单自动 COMPLETED', wo?.status === 'COMPLETED', `status=${wo?.status}`);
  check('工单完成/合格/不良 = 10/10/0',
    wo?.completedQty === 10 && wo?.goodQty === 10 && wo?.defectQty === 0,
    JSON.stringify({ completedQty: wo?.completedQty, goodQty: wo?.goodQty, defectQty: wo?.defectQty }));
  check('实际完工时间回填', !!wo?.actualEndTime, String(wo?.actualEndTime));
  check('详情报工记录数 = 13', String(wo?.reportCount) === '13', `reportCount=${wo?.reportCount}`);

  const page = (await req('GET', `/production/reports/page?pageNum=1&pageSize=50&workOrderId=${woId}`)).json?.data;
  check('报工列表 13 条', String(page?.total) === '13' && (page?.records ?? []).length === 13,
    `total=${page?.total} got=${page?.records?.length}`);
  const recs = page?.records ?? [];
  check('报工记录含工单号/任务号/操作员',
    recs.every(r => !!r.reportNo && !!r.workOrderNo && !!r.taskNo && !!r.operatorName),
    JSON.stringify(recs[0] ?? {}));

  const traces = (await req('GET', `/production/traces?workOrderId=${woId}`)).json?.data ?? [];
  const countBy = {};
  for (const t of traces) countBy[t.actionType] = (countBy[t.actionType] ?? 0) + 1;
  check('追溯链 CREATE=1 / RELEASE=1', countBy.CREATE === 1 && countBy.RELEASE === 1, JSON.stringify(countBy));
  check('追溯链 ASSIGN=13 / START=13', countBy.ASSIGN === 13 && countBy.START === 13, JSON.stringify(countBy));
  check('追溯链 PAUSE=1 / RESUME=1', countBy.PAUSE === 1 && countBy.RESUME === 1, JSON.stringify(countBy));
  check('追溯链 REPORT=13（失败报工事务回滚零残留）', countBy.REPORT === 13, JSON.stringify(countBy));
  check('追溯总条数 = 43', traces.length === 43, `got ${traces.length}`);
}

console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail > 0 ? 1 : 0);
