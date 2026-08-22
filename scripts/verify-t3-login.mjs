// T3 登录链路验证：真实登录（JWT）+ 拦截器 401 + 用户列表 + 审计回填
// 运行：node scripts/verify-t3-login.mjs（后端须已启动）

const BASE = 'http://localhost:8080/api';
let pass = 0, fail = 0;
const ok = (name, cond, extra = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'} ${name} ${extra}`);
  cond ? pass++ : fail++;
};

const post = async (path, body, token) => {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: JSON.stringify(body),
  });
  return { status: res.status, body: await res.json() };
};
const get = async (path, token) => {
  const res = await fetch(BASE + path, token ? { headers: { Authorization: 'Bearer ' + token } } : {});
  return { status: res.status, body: await res.json() };
};

// 1. admin 登录成功，返回完整用户信息
const admin = await post('/auth/login', { username: 'admin', password: 'admin123' });
ok('admin 登录 code=0', admin.body.code === 0, JSON.stringify(admin.body).slice(0, 80));
ok('admin 登录 token 非空', typeof admin.body.data?.token === 'string' && admin.body.data.token.length > 50);
ok('admin userId=1(String)', String(admin.body.data?.userId) === '1');
ok('admin realName 系统管理员', admin.body.data?.realName === '系统管理员');
ok('admin permissions 为空数组(T4 填充)', Array.isArray(admin.body.data?.permissions) && admin.body.data.permissions.length === 0);
const adminToken = admin.body.data?.token;

// 2. 错误密码 → 401（且提示不区分账号是否存在，防枚举）
const badPwd = await post('/auth/login', { username: 'admin', password: 'wrong' });
ok('错误密码 HTTP 401', badPwd.status === 401, JSON.stringify(badPwd.body).slice(0, 100));
const noUser = await post('/auth/login', { username: 'nobody', password: 'whatever' });
ok('不存在用户同样 401 提示', noUser.status === 401 && noUser.body.message === '用户名或密码错误');

// 3. 参数校验：空用户名 → 400
const emptyUser = await post('/auth/login', { username: '', password: 'x' });
ok('空用户名 400', emptyUser.status === 400);

// 4. 无 token 访问受保护接口 → 401
const noToken = await get('/master/products/page');
ok('无 token 访问接口 HTTP 401', noToken.status === 401, JSON.stringify(noToken.body).slice(0, 90));
ok('401 响应体结构含 code/message/requestId', noToken.body.code === 401 && !!noToken.body.requestId);

// 5. 伪造 token → 401
const forged = await get('/master/products/page', 'eyJhbGciOiJIUzI1NiJ9.forged.payload');
ok('伪造 token HTTP 401', forged.status === 401);

// 6. 正确 token 访问 → 200（旧接口不因改登录而破坏）
const products = await get('/master/products/page?pageNum=1&pageSize=3', adminToken);
ok('带 token 访问产品分页 code=0', products.body.code === 0, `records=${products.body.data?.records?.length}`);

// 7. operator 登录成功（真实用户表多账号）
const operator = await post('/auth/login', { username: 'operator', password: 'operator123' });
ok('operator 登录 code=0', operator.body.code === 0, `realName=${operator.body.data?.realName}`);
const operatorToken = operator.body.data?.token;

// 8. /auth/users 用户下拉列表（密码不序列化）
const users = await get('/auth/users', adminToken);
ok('用户列表 3 人', users.body.code === 0 && users.body.data?.length === 3);
ok('用户列表不含 password 字段', users.body.data?.every(u => u.password === undefined));

// 9. 审计回填：admin 建产品（created_by 落库值用 SQL 验证，ProductVO 不返回审计字段）
const created = await post('/master/products', {
  productCode: `T3-AUDIT-${Date.now() % 100000}`, productName: '审计验证产品', productType: '测试', unit: '台', status: 'ENABLED',
}, adminToken);
ok('admin 建产品 code=0', created.body.code === 0, `id=${created.body.data}`);
console.log('    -> 数据库验证 created_by：docker exec mysql mysql -uroot -pAtguigu.123 smartfactory_mes -e "SELECT created_by FROM mes_product WHERE id=' + created.body.data + '"');

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
