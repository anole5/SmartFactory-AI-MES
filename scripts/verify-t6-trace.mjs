// T6 追溯查询验证：SN 分页 + 按 SN 追溯 + 未知 SN 404 + 按批次追溯 + 权限边界
// 自包含可重跑：externalOrderNo=productBatchNo=V6-BATCH-1 的工单已存在（COMPLETED）则复用，
// 否则创建 → 下发 → 派工 → 13 工序各报工 3 台 → 最后一道完成自动铸 3 台 SN
// 运行：node scripts/verify-t6-trace.mjs（后端须已启动）

const BASE = 'http://localhost:8080/api';
const BATCH_NO = 'V6-BATCH-1';
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
const put = (p, b, t) => call('PUT', p, b, t);
const get = (p, t) => call('GET', p, undefined, t);

const admin = await post('/auth/login', { username: 'admin', password: 'admin123' });
const adminToken = admin.body.data?.token;
const operator = await post('/auth/login', { username: 'operator', password: 'operator123' });
const operatorToken = operator.body.data?.token;
ok('admin 登录成功', admin.body.code === 0 && !!adminToken);
ok('operator 登录成功', operator.body.code === 0 && !!operatorToken);

// 1. 找到或创建 V6-BATCH-1 工单（externalOrderNo 手填同值，供 keyword 检索复用）
let woId = null;
let woNo = null;
let created = false;
{
  const page = await get(`/production/work-orders/page?keyword=${BATCH_NO}&pageSize=50`, adminToken);
  const existing = page.body.data?.records?.find(w => w.externalOrderNo === BATCH_NO);
  if (existing) {
    if (existing.status !== 'COMPLETED') {
      ok('复用 V6-BATCH-1 工单（状态 COMPLETED）', false, `存在但状态=${existing.status}，请清理残留后重跑`);
    } else {
      ok('复用已有 V6-BATCH-1 工单（COMPLETED）', true, `wo=${existing.workOrderNo}`);
      woId = existing.id;
      woNo = existing.workOrderNo;
    }
  } else {
    const r1 = await post('/production/work-orders', {
      productId: 1,
      planQty: 3,
      externalOrderNo: BATCH_NO,
      priority: 'NORMAL',
      planStartTime: '2026-08-23 09:00:00',
      planEndTime: '2026-08-23 18:00:00',
    }, adminToken);
    ok('创建 V6-BATCH-1 工单成功（DRAFT）', r1.body.code === 0, JSON.stringify(r1.body).slice(0, 120));
    woId = r1.body.data;
    created = true;
    const r2 = await post(`/production/work-orders/${woId}/release`, {}, adminToken);
    ok('下发成功 -> RELEASED', r2.body.code === 0, JSON.stringify(r2.body).slice(0, 120));
    const wo = (await get(`/production/work-orders/${woId}`, adminToken)).body.data;
    woNo = wo.workOrderNo;
    const tasks = wo.tasks ?? [];
    ok('按路线生成 13 个工序任务', tasks.length === 13, `got ${tasks.length}`);

    // 派工（admin 执行）
    let assignOk = true;
    for (const t of tasks) {
      const r = await put(`/production/tasks/${t.id}/assign`, { operatorId: 2 }, adminToken);
      if (r.body.code !== 0) { assignOk = false; break; }
    }
    ok('13 个任务派工成功 -> ASSIGNED', assignOk, 'assign loop failed');

    // 开工 + 报工（operator 执行，reportQty=3/good=3，最后一道完成自动铸 3 台 SN）
    let chainOk = true;
    for (const t of tasks) {
      const st = await put(`/production/tasks/${t.id}/start`, {}, operatorToken);
      const rp = await post('/production/reports',
        { taskId: t.id, reportQty: 3, goodQty: 3, defectQty: 0, productBatchNo: BATCH_NO }, operatorToken);
      if (st.body.code !== 0 || rp.body.code !== 0) { chainOk = false; break; }
    }
    ok('13 个任务开工+报工 3 台全部成功', chainOk, 'chain loop failed');
  }
}

const wo = (await get(`/production/work-orders/${woId}`, adminToken)).body.data;
ok('工单最终状态 COMPLETED', wo?.status === 'COMPLETED', `status=${wo?.status}`);

// 2. SN 分页：3 条 + 格式 + 回填
{
  const page = await get(`/production/sns/page?workOrderId=${woId}&pageSize=10`, adminToken);
  const data = page.body.data;
  const records = data?.records ?? [];
  ok('SN 分页 total=3', String(data?.total) === '3' && records.length === 3, `total=${data?.total} got=${records.length}`);
  ok('SN 格式 /^SN\\d{12}$/', records.every(r => /^SN\d{12}$/.test(r.sn)), JSON.stringify(records.map(r => r.sn)));
  ok('SN 回填工单号/出生报工单号/产品快照',
    records.every(r => r.workOrderNo === woNo && !!r.reportNo && !!r.productNameSnapshot),
    JSON.stringify(records[0] ?? {}));

  // 3. 按 SN 追溯：工单号匹配 + 出生信息 + 时间线
  const sn = records[0].sn;
  const tr = await get(`/production/traces/sn?sn=${sn}`, adminToken);
  const t = tr.body.data;
  ok('按 SN 查 code=0 + 工单号匹配', tr.body.code === 0 && t?.workOrderNo === woNo, `wo=${t?.workOrderNo}`);
  ok('SN 出生信息回填（产品快照/出生报工单号/工单状态）',
    t?.sn === sn && !!t?.productNameSnapshot && !!t?.reportNo && t?.workOrderStatus === 'COMPLETED',
    JSON.stringify({ sn: t?.sn, reportNo: t?.reportNo, s: t?.workOrderStatus }));
  const timeline = t?.timeline ?? [];
  const countBy = {};
  for (const x of timeline) countBy[x.actionType] = (countBy[x.actionType] ?? 0) + 1;
  ok('时间线含 CREATE/REPORT', countBy.CREATE === 1 && countBy.REPORT === 13,
    JSON.stringify({ CREATE: countBy.CREATE, REPORT: countBy.REPORT }));
  ok('时间线含 9 个 INSPECT_TASK（需质检工序自动生成）', countBy.INSPECT_TASK === 9,
    `got ${countBy.INSPECT_TASK}`);

  // 4. 未知 SN -> 404
  const nf = await get('/production/traces/sn?sn=SN209901019999', adminToken);
  ok('未知 SN 404', nf.status === 404 && nf.body.code === 404, `status=${nf.status}`);
}

// 5. 按批次追溯：reports 13 条回填 + workOrders 去重 1
{
  const bt = await get(`/production/traces/batch?batchNo=${BATCH_NO}`, adminToken);
  const reports = bt.body.data?.reports ?? [];
  const workOrders = bt.body.data?.workOrders ?? [];
  ok('批次查 code=0 + reports=13 条', bt.body.code === 0 && reports.length === 13, `got ${reports.length}`);
  ok('批次报工含工单号/工序/操作人回填',
    reports.every(r => r.workOrderNo === woNo && !!r.processNameSnapshot && !!r.operatorName && r.productBatchNo === BATCH_NO),
    JSON.stringify(reports[0] ?? {}));
  ok('批次工单去重 = 1 + COMPLETED',
    workOrders.length === 1 && workOrders[0].workOrderNo === woNo && workOrders[0].status === 'COMPLETED',
    JSON.stringify(workOrders.map(w => `${w.workOrderNo}/${w.status}`)));

  // 6. 权限：operator 有 production:trace:query（菜单 204）可查三入口
  const ob = await get(`/production/traces/batch?batchNo=${BATCH_NO}`, operatorToken);
  ok('operator 查批次追溯 200', ob.status === 200 && ob.body.code === 0, `status=${ob.status}`);
  const sn = (await get(`/production/sns/page?workOrderId=${woId}&pageSize=1`, adminToken)).body.data.records[0].sn;
  const os = await get(`/production/traces/sn?sn=${sn}`, operatorToken);
  ok('operator 按 SN 追溯 200', os.status === 200 && os.body.code === 0, `status=${os.status}`);
  const op = await get(`/production/sns/page?workOrderId=${woId}`, operatorToken);
  ok('operator 查 SN 分页 200', op.status === 200 && op.body.code === 0, `status=${op.status}`);

  // 7. 未知批次 -> 200 空列表（前端展示"无记录"而非报错）
  const nb = await get('/production/traces/batch?batchNo=NO-SUCH-BATCH-999', adminToken);
  ok('未知批次 200 + 空列表',
    nb.body.code === 0 && Array.isArray(nb.body.data?.reports) && nb.body.data.reports.length === 0
    && Array.isArray(nb.body.data?.workOrders) && nb.body.data.workOrders.length === 0,
    JSON.stringify(nb.body.data).slice(0, 120));
}

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
