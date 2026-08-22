// T5 工单 CRUD 验证：建单快照回填/编辑草稿/取消幂等/状态机 409/权限 403/参数校验
// 运行：node scripts/verify-t5-workorder.mjs（后端须已启动）
// 前置：产品 1(TV-AOC-55U4K-001) 有 ACTIVE BOM+路线；产品 2/3 停用（负例）

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

// 1. 建单：快照 + ACTIVE BOM/路线自动解析 + 单号格式
const wo1 = await post('/production/work-orders', { productId: 1, planQty: 100, priority: 'HIGH', externalOrderNo: 'PO-2026-001' }, adminToken);
ok('建单 code=0', wo1.body.code === 0, `id=${wo1.body.data}`);
const id1 = wo1.body.data;

const d1 = await get(`/production/work-orders/${id1}`, adminToken);
const v1 = d1.body.data;
ok('单号格式 WO+12 位数字', /^WO\d{12}$/.test(v1.workOrderNo), v1.workOrderNo);
ok('状态 DRAFT', v1.status === 'DRAFT');
ok('产品快照回填 code', v1.productCodeSnapshot === 'TV-AOC-55U4K-001');
ok('产品快照回填 name 非空', typeof v1.productNameSnapshot === 'string' && v1.productNameSnapshot.length > 0);
ok('BOM 自动解析 id=1', String(v1.bomId) === '1', `bomId=${v1.bomId}`);
ok('路线自动解析 id=1', String(v1.routeId) === '1', `routeId=${v1.routeId}`);
ok('数量回填 completed=0', v1.completedQty === 0 && v1.goodQty === 0 && v1.defectQty === 0);
ok('优先级 HIGH 回读', v1.priority === 'HIGH');
ok('外部订单号回读', v1.externalOrderNo === 'PO-2026-001');

// 2. 编辑草稿
const upd = await put(`/production/work-orders/${id1}`, { productId: 1, planQty: 120, priority: 'LOW', remark: '演示工单' }, adminToken);
ok('编辑草稿 code=0', upd.body.code === 0);
const d1b = await get(`/production/work-orders/${id1}`, adminToken);
ok('编辑后 planQty=120 priority=LOW', d1b.body.data.planQty === 120 && d1b.body.data.priority === 'LOW');
ok('编辑后 remark 回读', d1b.body.data.remark === '演示工单');

// 3. 分页 + 状态过滤（枚举 name 绑定 GET 参数）
const page1 = await get('/production/work-orders/page?status=DRAFT&pageNum=1&pageSize=10', adminToken);
ok('按 DRAFT 过滤分页 code=0', page1.body.code === 0, `records=${page1.body.data?.records?.length}`);
ok('分页记录含刚建工单', page1.body.data?.records?.some(r => String(r.id) === String(id1)));
const pageKw = await get('/production/work-orders/page?keyword=PO-2026-001&pageNum=1&pageSize=10', adminToken);
ok('关键字模糊匹配外部订单号', pageKw.body.data?.records?.some(r => String(r.id) === String(id1)));

// 4. 取消 + 幂等
const cancel1 = await put(`/production/work-orders/${id1}/cancel`, {}, adminToken);
ok('取消工单 code=0', cancel1.body.code === 0);
ok('取消后状态 CANCELLED', (await get(`/production/work-orders/${id1}`, adminToken)).body.data.status === 'CANCELLED');
const cancelAgain = await put(`/production/work-orders/${id1}/cancel`, {}, adminToken);
ok('重复取消幂等 code=0', cancelAgain.body.code === 0);

// 5. 已取消工单不能再编辑
const updCancelled = await put(`/production/work-orders/${id1}`, { productId: 1, planQty: 130 }, adminToken);
ok('已取消工单编辑 HTTP 409', updCancelled.status === 409, JSON.stringify(updCancelled.body).slice(0, 90));

// 6. 负例：停用产品 / 无生效 BOM / 时间倒挂 / 参数校验
const woDisabled = await post('/production/work-orders', { productId: 2, planQty: 10 }, adminToken);
ok('停用产品建单 409', woDisabled.status === 409, JSON.stringify(woDisabled.body).slice(0, 90));
// 新建产品（默认 DISABLED）→ 启用 → 专测「无生效 BOM」分支
const tmpProduct = await post('/master/products', {
  productCode: `T5-NOBOM-${Date.now() % 100000}`, productName: '无BOM产品', productType: '测试', unit: '台',
}, adminToken);
ok('临时产品创建 code=0', tmpProduct.body.code === 0, `id=${tmpProduct.body.data}`);
const enableTmp = await put(`/master/products/${tmpProduct.body.data}/status`, { status: 'ENABLED' }, adminToken);
ok('临时产品启用 code=0', enableTmp.body.code === 0);
const woNoBom = await post('/production/work-orders', { productId: Number(tmpProduct.body.data), planQty: 10 }, adminToken);
ok('启用但无生效 BOM 的产品建单 409', woNoBom.status === 409, JSON.stringify(woNoBom.body).slice(0, 110));
const delTmp = await call('DELETE', `/master/products/${tmpProduct.body.data}`, undefined, adminToken);
ok('临时产品清场 code=0', delTmp.body.code === 0);
// 日期格式对齐 JacksonConfig 全局约定 yyyy-MM-dd HH:mm:ss（空格非 T）
const woTime = await post('/production/work-orders', { productId: 1, planQty: 10, planStartTime: '2026-08-30 00:00:00', planEndTime: '2026-08-20 00:00:00' }, adminToken);
ok('计划结束早于开始 409', woTime.status === 409, JSON.stringify(woTime.body).slice(0, 90));
const woBadDate = await post('/production/work-orders', { productId: 1, planQty: 10, planStartTime: '2026-08-30T00:00:00' }, adminToken);
ok('日期格式错误（带 T）400 而非 500', woBadDate.status === 400, JSON.stringify(woBadDate.body).slice(0, 110));
const woZero = await post('/production/work-orders', { productId: 1, planQty: 0 }, adminToken);
ok('planQty=0 参数校验 400', woZero.status === 400);
const woNoProduct = await post('/production/work-orders', { planQty: 10 }, adminToken);
ok('缺 productId 参数校验 400', woNoProduct.status === 400);

// 7. 权限：operator 无 create/update/cancel 权限
const opCreate = await post('/production/work-orders', { productId: 1, planQty: 10 }, operatorToken);
ok('operator 建单 403', opCreate.status === 403);
const opCancel = await put(`/production/work-orders/${id1}/cancel`, {}, operatorToken);
ok('operator 取消 403', opCancel.status === 403);

// 8. 第二个工单：供 T6 下发使用（保留不取消），并验证计划时间回读
const wo2 = await post('/production/work-orders', { productId: 1, planQty: 50, planStartTime: '2026-08-24 08:00:00', planEndTime: '2026-08-28 18:00:00' }, adminToken);
ok('建单 2（留 T6 下发用）code=0', wo2.body.code === 0, `id=${wo2.body.data}`);
const d2 = await get(`/production/work-orders/${wo2.body.data}`, adminToken);
ok('计划时间回读（空格格式）', d2.body.data.planStartTime === '2026-08-24 08:00:00' && d2.body.data.planEndTime === '2026-08-28 18:00:00');

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
console.log(`\nSQL 复核提示（追溯记录）：
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes -e "SELECT trace_no, action_type, operator_id FROM mes_trace_record WHERE work_order_id IN (${id1}, ${wo2.body.data}) ORDER BY id"`);
process.exit(fail > 0 ? 1 : 0);
