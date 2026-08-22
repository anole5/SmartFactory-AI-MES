// T4 RBAC 权限校验验证：三角色登录权限集合 + @RequirePermission 403 拦截 + 读接口放行
// 运行：node scripts/verify-t4-rbac.mjs（后端须已启动）
// 种子：admin=SUPER_ADMIN(44 权限) operator=OPERATOR(8，任务/报工操作) planning=PLANNING(13，基础资料只读+工单全操作)

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
const del = (p, t) => call('DELETE', p, undefined, t);
const get = (p, t) => call('GET', p, undefined, t);

// 1. 登录返回权限集合
const admin = await post('/auth/login', { username: 'admin', password: 'admin123' });
ok('admin 登录 code=0', admin.body.code === 0);
ok('admin roles=[SUPER_ADMIN]', admin.body.data?.roles?.includes('SUPER_ADMIN'));
// 44 个菜单中 2 个目录无 perm 标识 → 权限集合 42 项
ok('admin permissions=42 项', admin.body.data?.permissions?.length === 42, `实际=${admin.body.data?.permissions?.length}`);
ok('admin 含 master:product:create', admin.body.data?.permissions?.includes('master:product:create'));
ok('admin 含 production:work-order:release', admin.body.data?.permissions?.includes('production:work-order:release'));
const adminToken = admin.body.data?.token;

const operator = await post('/auth/login', { username: 'operator', password: 'operator123' });
ok('operator 登录 code=0', operator.body.code === 0);
ok('operator roles=[OPERATOR]', operator.body.data?.roles?.includes('OPERATOR'));
ok('operator permissions=8 项', operator.body.data?.permissions?.length === 8, `实际=${operator.body.data?.permissions?.length}`);
ok('operator 含 production:task:assign', operator.body.data?.permissions?.includes('production:task:assign'));
ok('operator 无 master:product:create', !operator.body.data?.permissions?.includes('master:product:create'));
const operatorToken = operator.body.data?.token;

const planning = await post('/auth/login', { username: 'planning', password: 'planning123' });
ok('planning 登录 code=0', planning.body.code === 0);
ok('planning permissions=13 项', planning.body.data?.permissions?.length === 13, `实际=${planning.body.data?.permissions?.length}`);
ok('planning 含 master:product:list（只读）', planning.body.data?.permissions?.includes('master:product:list'));
ok('planning 含 production:work-order:release', planning.body.data?.permissions?.includes('production:work-order:release'));
ok('planning 无 master:product:create', !planning.body.data?.permissions?.includes('master:product:create'));
ok('planning 无 production:task:assign', !planning.body.data?.permissions?.includes('production:task:assign'));
const planningToken = planning.body.data?.token;

// 2. admin 建产品（后续权限用例的靶子）
const payload = {
  productCode: `T4-RBAC-${Date.now() % 100000}`, productName: '权限验证产品', productType: '测试', unit: '台', status: 'ENABLED',
};
const created = await post('/master/products', payload, adminToken);
ok('admin 建产品 code=0', created.body.code === 0, `id=${created.body.data}`);
const pid = created.body.data;

// 3. operator 调 master 写接口 → 403（4 个写端点全覆盖）
const opCreate = await post('/master/products', payload, operatorToken);
ok('operator 建产品 HTTP 403', opCreate.status === 403, `code=${opCreate.body.code}`);
ok('operator 403 响应体 message 含无权限', opCreate.body.code === 403 && String(opCreate.body.message).includes('无权限访问'), JSON.stringify(opCreate.body).slice(0, 110));
const opUpdate = await put(`/master/products/${pid}`, payload, operatorToken);
ok('operator 改产品 HTTP 403', opUpdate.status === 403);
const opStatus = await put(`/master/products/${pid}/status`, { status: 'DISABLED' }, operatorToken);
ok('operator 启停用 HTTP 403', opStatus.status === 403);
const opDelete = await del(`/master/products/${pid}`, operatorToken);
ok('operator 删产品 HTTP 403', opDelete.status === 403);

// 4. operator 读接口放行（page 未加注解 → 任意登录用户可读）
const opRead = await get('/master/products/page?pageNum=1&pageSize=5', operatorToken);
ok('operator 读产品分页 code=0', opRead.status === 200 && opRead.body.code === 0, `records=${opRead.body.data?.records?.length}`);

// 5. planning：master 只读可查、写操作 403
const plRead = await get('/master/products/page?pageNum=1&pageSize=5', planningToken);
ok('planning 读产品分页 code=0', plRead.status === 200 && plRead.body.code === 0);
const plCreate = await post('/master/products', payload, planningToken);
ok('planning 建产品 HTTP 403（有 list 无 create）', plCreate.status === 403);
const plStatus = await put(`/master/products/${pid}/status`, { status: 'DISABLED' }, planningToken);
ok('planning 启停用 HTTP 403', plStatus.status === 403);

// 6. 其他 master 模块抽查：operator 对工序创建 403（跨模块一致性）
const opProcess = await post('/master/processes', { processCode: 'T4-X', processName: 'X', workCenter: 'X', standardMinutes: 1 }, operatorToken);
ok('operator 建工序 HTTP 403（跨模块一致）', opProcess.status === 403);

// 7. admin 全量放行：改 + 删（清场）
const admUpdate = await put(`/master/products/${pid}`, { ...payload, productName: '权限验证产品-改' }, adminToken);
ok('admin 改产品 code=0', admUpdate.body.code === 0);
const admDelete = await del(`/master/products/${pid}`, adminToken);
ok('admin 删产品 code=0（清场）', admDelete.body.code === 0);

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
