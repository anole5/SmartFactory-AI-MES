// 第 8 周 T3 验证：docker-compose 一键启动后的全栈健康断言
// 断言：8090 首页 200 含 #app / nginx 反代 actuator+api-docs / 8082 直连 health /
//       容器内登录鉴权链 / backend+frontend 两镜像存在 / 三容器 running 且 mysql+backend healthy
// 运行：node scripts/verify-t8-compose.mjs（需 compose 已 up，基址可用 SMOKE_BASE 覆盖）

import { execSync } from 'node:child_process';

const FRONT = process.env.SMOKE_FRONT || 'http://localhost:8090';
const BASE = process.env.SMOKE_BASE || 'http://localhost:8082/api';

let pass = 0, fail = 0;
const problems = [];

function check(name, ok, extra = '') {
  if (ok) { pass++; console.log(`  ✓ ${name}`); }
  else { fail++; problems.push(name + (extra ? ' — ' + extra : '')); console.log(`  ✗ ${name}${extra ? ' — ' + extra : ''}`); }
}

const j = async (res) => { try { return await res.json(); } catch { return null; } };

(async () => {
  // 1. 前端首页（nginx 托管 Vue 产物）
  const home = await fetch(FRONT + '/');
  const homeText = await home.text();
  check('8090 首页 200 含 #app', home.status === 200 && homeText.includes('id="app"'),
      `status=${home.status}`);

  // 2. nginx 反代 actuator（前端容器 → backend 容器内网连通）
  const healthViaNginx = await fetch(FRONT + '/api/actuator/health');
  const hBody = await j(healthViaNginx);
  check('8090/api/actuator/health 反代 200 + UP', healthViaNginx.status === 200 && hBody && hBody.status === 'UP',
      `status=${healthViaNginx.status}`);

  // 3. nginx 反代 api-docs（/api 前缀原样透传不重写）
  const docs = await fetch(FRONT + '/api/v3/api-docs');
  const docsBody = await j(docs);
  check('8090/api/v3/api-docs 反代 200 + openapi 3.x',
      docs.status === 200 && docsBody && /^3\./.test(String(docsBody.openapi)),
      `status=${docs.status}`);

  // 4. backend 宿主直连（8082:8080 端口映射）
  const health = await fetch(BASE + '/actuator/health');
  const healthBody = await j(health);
  check('8082 直连 health 200 + UP', health.status === 200 && healthBody && healthBody.status === 'UP',
      `status=${health.status}`);

  // 5. 容器内登录 + 鉴权链（SMOKE_BASE 语义：登录接口可换基址）
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
  check('8082 登录 + /auth/menus 200（容器内鉴权链可用）', menus.status === 200, `status=${menus.status}`);

  // 6. 两镜像存在（compose 项目名 = 目录名小写 smartfactory-ai-mes）
  let images = '';
  try { images = execSync('docker images --format "{{.Repository}}"').toString(); } catch (e) {
    problems.push('docker images 失败: ' + e.message);
  }
  const hasBackend = images.includes('smartfactory-ai-mes-backend');
  const hasFrontend = images.includes('smartfactory-ai-mes-frontend');
  check('backend/frontend 两镜像存在', hasBackend && hasFrontend,
      `backend=${hasBackend} frontend=${hasFrontend}`);

  // 7/8. 三容器 running + mysql/backend healthy（frontend 未配 healthcheck）
  let states = [], healths = [];
  try {
    states = execSync('docker inspect -f "{{.State.Status}}" mes-mysql mes-backend mes-frontend').toString().trim().split(/\s+/);
    healths = execSync('docker inspect -f "{{.State.Health.Status}}" mes-mysql mes-backend').toString().trim().split(/\s+/);
  } catch (e) {
    problems.push('docker inspect 失败: ' + e.message);
  }
  check('三容器 running', states.length === 3 && states.every(s => s === 'running'), `states=${states.join(',')}`);
  check('mysql/backend healthy', healths.length === 2 && healths.every(h => h === 'healthy'),
      `healths=${healths.join(',')}`);

  console.log(`\nverify-t8-compose: ${pass} 过 / ${fail} 败`);
  if (fail > 0) { console.log('失败项：\n' + problems.map(p => '  - ' + p).join('\n')); }
  // 不用 process.exit：undici keep-alive 未关会触发 libuv 断言崩溃（本机坑）
  process.exitCode = fail > 0 ? 1 : 0;
})().catch(e => { console.error('脚本异常:', e); process.exitCode = 1; });
