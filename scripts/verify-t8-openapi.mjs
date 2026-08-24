// 第 8 周 T2 验证：OpenAPI(springdoc) + Actuator 健康检查
// 断言：health UP / info / api-docs openapi 3.x / swagger UI 可达 /
//       白名单没有放多（非白名单接口匿名仍 401）/ 鉴权链未坏
// 运行：node scripts/verify-t8-openapi.mjs（需后端运行中；SMOKE_BASE 可覆盖基址）

const BASE = process.env.SMOKE_BASE || 'http://localhost:8080/api';

let pass = 0, fail = 0;
const problems = [];

function check(name, ok, extra = '') {
  if (ok) { pass++; console.log(`  ✓ ${name}`); }
  else { fail++; problems.push(name + (extra ? ' — ' + extra : '')); console.log(`  ✗ ${name}${extra ? ' — ' + extra : ''}`); }
}

const j = async (res) => { try { return await res.json(); } catch { return null; } };

(async () => {
  // 1. actuator health 200 + UP
  const health = await fetch(BASE + '/actuator/health');
  const healthBody = await j(health);
  check('GET /actuator/health 200 + UP', health.status === 200 && healthBody && healthBody.status === 'UP',
      `status=${health.status}`);

  // 2. actuator info 200
  const info = await fetch(BASE + '/actuator/info');
  check('GET /actuator/info 200', info.status === 200, `status=${info.status}`);

  // 3. api-docs openapi 3.x 且含 /auth/login 路径
  const docs = await fetch(BASE + '/v3/api-docs');
  const docsBody = await j(docs);
  const openapi3 = docsBody && /^3\./.test(String(docsBody.openapi));
  const hasLogin = docsBody && docsBody.paths && docsBody.paths['/auth/login'];
  check('GET /v3/api-docs 200 + openapi 3.x + 含 /auth/login', docs.status === 200 && openapi3 && hasLogin,
      `openapi=${docsBody && docsBody.openapi}`);

  // 4. swagger-config（UI 初始化配置源；单分组 springdoc 用 url 单数字段，多分组才有 urls 数组）
  const swaggerConfig = await fetch(BASE + '/v3/api-docs/swagger-config');
  const cfgBody = await j(swaggerConfig);
  const cfgOk = cfgBody
      && ((Array.isArray(cfgBody.urls) && cfgBody.urls.length > 0)
          || (typeof cfgBody.url === 'string' && cfgBody.url.includes('api-docs')));
  check('GET /v3/api-docs/swagger-config 200 含 api-docs 地址', swaggerConfig.status === 200 && !!cfgOk,
      `status=${swaggerConfig.status}`);

  // 5. swagger-ui.html 302（springdoc 默认跳 swagger-ui/index.html）
  const redirect = await fetch(BASE + '/swagger-ui.html', { redirect: 'manual' });
  const location = redirect.headers.get('location') || '';
  check('GET /swagger-ui.html 302 带 location', redirect.status === 302 && !!location,
      `status=${redirect.status} location=${location}`);

  // 6. 跟随到 swagger-ui/index.html 200
  const ui = await fetch(BASE + '/swagger-ui/index.html');
  const uiText = await ui.text();
  check('GET /swagger-ui/index.html 200 含 swagger-ui', ui.status === 200 && uiText.includes('swagger-ui'),
      `status=${ui.status}`);

  // 7. swagger 静态资源 css 200
  const css = await fetch(BASE + '/swagger-ui/swagger-ui.css');
  check('GET /swagger-ui/swagger-ui.css 200', css.status === 200, `status=${css.status}`);

  // 8. 非白名单接口匿名仍 401（白名单没有放多）
  const anon = await fetch(BASE + '/auth/menus');
  check('匿名 GET /auth/menus 仍 401', anon.status === 401, `status=${anon.status}`);

  // 9. 登录后 200（鉴权链未坏）
  const login = await fetch(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  });
  const loginBody = await j(login);
  const token = loginBody && loginBody.data && loginBody.data.token;
  const menus = token
      ? await fetch(BASE + '/auth/menus', { headers: { Authorization: 'Bearer ' + token } })
      : { status: 0 };
  check('登录后 GET /auth/menus 200（鉴权链未坏）', menus.status === 200, `status=${menus.status}`);

  console.log(`\nverify-t8-openapi: ${pass} 过 / ${fail} 败`);
  if (fail > 0) { console.log('失败项：\n' + problems.map(p => '  - ' + p).join('\n')); }
  // 不用 process.exit：undici keep-alive 未关会触发 libuv 断言崩溃（本机坑）
  process.exitCode = fail > 0 ? 1 : 0;
})().catch(e => { console.error('脚本异常:', e); process.exitCode = 1; });
