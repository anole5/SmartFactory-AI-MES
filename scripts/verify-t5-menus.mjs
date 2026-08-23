// T5-4 动态路由后端验证：GET /auth/menus 角色菜单树差异
// 覆盖：未登录 401 → admin 全量树（目录 1-5 + tv-demo 根级 C + 系统集成两页）→
// 按钮级 F 不进树 → planning 有系统集成 → operator/qa 无集成菜单但有 tv-demo
// （动态菜单角色差异演示素材）→ 树结构（父子关系/排序/M 无 path/C 有 path）
// 前置条件：后端已加载第 5 周 T4 代码；sys_menu/sys_role_menu 已按 10-seed-week5.sql 灌入
// 运行：node scripts/verify-t5-menus.mjs

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

const menus = async (token) => {
  const res = await fetch(BASE + '/auth/menus', {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const json = await res.json();
  return { status: res.status, json };
};

const flatten = (tree, out = []) => {
  for (const node of tree ?? []) {
    out.push(node);
    flatten(node.children, out);
  }
  return out;
};

const admin = await login('admin', 'admin123');
const planning = await login('planning', 'planning123');
const operator = await login('operator', 'operator123');
const qa = await login('qa', 'qa123');
ok('四角色登录成功', [admin, planning, operator, qa].every(r => !!r.data?.token));

// 1. 未登录 → 401（鉴权拦截器兜底，白名单仅 /auth/login）
const noToken = await menus(null);
ok('未登录取菜单 401', noToken.status === 401, `status=${noToken.status}`);

// 2. admin：全量树
const at = admin.data.token;
const am = await menus(at);
ok('admin 取菜单 200', am.status === 200 && am.json?.code === 0, `status=${am.status}`);
const aTree = am.json?.data ?? [];
const aFlat = flatten(aTree);
const aById = new Map(aFlat.map(n => [String(n.id), n]));

ok('admin 树含 5 个目录 + tv-demo 根级菜单（共 6 个根）', aTree.length === 6
  && aTree.some(n => String(n.id) === '500'),
  `roots=${aTree.map(n => `${n.id}:${n.menuType}`).join(',')}`);
ok('admin 树含系统集成目录(id=5)', aFlat.some(n => String(n.id) === '5' && n.menuType === 'M'),
  '');
const node5 = aById.get('5');
const node501 = aById.get('501');
const node502 = aById.get('502');
ok('ERP 订单(501)挂在系统集成下且 path=/erp-orders', node5 && node501
  && String(node501.parentId) === '5' && node501.path === '/erp-orders'
  && node501.menuType === 'C',
  `parent=${node501?.parentId} path=${node501?.path}`);
ok('WMS 库存(502)挂在系统集成下且 path=/inventory', node5 && node502
  && String(node502.parentId) === '5' && node502.path === '/inventory',
  `parent=${node502?.parentId} path=${node502?.path}`);
ok('tv-demo(500)为根级 C：parent=0、path=/tv-demo', aTree.some(n =>
  String(n.id) === '500' && String(n.parentId) === '0' && n.path === '/tv-demo'),
  '');
ok('按钮级 F（5011/5012/5021/5022）不进菜单树', !aFlat.some(n =>
  ['5011', '5012', '5021', '5022'].includes(String(n.id))), '');
ok('树节点仅 M/C 且 C 有 path、M 无 path', aFlat.every(n =>
  (n.menuType === 'C' && typeof n.path === 'string' && n.path.startsWith('/'))
  || (n.menuType === 'M' && (n.path === null || n.path === undefined))), '');
// 排序：系统集成目录 order_num=50 在 tv-demo(60) 前
const rootIds = aTree.map(n => String(n.id));
ok('根级按 order_num 排序（5 在 500 前）', rootIds.indexOf('5') < rootIds.indexOf('500'),
  rootIds.join(','));

// 3. planning：有系统集成全功能
const pm = await menus(planning.data.token);
const pFlat = flatten(pm.json?.data ?? []);
ok('planning 树含系统集成(5)+ERP 订单(501)+WMS 库存(502)', ['5', '501', '502']
  .every(id => pFlat.some(n => String(n.id) === id)), '');
ok('planning 树含 tv-demo(500)', pFlat.some(n => String(n.id) === '500'), '');

// 4. operator / qa：无集成菜单（第 5 周种子只给 tv-demo）——动态菜单角色差异
const om = await menus(operator.data.token);
const oFlat = flatten(om.json?.data ?? []);
ok('operator 树无系统集成/ERP/WMS 菜单', !oFlat.some(n =>
  ['5', '501', '502'].includes(String(n.id))), '');
ok('operator 树含 tv-demo(500)', oFlat.some(n => String(n.id) === '500'), '');
ok('operator 树非空（保留原有生产菜单）', oFlat.length > 0, `nodes=${oFlat.length}`);

const qm = await menus(qa.data.token);
const qFlat = flatten(qm.json?.data ?? []);
ok('qa 树无系统集成/ERP/WMS 菜单', !qFlat.some(n =>
  ['5', '501', '502'].includes(String(n.id))), '');
ok('qa 树含 tv-demo(500)', qFlat.some(n => String(n.id) === '500'), '');

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
