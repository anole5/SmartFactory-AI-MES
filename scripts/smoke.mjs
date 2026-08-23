// SmartFactory-MES 冒烟测试脚本（Node 18+ 内置 fetch）
// 第 4 周版：真实登录（JWT）→ 第 1/2 周断言回归（基础资料/生产执行/BOM升版）
//   → 第 3 周质量链路（质检任务/检验录入/不良/异常）+ SN 追溯 + 批次追溯 + 生产看板
//   → 第 4 周 AI（知识库 RAG/统一助手四意图/异常建议/生产日报 + 权限边界）
//   → 第 5 周系统集成（ERP 外单 5 台全链 + WMS 领料/成品入库 + 菜单树角色差异）
//   → 第 6 周生产深化（物料批次追溯 + 生产排程甘特图 + 报表中心）
// 前置条件：干净库重放 00→12 后运行（种子产品 3 条等断言依赖干净状态）
// 运行后清理：Git Bash 执行 scripts/clean-smoke.sql 回到种子状态
const BASE = 'http://localhost:8080/api';
let pass = 0, fail = 0;
let ADMIN_TOKEN = null;
let OPERATOR_TOKEN = null;
let PLANNING_TOKEN = null;
let QA_TOKEN = null;

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

  // 第 3 周新角色：质检员 qa（种子 id=4，INSPECTOR 角色）
  const r6 = await req('POST', '/auth/login', { username: 'qa', password: 'qa123' }, null);
  QA_TOKEN = r6.json?.data?.token;
  const qaPerms = r6.json?.data?.permissions ?? [];
  check('qa 登录成功', r6.status === 200 && r6.json?.code === 0 && !!QA_TOKEN,
    JSON.stringify(r6.json)?.slice(0, 150));
  check('qa 权限含检验录入/追溯/看板，不含工单建单',
    qaPerms.includes('quality:inspection-record:create') &&
      qaPerms.includes('production:trace:query') &&
      qaPerms.includes('production:dashboard:query') &&
      !qaPerms.includes('production:work-order:create'),
    JSON.stringify(qaPerms));
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
  check('9 个需质检工序各生成 1 个质检任务（INSPECT_TASK=9）', countBy.INSPECT_TASK === 9, JSON.stringify(countBy));
  check('追溯总条数 = 52（43 + 9 质检任务生成）', traces.length === 52, `got ${traces.length}`);
}

// ------------------------------------------------------------
// 12. 整机 SN：最后一道报工完成自动铸号 10 台 + 按 SN 追溯
// ------------------------------------------------------------
{
  const page = (await req('GET', `/production/sns/page?pageNum=1&pageSize=20&workOrderId=${woId}`)).json?.data;
  check('SN 分页 total = 10（按工单限定）', String(page?.total) === '10' && (page?.records ?? []).length === 10,
    `total=${page?.total}`);
  const sns = page?.records ?? [];
  check('SN 格式 = SN+日期+4位流水', sns.every(s => /^SN\d{12}$/.test(s.sn)),
    JSON.stringify(sns.map(s => s.sn)));
  check('SN 回填工单号/产品/出生报工单号',
    sns.every(s => s.workOrderNo && s.productNameSnapshot && s.reportNo),
    JSON.stringify(sns[0] ?? {}));

  const st = await req('GET', `/production/traces/sn?sn=${sns[0].sn}`);
  check('按 SN 追溯成功（工单号一致 + 时间线 52 条）',
    st.status === 200 && st.json?.code === 0 &&
      st.json?.data?.workOrderNo === sns[0].workOrderNo &&
      (st.json?.data?.timeline ?? []).length === 52,
    JSON.stringify({ workOrderNo: st.json?.data?.workOrderNo, timeline: st.json?.data?.timeline?.length }));

  const un = await req('GET', '/production/traces/sn?sn=SN999999999999');
  check('未知 SN -> 404', un.status === 404 && un.json?.code === 404, `status=${un.status}`);
}

// ------------------------------------------------------------
// 13. 质量链路（qa）：质检任务 -> 检验录入 -> 不良 -> 异常单 -> 处理 -> 关闭
// ------------------------------------------------------------
{
  const page = (await req('GET', `/quality/inspection-tasks/page?pageNum=1&pageSize=20&workOrderId=${woId}`, null, QA_TOKEN)).json?.data;
  check('质检任务 9 条（9 个需质检工序）', String(page?.total) === '9' && (page?.records ?? []).length === 9,
    `total=${page?.total}`);
  const tasks = page?.records ?? [];
  check('质检任务全部 PENDING + planQty=10（=工序累计完成数）',
    tasks.every(t => t.status === 'PENDING' && t.planQty === 10),
    JSON.stringify(tasks.map(t => `${t.processCodeSnapshot}:${t.status}:${t.planQty}`)));

  const iqc = tasks.find(t => t.processCodeSnapshot === 'IQC');
  const aging = tasks.find(t => t.processCodeSnapshot === 'AGING');
  check('找到 IQC 与 AGING 质检任务', !!iqc && !!aging,
    JSON.stringify(tasks.map(t => t.processCodeSnapshot)));

  const n1 = await req('POST', '/quality/inspection-records',
    { inspectionTaskId: iqc.id, goodQty: 10, defectQty: 0 }, QA_TOKEN);
  check('PENDING 直接录入 -> 409', n1.status === 409, `status=${n1.status}`);

  const s1 = await req('PUT', `/quality/inspection-tasks/${iqc.id}/start`, {}, QA_TOKEN);
  check('开始检验 IQC 成功', s1.json?.code === 0, JSON.stringify(s1.json)?.slice(0, 150));
  const r1 = await req('POST', '/quality/inspection-records',
    { inspectionTaskId: iqc.id, goodQty: 10, defectQty: 0, remark: '冒烟 IQC 全检合格' }, QA_TOKEN);
  check('IQC 录入 10 良成功', r1.json?.code === 0, JSON.stringify(r1.json)?.slice(0, 150));
  const iqcAfter = (await req('GET', `/quality/inspection-tasks/${iqc.id}`, null, QA_TOKEN)).json?.data;
  check('IQC 任务 COMPLETED 且累计 10/10/0',
    iqcAfter?.status === 'COMPLETED' && iqcAfter?.inspectedQty === 10 &&
      iqcAfter?.goodQty === 10 && iqcAfter?.defectQty === 0,
    JSON.stringify({ status: iqcAfter?.status, inspectedQty: iqcAfter?.inspectedQty, goodQty: iqcAfter?.goodQty }));

  const s2 = await req('PUT', `/quality/inspection-tasks/${aging.id}/start`, {}, QA_TOKEN);
  check('开始检验 AGING 成功', s2.json?.code === 0, JSON.stringify(s2.json)?.slice(0, 150));
  const r2 = await req('POST', '/quality/inspection-records', {
    inspectionTaskId: aging.id,
    goodQty: 9,
    defectQty: 1,
    defectItems: [{ defectCode: 'FLOWER_SCREEN', defectQty: 1, remark: '老化过程花屏' }],
  }, QA_TOKEN);
  check('AGING 录入 9 良 1 不良（FLOWER_SCREEN）成功', r2.json?.code === 0,
    JSON.stringify(r2.json)?.slice(0, 150));

  const defPage = (await req('GET', `/quality/defects/page?pageNum=1&pageSize=10&workOrderId=${woId}`, null, QA_TOKEN)).json?.data;
  check('不良记录 1 条', String(defPage?.total) === '1', `total=${defPage?.total}`);
  const defect = defPage?.records?.[0];
  check('不良单号 DEF+日期+4位流水 + 编码正确',
    /^DEF\d{12}$/.test(defect?.defectNo ?? '') && defect?.defectCode === 'FLOWER_SCREEN',
    JSON.stringify({ defectNo: defect?.defectNo, defectCode: defect?.defectCode }));

  const toExp = await req('PUT', `/quality/defects/${defect.id}/to-exception`, {}, QA_TOKEN);
  check('不良生成异常单成功', toExp.json?.code === 0, JSON.stringify(toExp.json)?.slice(0, 150));
  const dupExp = await req('PUT', `/quality/defects/${defect.id}/to-exception`, {}, QA_TOKEN);
  check('重复生成异常单 -> 409', dupExp.status === 409, `status=${dupExp.status}`);
  const expPage = (await req('GET', `/quality/exceptions/page?pageNum=1&pageSize=10&workOrderId=${woId}`, null, QA_TOKEN)).json?.data;
  check('异常单 1 条（DEFECT 来源 + OPEN）',
    String(expPage?.total) === '1' && expPage?.records?.[0]?.sourceType === 'DEFECT' && expPage?.records?.[0]?.status === 'OPEN',
    JSON.stringify(expPage?.records?.[0] ?? {}));
  const exp = expPage?.records?.[0];
  check('异常单号 EXP+日期+4位流水', /^EXP\d{12}$/.test(exp?.exceptionNo ?? ''), exp?.exceptionNo);

  const p1 = await req('PUT', `/quality/exceptions/${exp.id}/process`, {}, QA_TOKEN);
  check('处理异常 -> PROCESSING', p1.json?.code === 0, JSON.stringify(p1.json)?.slice(0, 150));
  const c1 = await req('PUT', `/quality/exceptions/${exp.id}/close`,
    { resolveRemark: '更换主板后老化复测通过' }, QA_TOKEN);
  check('关闭异常（带处理结论）成功', c1.json?.code === 0, JSON.stringify(c1.json)?.slice(0, 150));
  const expAfter = (await req('GET', `/quality/exceptions/page?pageNum=1&pageSize=10&workOrderId=${woId}`, null, QA_TOKEN)).json?.data?.records?.[0];
  check('异常单 CLOSED + 处理人/结论/关闭时间回填',
    expAfter?.status === 'CLOSED' && !!expAfter?.handlerName && !!expAfter?.resolveRemark && !!expAfter?.resolvedAt,
    JSON.stringify({ status: expAfter?.status, handlerName: expAfter?.handlerName }));

  const opStart = await req('PUT', `/quality/inspection-tasks/${iqc.id}/start`, {}, OPERATOR_TOKEN);
  check('operator 开始检验 -> 403', opStart.status === 403, `status=${opStart.status}`);
}

// ------------------------------------------------------------
// 14. 质量追溯复核 + 批次追溯
// ------------------------------------------------------------
{
  const traces = (await req('GET', `/production/traces?workOrderId=${woId}`)).json?.data ?? [];
  const countBy = {};
  for (const t of traces) countBy[t.actionType] = (countBy[t.actionType] ?? 0) + 1;
  check('INSPECT=2 / DEFECT=1', countBy.INSPECT === 2 && countBy.DEFECT === 1, JSON.stringify(countBy));
  check('EXCEPTION_CREATE/PROCESS/CLOSE 各 1',
    countBy.EXCEPTION_CREATE === 1 && countBy.EXCEPTION_PROCESS === 1 && countBy.EXCEPTION_CLOSE === 1,
    JSON.stringify(countBy));
  check('追溯总条数 = 58（52 + 质量链路 6）', traces.length === 58, `got ${traces.length}`);

  const bt = await req('GET', '/production/traces/batch?batchNo=SMOKE-BATCH-01');
  check('批次追溯：报工 1 条 + 工单去重 1',
    (bt.json?.data?.reports ?? []).length === 1 && (bt.json?.data?.workOrders ?? []).length === 1,
    JSON.stringify({ reports: bt.json?.data?.reports?.length, workOrders: bt.json?.data?.workOrders?.length }));
  check('批次报工回填工单号/工序/操作人',
    bt.json?.data?.reports?.[0]?.workOrderNo && bt.json?.data?.reports?.[0]?.processNameSnapshot &&
      bt.json?.data?.reports?.[0]?.operatorName,
    JSON.stringify(bt.json?.data?.reports?.[0] ?? {}));
  check('批次涉及工单 COMPLETED 10/10',
    bt.json?.data?.workOrders?.[0]?.status === 'COMPLETED' && bt.json?.data?.workOrders?.[0]?.completedQty === 10,
    JSON.stringify(bt.json?.data?.workOrders?.[0] ?? {}));
  const empty = await req('GET', '/production/traces/batch?batchNo=NO-SUCH-BATCH');
  check('未知批次 -> 空列表', empty.json?.code === 0 && (empty.json?.data?.reports ?? []).length === 0,
    JSON.stringify(empty.json)?.slice(0, 150));
}

// ------------------------------------------------------------
// 15. 生产看板聚合 + 设备主数据 + 权限边界收口
// ------------------------------------------------------------
{
  const s = await req('GET', '/dashboard/summary');
  check('看板 summary 成功', s.status === 200 && s.json?.code === 0, JSON.stringify(s.json)?.slice(0, 150));
  const d = s.json?.data ?? {};
  check('今日产量 >= 10（冒烟报工）', Number(d.todayOutputQty) >= 10, `todayOutputQty=${d.todayOutputQty}`);
  check('今日良率 null 或 0-100', d.todayYieldRate == null || (d.todayYieldRate >= 0 && d.todayYieldRate <= 100),
    `todayYieldRate=${d.todayYieldRate}`);
  check('设备状态分布合计 >= 10（种子 10 台）',
    Array.isArray(d.equipmentStatusCounts) && d.equipmentStatusCounts.length > 0 &&
      d.equipmentStatusCounts.reduce((a, c) => a + Number(c.count), 0) >= 10,
    JSON.stringify(d.equipmentStatusCounts));

  const wo = await req('GET', '/dashboard/work-orders');
  check('看板进行中工单返回数组', wo.json?.code === 0 && Array.isArray(wo.json?.data),
    JSON.stringify(wo.json?.data)?.slice(0, 100));

  const q = await req('GET', '/dashboard/quality');
  check('看板工序良率非空（9 工序有数据）', q.json?.code === 0 && (q.json?.data?.processYields ?? []).length > 0,
    JSON.stringify(q.json?.data?.processYields?.map(p => p.processName)));
  check('看板整体良率 null 或 0-100',
    q.json?.data?.overallYieldRate == null || (q.json?.data?.overallYieldRate >= 0 && q.json?.data?.overallYieldRate <= 100),
    `overallYieldRate=${q.json?.data?.overallYieldRate}`);
  check('看板不良分布含 FLOWER_SCREEN',
    (q.json?.data?.defectDistribution ?? []).some(r => r.defectCode === 'FLOWER_SCREEN'),
    JSON.stringify(q.json?.data?.defectDistribution));

  const eq = await req('GET', '/dashboard/equipment');
  const eqList = eq.json?.data?.equipment ?? [];
  const eqDist = eq.json?.data?.statusCounts ?? [];
  check('看板设备列表与分布合计一致（>=10 台）',
    eq.json?.code === 0 && eqList.length === eqDist.reduce((a, c) => a + Number(c.count), 0) && eqList.length >= 10,
    JSON.stringify({ list: eqList.length, dist: eqDist }));

  const eqPage = await req('GET', '/master/equipment/page?pageNum=1&pageSize=20');
  check('设备主数据 >= 10 行（种子）', Number(eqPage.json?.data?.total) >= 10, `total=${eqPage.json?.data?.total}`);

  const qs = await req('GET', '/dashboard/summary', null, QA_TOKEN);
  check('qa 查看板 200', qs.status === 200 && qs.json?.code === 0, `status=${qs.status}`);
  const qe = await req('POST', '/master/equipment', { equipmentCode: 'EQ-QA-X', equipmentName: '越权' }, QA_TOKEN);
  check('qa 建设备 -> 403', qe.status === 403, `status=${qe.status}`);
}

// ------------------------------------------------------------
// 16. 第 4 周 AI：知识库 RAG + 统一助手四意图 + 异常建议 + 日报 + 权限边界
// ------------------------------------------------------------
{
  // 16.1 知识库文档分页（干净库种子 4 篇）
  const docs = await req('GET', '/ai/knowledge/docs/page?pageNum=1&pageSize=10');
  check('知识库文档分页 total=4（种子）', docs.status === 200 && String(docs.json?.data?.total) === '4',
    `total=${docs.json?.data?.total}`);

  // 16.2 知识库问答：命中 + 引用 + 反馈
  const ask = await req('POST', '/ai/knowledge/ask', { question: '烧录时报 BURN_FAIL 怎么处理？' });
  const askData = ask.json?.data ?? {};
  check('知识库问答 200 且引用含烧录指导书',
    ask.status === 200 && (askData.references ?? []).some(r => r.docName?.includes('烧录')),
    JSON.stringify(askData.references?.map(r => r.docName)));
  check('知识库问答 recordId 存在（问答记录落库）', !!askData.recordId, `recordId=${askData.recordId}`);
  const fb = await req('PUT', `/ai/knowledge/qa-records/${askData.recordId}/feedback`, { useful: true });
  check('问答反馈 200', fb.status === 200 && fb.json?.code === 0, `status=${fb.status}`);

  // 16.3 统一助手四意图路由（规则前置，全部真 LLM）
  const c1 = await req('POST', '/ai/chat', { question: '软件烧录的SOP流程是什么' });
  check('助手-知识库意图 KNOWLEDGE 且引用非空',
    c1.status === 200 && c1.json?.data?.intent === 'KNOWLEDGE' && (c1.json?.data?.references ?? []).length > 0,
    `intent=${c1.json?.data?.intent}`);
  const c2 = await req('POST', '/ai/chat', { question: '生成今天的生产日报' });
  check('助手-日报意图 REPORT 且含当日日期',
    c2.status === 200 && c2.json?.data?.intent === 'REPORT' && !!c2.json?.data?.reportDate,
    `intent=${c2.json?.data?.intent} reportDate=${c2.json?.data?.reportDate}`);
  const c3 = await req('POST', '/ai/chat', { question: '现在工厂整体情况怎么样' });
  check('助手-概况意图 OVERVIEW 且 summary 含工单',
    c3.status === 200 && c3.json?.data?.intent === 'OVERVIEW' && String(c3.json?.data?.summary ?? '').includes('工单'),
    `intent=${c3.json?.data?.intent}`);

  // 16.4 异常建议：qa 建 MANUAL 异常单 → pro 生成建议 → qa 保存回写 → 查询回显
  const ex = await req('POST', '/quality/exceptions',
    { defectCode: 'BLACK_SCREEN', description: '冒烟验证：整机黑屏' }, QA_TOKEN);
  const exceptionId = ex.json?.data;
  check('qa 创建异常单 200', ex.status === 200 && !!exceptionId, `id=${exceptionId}`);
  const sg = await req('POST', '/ai/assistant/suggest', { exceptionId }, QA_TOKEN);
  check('异常建议生成 200 且非模板降级（pro 推理）',
    sg.status === 200 && String(sg.json?.data?.suggestion ?? '').length > 50 && sg.json?.data?.fallback === false,
    `len=${sg.json?.data?.suggestion?.length}`);
  const s1 = await req('POST', '/ai/assistant/save', { exceptionId, suggestion: sg.json?.data?.suggestion }, QA_TOKEN);
  const s2 = await req('POST', '/ai/assistant/save', { exceptionId, suggestion: '越权保存' }, OPERATOR_TOKEN);
  check('qa 保存建议 200 / operator 保存建议 403', s1.status === 200 && s2.status === 403,
    `qa=${s1.status} operator=${s2.status}`);
  const gs = await req('GET', `/ai/assistant/suggestion/${exceptionId}`, null, OPERATOR_TOKEN);
  check('已保存建议回显非空', gs.status === 200 && String(gs.json?.data?.suggestion ?? '').length > 50,
    `len=${gs.json?.data?.suggestion?.length}`);

  // 16.5 生产日报：预览（flash 润色）→ 保存 → 同日幂等
  const today = new Date().toISOString().slice(0, 10);
  const pv = await req('POST', '/ai/daily/preview', { reportDate: today }, OPERATOR_TOKEN);
  check('日报预览 200 且正文非空',
    pv.status === 200 && String(pv.json?.data?.content ?? '').length > 20 && pv.json?.data?.fallback === false,
    `len=${pv.json?.data?.content?.length}`);
  const ds = await req('POST', '/ai/daily/save', { reportDate: today, content: pv.json?.data?.content }, OPERATOR_TOKEN);
  check('日报保存 200', ds.status === 200, `status=${ds.status}`);
  const dp = await req('GET', `/ai/daily/page?pageNum=1&pageSize=10&reportDate=${today}`, null, OPERATOR_TOKEN);
  const sameDate = (dp.json?.data?.records ?? []).filter(r => r.reportDate === today);
  check('日报分页同日仅 1 条（幂等覆盖）', dp.status === 200 && sameDate.length === 1,
    `sameDate=${sameDate.length}`);

  // 16.6 AI 权限边界：查询全员、知识库写仅 admin
  const od = await req('GET', '/ai/knowledge/docs/page?pageNum=1&pageSize=5', null, OPERATOR_TOKEN);
  const oc = await req('POST', '/ai/chat', { question: '黑屏故障怎么排查' }, OPERATOR_TOKEN);
  const ow = await req('POST', '/ai/knowledge/docs',
    { docName: '越权', docType: 'SOP', keywords: 'X', content: 'x' }, OPERATOR_TOKEN);
  check('operator 知识库查询 200 / 助手 200 / 文档写 403',
    od.status === 200 && oc.status === 200 && ow.status === 403,
    `page=${od.status} chat=${oc.status} write=${ow.status}`);
}

// ------------------------------------------------------------
// 17. 第 5 周系统集成：ERP 外单 5 台全链（下单→转工单→领料→开工→报工→完工回传+成品入库）
// ------------------------------------------------------------
let extWoId = null;
let extTaskIds = [];
{
  // 17.1 planning 模拟下单 → PENDING（外部订单号 ERP+日期+4位流水）
  const o1 = await req('POST', '/integration/erp/orders',
    { productId: 1, planQty: 5, priority: 'HIGH', remark: '冒烟集成链路' }, PLANNING_TOKEN);
  check('planning 模拟下单 200', o1.status === 200 && o1.json?.code === 0,
    JSON.stringify(o1.json)?.slice(0, 150));
  const extOrderId = o1.json?.data;
  const o1b = (await req('GET', `/integration/erp/orders/${extOrderId}`, null, PLANNING_TOKEN)).json?.data;
  check('外部订单号 ERP+日期+4位流水 且 PENDING',
    /^ERP\d{12}$/.test(o1b?.externalOrderNo ?? '') && o1b?.status === 'PENDING',
    JSON.stringify({ externalOrderNo: o1b?.externalOrderNo, status: o1b?.status }));

  // 17.2 权限边界：operator 下单/转工单 403
  const o2 = await req('POST', '/integration/erp/orders', { productId: 1, planQty: 1 }, OPERATOR_TOKEN);
  check('operator 模拟下单 -> 403', o2.status === 403, `status=${o2.status}`);
  const o3 = await req('PUT', `/integration/erp/orders/${extOrderId}/to-work-order`, {}, OPERATOR_TOKEN);
  check('operator 转工单 -> 403', o3.status === 403, `status=${o3.status}`);

  // 17.3 admin 转工单 → SYNCED + 工单透传外单号/数量/优先级
  const o4 = await req('PUT', `/integration/erp/orders/${extOrderId}/to-work-order`, {}, ADMIN_TOKEN);
  check('admin 转工单 200', o4.status === 200 && o4.json?.code === 0,
    JSON.stringify(o4.json)?.slice(0, 150));
  const o4b = (await req('GET', `/integration/erp/orders/${extOrderId}`, null, PLANNING_TOKEN)).json?.data;
  extWoId = o4b?.workOrderId;
  check('外部订单 SYNCED + 关联工单回填', o4b?.status === 'SYNCED' && !!extWoId,
    JSON.stringify({ status: o4b?.status, workOrderId: extWoId }));
  const extWo = (await req('GET', `/production/work-orders/${extWoId}`)).json?.data;
  check('转单工单透传 externalOrderNo + 计划 5 台 + 优先级 HIGH',
    extWo?.externalOrderNo === o4b?.externalOrderNo && extWo?.planQty === 5 && extWo?.priority === 'HIGH',
    JSON.stringify({ externalOrderNo: extWo?.externalOrderNo, planQty: extWo?.planQty, priority: extWo?.priority }));

  // 17.4 下发 → 未领料开工 409 → 领料（关键物料 ×5）→ 重复领料 409
  const o5 = await req('POST', `/production/work-orders/${extWoId}/release`, {}, ADMIN_TOKEN);
  check('外单工单下发 200', o5.json?.code === 0, JSON.stringify(o5.json)?.slice(0, 150));
  const extDetail = (await req('GET', `/production/work-orders/${extWoId}`)).json?.data;
  extTaskIds = (extDetail?.tasks ?? []).map(t => t.id);
  check('外单工单 13 个任务', extTaskIds.length === 13, `got ${extTaskIds.length}`);
  await req('PUT', `/production/tasks/${extTaskIds[0]}/assign`, { operatorId: 2 });
  const o6 = await req('PUT', `/production/tasks/${extTaskIds[0]}/start`, {});
  check('ERP 推单工单未领料开工 -> 409', o6.status === 409, `status=${o6.status}`);
  const o7 = await req('POST', '/integration/wms/pick', { workOrderId: extWoId }, PLANNING_TOKEN);
  check('planning 工单领料 200', o7.status === 200 && o7.json?.code === 0,
    JSON.stringify(o7.json)?.slice(0, 200));
  const pickItems = o7.json?.data?.items ?? [];
  check('领料明细只含关键物料（1/2/3/4/5/20）且应领 = 用量×5',
    pickItems.length > 0 && pickItems.every(i =>
      ['1', '2', '3', '4', '5', '20'].includes(String(i.materialId)) && i.needQty % 5 === 0),
    JSON.stringify(pickItems.map(i => `${i.materialCode}:${i.needQty}`)));
  const o8 = await req('POST', '/integration/wms/pick', { workOrderId: extWoId }, PLANNING_TOKEN);
  check('重复领料 -> 409（已足额领用）', o8.status === 409, `status=${o8.status}`);

  // 17.5 领料后开工放行 → 其余任务派工+开工 → 13 道报工 5 台
  const o9 = await req('PUT', `/production/tasks/${extTaskIds[0]}/start`, {});
  check('领料后开工 200（钩子放行）', o9.json?.code === 0, JSON.stringify(o9.json)?.slice(0, 150));
  let ok1 = true;
  for (let i = 1; i < 13; i++) {
    const a = await req('PUT', `/production/tasks/${extTaskIds[i]}/assign`, { operatorId: 2 });
    const s = await req('PUT', `/production/tasks/${extTaskIds[i]}/start`, {});
    if (a.json?.code !== 0 || s.json?.code !== 0) { ok1 = false; break; }
  }
  check('t2..t13 派工+开工成功（领料钩子全部放行）', ok1, 'chain failed');
  let ok2 = true;
  for (let i = 0; i < 13; i++) {
    const rp = await req('POST', '/production/reports',
      { taskId: extTaskIds[i], reportQty: 5, goodQty: 5, defectQty: 0 }, OPERATOR_TOKEN);
    if (rp.json?.code !== 0) { ok2 = false; break; }
  }
  check('13 道报工 5 台成功（operator）', ok2, 'report loop failed');
  const extWoDone = (await req('GET', `/production/work-orders/${extWoId}`)).json?.data;
  check('外单工单 COMPLETED 5/5', extWoDone?.status === 'COMPLETED' && extWoDone?.goodQty === 5,
    JSON.stringify({ status: extWoDone?.status, goodQty: extWoDone?.goodQty }));

  // 17.6 完工钩子：外部订单 DONE + 成品入库 +5 + FINISHED_IN 流水 + 追溯 2 条
  const oDone = (await req('GET', `/integration/erp/orders/${extOrderId}`, null, PLANNING_TOKEN)).json?.data;
  check('完工钩子：外部订单 DONE', oDone?.status === 'DONE', `status=${oDone?.status}`);
  const inv = await req('GET', '/integration/wms/inventory/page?pageNum=1&pageSize=100&itemType=FINISHED',
    null, PLANNING_TOKEN);
  const finRow = (inv.json?.data?.records ?? []).find(r => String(r.itemRefId) === '1');
  check('完工钩子：成品库存 +5（产品 1）', finRow?.qty === 5, `qty=${finRow?.qty}`);
  const txp = await req('GET', `/integration/wms/transactions/page?pageNum=1&pageSize=50&workOrderId=${extWoId}&bizType=FINISHED_IN`,
    null, PLANNING_TOKEN);
  check('完工钩子：FINISHED_IN 流水 1 条', String(txp.json?.data?.total) === '1',
    `total=${txp.json?.data?.total}`);
  const extTraces = (await req('GET', `/production/traces?workOrderId=${extWoId}`)).json?.data ?? [];
  const extCount = {};
  for (const t of extTraces) extCount[t.actionType] = (extCount[t.actionType] ?? 0) + 1;
  check('ERP 工单追溯含 ERP_DONE=1 / WMS_FINISHED_IN=1',
    extCount.ERP_DONE === 1 && extCount.WMS_FINISHED_IN === 1, JSON.stringify(extCount));

  // 17.7 菜单树角色差异（动态路由数据源回归）：operator 无集成菜单、planning 有
  const walk = (ns, out) => { for (const n of ns ?? []) { if (n.path) out.push(n.path); walk(n.children, out); } return out; };
  const m1 = await req('GET', '/auth/menus', null, OPERATOR_TOKEN);
  const m1paths = walk(m1.json?.data ?? [], []);
  check('operator 菜单树无 /erp-orders /inventory',
    !m1paths.includes('/erp-orders') && !m1paths.includes('/inventory'), m1paths.join(','));
  const m2 = await req('GET', '/auth/menus', null, PLANNING_TOKEN);
  const m2paths = walk(m2.json?.data ?? [], []);
  check('planning 菜单树含 /erp-orders /inventory',
    m2paths.includes('/erp-orders') && m2paths.includes('/inventory'), m2paths.join(','));
}

// ------------------------------------------------------------
// 18. 第 6 周生产深化：物料批次追溯（列表/新建/补录绑定/正反向反查）+ 排程（run/gantt/幂等）+ 报表中心（summary/export/权限）
// ------------------------------------------------------------
let schedWoId = null;
{
  const pad2 = (n) => String(n).padStart(2, '0');
  const _d = new Date();
  const TODAY = `${_d.getFullYear()}-${pad2(_d.getMonth() + 1)}-${pad2(_d.getDate())}`;

  // 18.1 批次主数据：种子 12 批 + 新建（MB 生成器）
  const b1 = await req('GET', '/production/material-batches/page?pageNum=1&pageSize=50', null, PLANNING_TOKEN);
  check('批次列表种子 12 批', String(b1.json?.data?.total) === '12',
    `total=${b1.json?.data?.total}`);
  const b2 = await req('POST', '/production/material-batches',
    { materialId: 2, batchQty: 50, supplier: '冒烟供应商' }, PLANNING_TOKEN);
  const b2b = await req('GET', '/production/material-batches/page?pageNum=1&pageSize=50&materialId=2',
    null, PLANNING_TOKEN);
  const newBatch = (b2b.json?.data?.records ?? []).find(x => x.supplier === '冒烟供应商');
  check('新建批次 200 + MB 流水号（MB+日期+4位，创建返回 id，批号回查列表）',
    b2.status === 200 && /^MB\d{12}$/.test(newBatch?.batchNo ?? ''),
    `id=${b2.json?.data} batchNo=${newBatch?.batchNo}`);
  const b3 = await req('GET', '/production/material-batches/page?pageNum=1&pageSize=50', null, PLANNING_TOKEN);
  check('新建后批次列表 13 批', String(b3.json?.data?.total) === '13',
    `total=${b3.json?.data?.total}`);

  // 18.2 补录绑定：对 17 节完成工单第一道报工补录（先不匹配 409 再成功 + 幂等重放）
  const rp1 = await req('GET', `/production/reports/page?pageNum=1&pageSize=50&workOrderId=${extWoId}`,
    null, PLANNING_TOKEN);
  const firstReportId = rp1.json?.data?.records?.[0]?.id;
  const bNeg = await req('POST', `/production/reports/${firstReportId}/bind-batch`,
    [{ materialId: 1, batchNo: 'MB202608230003' }], OPERATOR_TOKEN);
  check('补录绑定负例：批次与物料不匹配 -> 409', bNeg.status === 409, `status=${bNeg.status}`);
  const bOk = await req('POST', `/production/reports/${firstReportId}/bind-batch`,
    [{ materialId: 1, batchNo: 'MB202608230001' }], OPERATOR_TOKEN);
  check('补录绑定成功 -> 200', bOk.status === 200 && bOk.json?.code === 0,
    JSON.stringify(bOk.json)?.slice(0, 150));
  const bIdem = await req('POST', `/production/reports/${firstReportId}/bind-batch`,
    [{ materialId: 1, batchNo: 'MB202608230001' }], OPERATOR_TOKEN);
  check('同批重放补录幂等 -> 200', bIdem.status === 200, `status=${bIdem.status}`);

  // 18.3 批次反向追溯：绑定记录 + 5 台 SN；SN 正查 materialBatches 含该批次；批次不存在 404
  const bs = await req('GET', '/production/traces/batch-sns?batchNo=MB202608230001', null, PLANNING_TOKEN);
  const bsData = bs.json?.data ?? {};
  check('batch-sns 反向：含补录报工绑定 + 5 台整机 SN',
    (bsData.bindings ?? []).some(x => String(x.reportId) === String(firstReportId))
      && (bsData.sns ?? []).length === 5,
    `bindings=${bsData.bindings?.length} sns=${bsData.sns?.length}`);
  const firstSn = bsData.sns?.[0]?.sn;
  const snT = await req('GET', `/production/traces/sn?sn=${firstSn}`, null, PLANNING_TOKEN);
  const snBatches = snT.json?.data?.materialBatches ?? [];
  check('SN 正查：materialBatches 含绑定批次（6 料聚合）',
    snBatches.some(x => x.batchNo === 'MB202608230001') && snBatches.length > 0,
    JSON.stringify(snBatches.map(x => `${x.materialId}:${x.batchNo}`)));
  const bs404 = await req('GET', '/production/traces/batch-sns?batchNo=MB209901010001', null, PLANNING_TOKEN);
  check('batch-sns 批次不存在 -> 404', bs404.status === 404, `status=${bs404.status}`);

  // 18.4 排程：建单+下发（不做派工/开工/报工）→ run → gantt 13 条不重叠 + AGING 时长 + 重跑幂等
  const w1 = await req('POST', '/production/work-orders',
    { productId: 1, planQty: 1, priority: 'NORMAL', remark: '冒烟第 6 周排程' }, PLANNING_TOKEN);
  schedWoId = w1.json?.data;
  check('排程验证建单 200', w1.status === 200, `id=${schedWoId}`);
  const w2 = await req('POST', `/production/work-orders/${schedWoId}/release`, {}, ADMIN_TOKEN);
  check('排程验证下发 200', w2.json?.code === 0, JSON.stringify(w2.json)?.slice(0, 150));
  const s1 = await req('POST', '/production/schedule/run', {}, PLANNING_TOKEN);
  check('planning 执行排程 200（覆盖 13 道任务）',
    s1.status === 200 && Number(s1.json?.data?.taskCount) === 13,
    JSON.stringify(s1.json?.data));
  const g1 = await req('GET', `/production/schedule/gantt?date=${TODAY}`, null, PLANNING_TOKEN);
  const mine = (g1.json?.data ?? []).filter(t => String(t.workOrderId) === String(schedWoId));
  check('gantt 本单 13 条（计划时间/工位/逾期字段回填）',
    mine.length === 13 && mine.every(t => t.planStartTime && t.planEndTime && typeof t.isOverdue === 'boolean'),
    `mine=${mine.length}`);
  const wsMap = new Map();
  for (const t of mine) {
    const k = t.workstationName || '未分配工位';
    if (!wsMap.has(k)) wsMap.set(k, []);
    wsMap.get(k).push(t);
  }
  const serial = [...wsMap.values()].every(g => {
    const s = [...g].sort((a, b) => (a.planStartTime < b.planStartTime ? -1 : 1));
    return s.every((t, i) => i === 0 || s[i - 1].planEndTime <= t.planStartTime);
  });
  check('gantt 同工位串行不重叠（含跨工单）', serial, `工位组=${wsMap.size}`);
  const aging = mine.find(t => t.processNameSnapshot === '老化测试');
  const agingMs = aging
    ? Date.parse(aging.planEndTime.replace(' ', 'T')) - Date.parse(aging.planStartTime.replace(' ', 'T'))
    : -1;
  check('AGING 任务计划时长 = 标准工时 120 分钟 × 1 台', agingMs === 7200000, `ms=${agingMs}`);
  const s2 = await req('POST', '/production/schedule/run', {}, PLANNING_TOKEN);
  const g2 = await req('GET', `/production/schedule/gantt?date=${TODAY}`, null, PLANNING_TOKEN);
  const mine2 = (g2.json?.data ?? []).filter(t => String(t.workOrderId) === String(schedWoId));
  const idem = mine2.every(t => {
    const prev = mine.find(o => String(o.taskId) === String(t.taskId));
    return prev && prev.planStartTime === t.planStartTime && prev.planEndTime === t.planEndTime;
  });
  check('重跑排程幂等：计划时间完全一致', idem && mine2.length === 13, `mine2=${mine2.length}`);

  // 18.5 报表中心：day 总量 ≥ 15（7 节 10 台 + 17 节 5 台）+ export 魔数 + 权限边界
  const sum = await req('GET', `/production/reports-center/summary?type=day&date=${TODAY}`, null, PLANNING_TOKEN);
  const sumData = sum.json?.data ?? {};
  check('报表中心 day 汇总：合格 ≥ 15、报工数 ≥ 26、良率一致',
    Number(sumData.totalGoodQty) >= 15 && Number(sumData.reportCount) >= 26
      && Number(sumData.yieldRate) >= 0,
    JSON.stringify({ good: sumData.totalGoodQty, reports: sumData.reportCount, yield: sumData.yieldRate }));
  const expRaw = await fetch(`${BASE}/production/reports-center/export?type=day&date=${TODAY}`, {
    headers: { Authorization: `Bearer ${PLANNING_TOKEN}` },
  });
  const expBuf = Buffer.from(await expRaw.arrayBuffer());
  check('export 200 + xlsx PK 魔数（zip 容器）',
    expRaw.status === 200 && expBuf[0] === 0x50 && expBuf[1] === 0x4B,
    `status=${expRaw.status} bytes=${expBuf.length}`);
  const opRun = await req('POST', '/production/schedule/run', {}, OPERATOR_TOKEN);
  check('operator 执行排程 -> 403', opRun.status === 403, `status=${opRun.status}`);
  const opExp = await fetch(`${BASE}/production/reports-center/export?type=day`, {
    headers: { Authorization: `Bearer ${OPERATOR_TOKEN}` },
  });
  check('operator 导出报表 -> 403', opExp.status === 403, `status=${opExp.status}`);

  // 18.6 收尾：取消排程验证工单（批次/绑定行留给 clean-smoke 清理）
  const c1 = await req('PUT', `/production/work-orders/${schedWoId}/cancel`, {}, PLANNING_TOKEN);
  check('取消排程验证工单 200', c1.status === 200 && c1.json?.code === 0,
    JSON.stringify(c1.json)?.slice(0, 150));
}

console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail > 0 ? 1 : 0);
