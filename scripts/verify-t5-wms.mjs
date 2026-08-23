// T5-3 WMS 后端 + 生产钩子验证（自包含：脚本内自行下单/转工单，与 verify-t5-erp.mjs 相互独立）
// 覆盖：ERP 工单未领料开工 409 → 领料后放行 → 重复领料 409 → 库存不足 409 整单回滚
// → 采购入库累加 + 流水 → 权限 403 → 完工钩子（外部订单 DONE + 成品入库 + 追溯 2 条）
// → 手建工单（手填外部单号）开工直接放行（老冒烟回归点）→ 取消工单
// 前置条件：种子库存状态（先执行 clean-smoke.sql 重放）；后端已加载第 5 周 T3 代码
// 运行：node scripts/verify-t5-wms.mjs

const BASE = 'http://localhost:8080/api';
let pass = 0, fail = 0;
const ok = (name, cond, extra = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'} ${name} ${extra}`);
  cond ? pass++ : fail++;
};

const login = async (username, password) => {
  const res = await fetch(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  return res.json();
};

const api = async (token, method, path, body) => {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json();
  return { status: res.status, json };
};

const admin = await login('admin', 'admin123');
const planning = await login('planning', 'planning123');
const operator = await login('operator', 'operator123');
ok('admin/planning/operator 登录成功', !!admin.data?.token && !!planning.data?.token && !!operator.data?.token);
const at = admin.data.token, pt = planning.data.token, ot = operator.data.token;

const SEED_QTY = { 1: 100, 2: 100, 3: 100, 4: 100, 5: 100, 20: 500 };
const inventoryQty = async (token, itemRefId, itemType = 'MATERIAL') => {
  const r = await api(token, 'GET', `/integration/wms/inventory/page?pageNum=1&pageSize=100&itemType=${itemType}`);
  const row = (r.json?.data?.records ?? []).find(v => String(v.itemRefId) === String(itemRefId));
  return row ? row.qty : null;
};

// 0. 自建链路：planning 下单 → admin 转工单（planQty=5，产品 1）
const e0 = await api(pt, 'POST', '/integration/erp/orders', {
  productId: 1, planQty: 5, priority: 'HIGH', remark: 'T5-3 WMS 验证订单',
});
ok('planning 下单 200', e0.status === 200 && Number(e0.json.data) > 0, `id=${e0.json.data}`);
const ERP_ORDER_ID = e0.json.data;
const e1 = await api(at, 'PUT', `/integration/erp/orders/${ERP_ORDER_ID}/to-work-order`);
ok('admin 转工单 200', e1.status === 200, `status=${e1.status}`);
const eo0 = (await api(pt, 'GET', `/integration/erp/orders/${ERP_ORDER_ID}`)).json?.data;
const WO_ID = Number(eo0?.workOrderId);
ok('转工单 SYNCED + workOrderId 回填', eo0?.status === 'SYNCED' && WO_ID > 0, `wo=${WO_ID}`);

// 1. 下发工单（admin）→ 13 个任务
const r1 = await api(at, 'POST', `/production/work-orders/${WO_ID}/release`, {});
ok('工单下发成功', r1.status === 200 && r1.json?.code === 0, `status=${r1.status}`);
const woDetail = (await api(at, 'GET', `/production/work-orders/${WO_ID}`)).json?.data;
const taskIds = (woDetail?.tasks ?? []).map(t => t.id);
ok('下发生成 13 个任务', taskIds.length === 13, `got ${taskIds.length}`);

// 2. 未领料开工 → 409（ERP 推单工单）
await api(at, 'PUT', `/production/tasks/${taskIds[0]}/assign`, { operatorId: 2 });
const s1 = await api(at, 'PUT', `/production/tasks/${taskIds[0]}/start`, {});
ok('ERP 工单未领料开工 409', s1.status === 409, `status=${s1.status}`);

// 3. planning 领料 → 200 + 应领 = BOM 用量 × 计划数 5
const p1 = await api(pt, 'POST', '/integration/wms/pick', { workOrderId: WO_ID });
ok('planning 工单领料 200', p1.status === 200 && p1.json?.code === 0, `status=${p1.status}`);
const pickItems = p1.json?.data?.items ?? [];
ok('领料明细只含关键物料（trace_required=1 的 6 种内）', pickItems.length > 0
  && pickItems.every(i => SEED_QTY[i.materialId] !== undefined),
  JSON.stringify(pickItems.map(i => `${i.materialId}:${i.actualPickedQty}/${i.needQty}`)));
const bom = (await api(at, 'GET', `/master/boms/${woDetail?.bomId}`)).json?.data;
const requiredMap = {};
for (const item of bom?.items ?? []) requiredMap[item.materialId] = Number(item.requiredQty);
ok('应领数量 = BOM 用量 × 计划数 5（向上取整）', pickItems.every(i =>
  i.needQty === Math.ceil((requiredMap[i.materialId] ?? 0) * 5)),
  JSON.stringify(pickItems.map(i => `${i.materialCode}: need=${i.needQty} req=${requiredMap[i.materialId]}`)));
ok('领料后库存扣减到 种子数量 - 应领', await (async () => {
  for (const i of pickItems) {
    const q = await inventoryQty(pt, i.materialId);
    if (q !== SEED_QTY[i.materialId] - i.needQty) return false;
  }
  return true;
})(), JSON.stringify(pickItems.map(i => `${i.materialId}: qty=${SEED_QTY[i.materialId] - i.needQty}`)));

// 4. 重复领料 → 409（已足额领用）
const p2 = await api(pt, 'POST', '/integration/wms/pick', { workOrderId: WO_ID });
ok('重复领料 409（已足额领用）', p2.status === 409, `status=${p2.status}`);

// 5. 采购入库：planning 200 + 累加；operator 403；物料不存在 409
const st1 = await api(pt, 'POST', '/integration/wms/stock-in', { materialId: 1, qty: 50, remark: 'T3 验证入库' });
ok('planning 采购入库 200', st1.status === 200 && st1.json?.code === 0, `status=${st1.status}`);
const pickOfM1 = pickItems.find(i => String(i.materialId) === '1');
const expectedAfterStockIn = SEED_QTY[1] - (pickOfM1?.needQty ?? 0) + 50;
const m1AfterStockIn = await inventoryQty(pt, 1);
ok('入库后物料 1 库存累加 +50', m1AfterStockIn === expectedAfterStockIn,
  `qty=${m1AfterStockIn} expected=${expectedAfterStockIn}`);
const st2 = await api(ot, 'POST', '/integration/wms/stock-in', { materialId: 1, qty: 10 });
ok('operator 采购入库 403', st2.status === 403, `status=${st2.status}`);
const st3 = await api(pt, 'POST', '/integration/wms/stock-in', { materialId: 99999, qty: 10 });
ok('物料不存在入库 409', st3.status === 409, `status=${st3.status}`);

// 6. 库存不足：新外单 planQty 999999 → 转工单 → 领料 409 且库存零扣减（整单回滚）
const b1 = await api(pt, 'POST', '/integration/erp/orders', { productId: 1, planQty: 999999, priority: 'NORMAL' });
await api(at, 'PUT', `/integration/erp/orders/${b1.json.data}/to-work-order`);
const b2 = (await api(pt, 'GET', `/integration/erp/orders/${b1.json.data}`)).json?.data;
const p3 = await api(pt, 'POST', '/integration/wms/pick', { workOrderId: Number(b2?.workOrderId) });
ok('库存不足领料 409', p3.status === 409, `status=${p3.status}`);
const m1AfterFailedPick = await inventoryQty(pt, 1);
ok('失败领料整单回滚（物料 1 库存不变）', m1AfterFailedPick === expectedAfterStockIn,
  `qty=${m1AfterFailedPick} expected=${expectedAfterStockIn}`);

// 7. 领料后开工放行 → IN_PROGRESS
const s2 = await api(at, 'PUT', `/production/tasks/${taskIds[0]}/start`, {});
ok('领料后开工 200', s2.status === 200 && s2.json?.code === 0, `status=${s2.status}`);
const woAfterStart = (await api(at, 'GET', `/production/work-orders/${WO_ID}`)).json?.data;
ok('工单级联 IN_PROGRESS', woAfterStart?.status === 'IN_PROGRESS', `status=${woAfterStart?.status}`);

// 8. 剩余 12 个任务派工 + 开工（每个开工都过钩子：已领料 → 放行）
let chainOk = true;
for (let i = 1; i < 13; i++) {
  const a = await api(at, 'PUT', `/production/tasks/${taskIds[i]}/assign`, { operatorId: 2 });
  const s = await api(at, 'PUT', `/production/tasks/${taskIds[i]}/start`, {});
  if (a.json?.code !== 0 || s.json?.code !== 0) { chainOk = false; break; }
}
ok('t2..t13 派工+开工全部成功（钩子放行）', chainOk, 'chain failed');

// 9. 报工 13 道（operator）→ 完工钩子：外部订单 DONE + 成品入库 + 追溯
let reportOk = true;
for (let i = 0; i < 13; i++) {
  const r = await api(ot, 'POST', '/production/reports',
    { taskId: taskIds[i], reportQty: 5, goodQty: 5, defectQty: 0, productBatchNo: 'T3-WMS-BATCH' });
  if (r.json?.code !== 0) { reportOk = false; break; }
}
ok('13 道报工全部成功（工单 COMPLETED）', reportOk, 'report loop failed');

const woDone = (await api(at, 'GET', `/production/work-orders/${WO_ID}`)).json?.data;
ok('工单自动 COMPLETED 合格 5 台', woDone?.status === 'COMPLETED' && woDone?.goodQty === 5,
  `status=${woDone?.status} good=${woDone?.goodQty}`);

const eo = (await api(pt, 'GET', `/integration/erp/orders/${ERP_ORDER_ID}`)).json?.data;
ok('完工钩子：外部订单 DONE', eo?.status === 'DONE', `status=${eo?.status}`);
const finQty = await inventoryQty(pt, 1, 'FINISHED');
ok('完工钩子：成品库存 +5（FINISHED 产品 1）', finQty === 5, `qty=${finQty}`);
const txDone = await api(pt, 'GET', `/integration/wms/transactions/page?pageNum=1&pageSize=50&workOrderId=${WO_ID}&bizType=FINISHED_IN`);
ok('完工钩子：FINISHED_IN 流水 1 条', (txDone.json?.data?.records ?? []).length === 1,
  `got ${(txDone.json?.data?.records ?? []).length}`);
const txPick = await api(pt, 'GET', `/integration/wms/transactions/page?pageNum=1&pageSize=50&workOrderId=${WO_ID}&bizType=PICK_OUT`);
ok('领料流水 PICK_OUT 条数 = 领料明细数', (txPick.json?.data?.records ?? []).length === pickItems.length,
  `got ${(txPick.json?.data?.records ?? []).length}`);

const traces = (await api(at, 'GET', `/production/traces?workOrderId=${WO_ID}`)).json?.data ?? [];
const countBy = {};
for (const t of traces) countBy[t.actionType] = (countBy[t.actionType] ?? 0) + 1;
ok('完工钩子：追溯 ERP_DONE=1 / WMS_FINISHED_IN=1', countBy.ERP_DONE === 1 && countBy.WMS_FINISHED_IN === 1,
  JSON.stringify(countBy));

// 10. 权限边界：operator 查库存/流水/领料全 403
const q1 = await api(ot, 'GET', '/integration/wms/inventory/page?pageNum=1&pageSize=5');
ok('operator 查库存 403', q1.status === 403, `status=${q1.status}`);
const q2 = await api(ot, 'POST', '/integration/wms/pick', { workOrderId: WO_ID });
ok('operator 领料 403', q2.status === 403, `status=${q2.status}`);

// 11. 手建工单（手填外部单号）开工直接放行 —— 老冒烟 139 回归点
const m1c = await api(at, 'POST', '/production/work-orders', {
  productId: 1, planQty: 1, externalOrderNo: 'SMOKE-T3-MANUAL', priority: 'NORMAL',
});
const manualWoId = m1c.json?.data;
await api(at, 'POST', `/production/work-orders/${manualWoId}/release`, {});
const manualDetail = (await api(at, 'GET', `/production/work-orders/${manualWoId}`)).json?.data;
const manualT1 = manualDetail?.tasks?.[0]?.id;
await api(at, 'PUT', `/production/tasks/${manualT1}/assign`, { operatorId: 2 });
const ms = await api(at, 'PUT', `/production/tasks/${manualT1}/start`, {});
ok('手建工单（手填外部单号）开工放行 200', ms.status === 200 && ms.json?.code === 0, `status=${ms.status}`);

// 12. 领料不存在的工单 → 409（项目惯例：mustExist 一律 409）
const p4 = await api(pt, 'POST', '/integration/wms/pick', { workOrderId: 999999 });
ok('领料不存在的工单 409', p4.status === 409, `status=${p4.status}`);

// 13. 清理：取消手工工单（IN_PROGRESS → CANCELLED，支持取消中状态）
const c1 = await api(at, 'PUT', `/production/work-orders/${manualWoId}/cancel`, {});
ok('手工工单取消成功', c1.status === 200 && c1.json?.code === 0, `status=${c1.status}`);

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
