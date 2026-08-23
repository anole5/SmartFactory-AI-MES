// T5-2 ERP 后端验证（可重复运行）
// 覆盖：planning 模拟下单 PENDING → admin 转工单 SYNCED+workOrderId 回填
// → 工单侧 externalOrderNo 透传 → 重复转单 409 → operator 查询/下单 403
// → 分页关键词/状态过滤 → 参数 400 → 产品不存在/优先级非法 409
// 运行：node scripts/verify-t5-erp.mjs（后端须已重启加载第 5 周代码）

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

// 1. planning 模拟下单 → 200 + id
const c1 = await api(pt, 'POST', '/integration/erp/orders', {
  productId: 1, planQty: 5, priority: 'HIGH',
  planStartTime: '2026-08-24', planEndTime: '2026-08-28', remark: 'T5-2 验证订单',
});
ok('planning 模拟下单 200', c1.status === 200 && Number(c1.json.data) > 0, `id=${c1.json.data}`);
const orderId = c1.json.data;

// 2. 详情：PENDING + ERP 单号 + 产品快照 + 优先级透传
const c2 = await api(pt, 'GET', `/integration/erp/orders/${orderId}`);
ok('详情 PENDING + ERP 单号前缀', c2.status === 200 && c2.json.data?.status === 'PENDING'
  && /^ERP\d+$/.test(c2.json.data?.externalOrderNo ?? ''),
  `status=${c2.json.data?.status} no=${c2.json.data?.externalOrderNo}`);
ok('产品快照回填 + 数量/优先级透传', c2.status === 200
  && !!c2.json.data?.productCodeSnapshot && !!c2.json.data?.productNameSnapshot
  && c2.json.data?.planQty === 5 && c2.json.data?.priority === 'HIGH',
  `snap=${c2.json.data?.productCodeSnapshot}/${c2.json.data?.productNameSnapshot}`);
const erpNo = c2.json.data?.externalOrderNo;

// 3. admin 一键转工单 → 200（无返回体）
const c3 = await api(at, 'PUT', `/integration/erp/orders/${orderId}/to-work-order`);
ok('admin 转工单 200', c3.status === 200, `status=${c3.status}`);

// 4. 回查：SYNCED + workOrderId 回填
const c4 = await api(pt, 'GET', `/integration/erp/orders/${orderId}`);
ok('转工单后 SYNCED + workOrderId 回填', c4.status === 200 && c4.json.data?.status === 'SYNCED'
  && Number(c4.json.data?.workOrderId) > 0, `status=${c4.json.data?.status} wo=${c4.json.data?.workOrderId}`);
const workOrderId = c4.json.data?.workOrderId;

// 5. 工单侧透传 externalOrderNo
const c5 = await api(at, 'GET', `/production/work-orders/${workOrderId}`);
ok('工单侧 externalOrderNo 透传', c5.status === 200 && c5.json.data?.externalOrderNo === erpNo,
  `wo.externalOrderNo=${c5.json.data?.externalOrderNo}`);

// 6. 重复转单 → 409
const c6 = await api(at, 'PUT', `/integration/erp/orders/${orderId}/to-work-order`);
ok('重复转单 409', c6.status === 409, `status=${c6.status}`);

// 7. operator 无权限：查询 403 + 下单 403
const c7 = await api(ot, 'GET', `/integration/erp/orders/page?pageNum=1&pageSize=10`);
ok('operator 查询外部订单 403', c7.status === 403, `status=${c7.status}`);
const c8 = await api(ot, 'POST', '/integration/erp/orders', { productId: 1, planQty: 1 });
ok('operator 模拟下单 403', c8.status === 403, `status=${c8.status}`);

// 8. planning 分页 + 状态过滤（本单已 SYNCED，PENDING 过滤应不含它）
const c9 = await api(pt, 'GET', '/integration/erp/orders/page?pageNum=1&pageSize=10');
ok('planning 分页 200 且含本单', c9.status === 200 && (c9.json.data?.records ?? [])
  .some(r => r.id === orderId), `total=${c9.json.data?.total}`);
const c10 = await api(pt, 'GET', `/integration/erp/orders/page?pageNum=1&pageSize=10&status=SYNCED`);
ok('状态过滤 SYNCED 命中', c10.status === 200 && (c10.json.data?.records ?? [])
  .some(r => r.id === orderId && r.status === 'SYNCED'), `total=${c10.json.data?.total}`);
const c11 = await api(pt, 'GET', `/integration/erp/orders/page?pageNum=1&pageSize=10&status=PENDING`);
ok('状态过滤 PENDING 不含已转单', c11.status === 200 && (c11.json.data?.records ?? [])
  .every(r => r.status === 'PENDING'), `total=${c11.json.data?.total}`);
const c12 = await api(pt, 'GET', `/integration/erp/orders/page?pageNum=1&pageSize=10&keyword=${erpNo}`);
ok('关键词过滤单号命中', c12.status === 200 && (c12.json.data?.records ?? [])
  .some(r => r.externalOrderNo === erpNo), `total=${c12.json.data?.total}`);

// 9. 参数校验 400：缺 productId / planQty=0
const c13 = await api(pt, 'POST', '/integration/erp/orders', { planQty: 5 });
ok('缺 productId 400', c13.status === 400, `status=${c13.status}`);
const c14 = await api(pt, 'POST', '/integration/erp/orders', { productId: 1, planQty: 0 });
ok('planQty=0 400', c14.status === 400, `status=${c14.status}`);

// 10. 业务校验 409：产品不存在 / 优先级非法
const c15 = await api(pt, 'POST', '/integration/erp/orders', { productId: 99999, planQty: 5 });
ok('产品不存在 409', c15.status === 409, `status=${c15.status}`);
const c16 = await api(pt, 'POST', '/integration/erp/orders', { productId: 1, planQty: 5, priority: 'URGENT' });
ok('优先级非法 409', c16.status === 409, `status=${c16.status}`);

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
