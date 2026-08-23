// T7 设备管理 + 状态漂移模拟验证：分页/CRUD/编码唯一/状态切换/权限边界
// 状态断言只断"四值集合成员"，不断具体值（漂移模拟器每 15s 随机改状态）
// 漂移定时器本身由后端日志验证（grep 后端输出 "设备状态漂移"）
// 运行：node scripts/verify-t7-equipment.mjs（后端须已启动）

const BASE = 'http://localhost:8080/api';
const FOUR = ['RUNNING', 'IDLE', 'STOPPED', 'MAINTENANCE'];
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

// 1. 种子设备分页：10 行 + 状态合法 + 工位名称回填
{
  const page = await get('/master/equipment/page?pageSize=20', adminToken);
  const records = page.body.data?.records ?? [];
  ok('设备分页 ≥10 行（种子 10 + 可能的验证残留）', Number(page.body.data?.total) >= 10 && records.length >= 10,
    `total=${page.body.data?.total} got=${records.length}`);
  ok('种子设备状态 ∈ 四值集合', records.every(r => FOUR.includes(r.status)),
    JSON.stringify(records.map(r => r.status)));
  ok('工位名称回填（种子全挂工位）', records.every(r => !!r.workstationName),
    JSON.stringify(records[0] ?? {}));
}

// 2. CRUD：复用或创建 EQ-T7-TEST（可重跑）→ 重复编码 409 → 详情 → 更新
let eqId = null;
{
  const page = await get('/master/equipment/page?keyword=EQ-T7-TEST&pageSize=5', adminToken);
  const existing = page.body.data?.records?.find(e => e.equipmentCode === 'EQ-T7-TEST');
  if (existing) {
    eqId = existing.id;
    ok('复用已有 EQ-T7-TEST 设备', true, `id=${eqId}`);
  } else {
    const r1 = await post('/master/equipment', {
      equipmentCode: 'EQ-T7-TEST', equipmentName: 'T7 测试设备', model: 'M-T7', workstationId: 1,
    }, adminToken);
    ok('创建设备 code=0', r1.body.code === 0, JSON.stringify(r1.body).slice(0, 120));
    eqId = r1.body.data;
  }
  const r2 = await post('/master/equipment', {
    equipmentCode: 'EQ-T7-TEST', equipmentName: '重复编码设备', model: 'X',
  }, adminToken);
  ok('重复设备编码 409', r2.status === 409 && r2.body.code === 409, `status=${r2.status}`);
  const det = await get(`/master/equipment/${eqId}`, adminToken);
  ok('详情匹配 + 状态 ∈ 四值集合（漂移影响）',
    det.body.data?.equipmentCode === 'EQ-T7-TEST' && FOUR.includes(det.body.data?.status),
    JSON.stringify(det.body.data));
  const r3 = await put(`/master/equipment/${eqId}`, {
    equipmentCode: 'EQ-T7-TEST', equipmentName: 'T7 测试设备-改', model: 'M-T7-V2', workstationId: 2,
  }, adminToken);
  ok('更新设备 code=0', r3.body.code === 0, JSON.stringify(r3.body).slice(0, 120));
  const det2 = await get(`/master/equipment/${eqId}`, adminToken);
  ok('更新生效（名称/型号/工位）',
    det2.body.data?.equipmentName === 'T7 测试设备-改' && det2.body.data?.model === 'M-T7-V2'
    && String(det2.body.data?.workstationId) === '2',
    JSON.stringify(det2.body.data));
}

// 3. 状态切换：MAINTENANCE 后立即 GET ∈ 四值集合（漂移影响，不断具体值）；非法状态 409
{
  const r1 = await put(`/master/equipment/${eqId}/status`, { status: 'MAINTENANCE' }, adminToken);
  ok('切 MAINTENANCE code=0', r1.body.code === 0, JSON.stringify(r1.body).slice(0, 120));
  const det = await get(`/master/equipment/${eqId}`, adminToken);
  ok('切后状态 ∈ 四值集合', FOUR.includes(det.body.data?.status), `status=${det.body.data?.status}`);
  const r2 = await put(`/master/equipment/${eqId}/status`, { status: 'FLYING' }, adminToken);
  ok('非法状态 400（EnumUtils 参数错误约定）', r2.status === 400 && r2.body.code === 400, `status=${r2.status}`);
}

// 4. 权限边界：operator 无设备写权限 403，GET 分页放行（master 风格）
{
  const r1 = await post('/master/equipment', {
    equipmentCode: 'EQ-OP-403', equipmentName: '越权设备',
  }, operatorToken);
  ok('operator 创建设备 403', r1.status === 403, `status=${r1.status}`);
  const r2 = await put(`/master/equipment/${eqId}`, {
    equipmentCode: 'EQ-T7-TEST', equipmentName: '越权改名',
  }, operatorToken);
  ok('operator 更新设备 403', r2.status === 403, `status=${r2.status}`);
  const r3 = await put(`/master/equipment/${eqId}/status`, { status: 'STOPPED' }, operatorToken);
  ok('operator 切设备状态 403', r3.status === 403, `status=${r3.status}`);
  const r4 = await get('/master/equipment/page?pageSize=5', operatorToken);
  ok('operator 查设备列表放行 200', r4.status === 200 && r4.body.code === 0, `status=${r4.status}`);
}

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
console.log('漂移定时器验证：另查后端日志应有"设备状态漂移"行（约每 15s 1-2 条）');
process.exit(fail > 0 ? 1 : 0);
