// T7 任务流转验证：派工（校验/覆盖工位/重复 409）→ 开工（工单级联 IN_PROGRESS）→ 暂停/继续往返
// + 工单取消级联任务 + 三角色权限边界
// 运行：node scripts/verify-t7-taskflow.mjs（后端须已启动）

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
const planning = await post('/auth/login', { username: 'planning', password: 'planning123' });
const planningToken = planning.body.data?.token;

// 1. 建单 + 下发，取任务
const wo = await post('/production/work-orders', { productId: 1, planQty: 10 }, adminToken);
const woId = wo.body.data;
await post(`/production/work-orders/${woId}/release`, {}, adminToken);
const tasks = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data;
ok('前置：下发生成 13 任务', tasks?.length === 13);
const task1 = tasks[0], task4 = tasks[3], task13 = tasks[12];

// 2. 派工：正常 + 校验 + 覆盖工位 + 重复 409
const assign1 = await put(`/production/tasks/${task1.id}/assign`, { operatorId: 2 }, adminToken);
ok('派工 code=0', assign1.body.code === 0);
let t1 = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data[0];
ok('派工后 ASSIGNED + operatorId=2', t1.status === 'ASSIGNED' && String(t1.operatorId) === '2');
ok('操作员名称回填 张操作', t1.operatorName === '张操作', t1.operatorName);
ok('派工沿用默认工位', String(t1.workstationId) === '1', `ws=${t1.workstationId}`);
const assignAgain = await put(`/production/tasks/${task1.id}/assign`, { operatorId: 2 }, adminToken);
ok('重复派工 409', assignAgain.status === 409, JSON.stringify(assignAgain.body).slice(0, 90));
const assignBadOp = await put(`/production/tasks/${task4.id}/assign`, { operatorId: 99999 }, adminToken);
ok('派给不存在操作员 409', assignBadOp.status === 409);
const assignBadWs = await put(`/production/tasks/${task4.id}/assign`, { operatorId: 2, workstationId: 99999 }, adminToken);
ok('覆盖工位不存在 409', assignBadWs.status === 409);
const assignWs3 = await put(`/production/tasks/${task4.id}/assign`, { operatorId: 3, workstationId: 3 }, adminToken);
ok('覆盖工位 3 code=0', assignWs3.body.code === 0);
let t4 = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data[3];
ok('覆盖后 workstationId=3 + 设备快照刷新', String(t4.workstationId) === '3' && !!t4.equipmentCodeSnapshot, `eq=${t4.equipmentCodeSnapshot}`);
ok('覆盖后操作员=3（李计划）', String(t4.operatorId) === '3');

// 3. 开工：任务 RUNNING + 工单级联 IN_PROGRESS + 实际开工时间回填
const start1 = await put(`/production/tasks/${task1.id}/start`, {}, adminToken);
ok('开工 code=0', start1.body.code === 0);
t1 = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data[0];
ok('开工后任务 RUNNING', t1.status === 'RUNNING');
ok('任务 startTime 回填', !!t1.startTime);
const woDetail = await get(`/production/work-orders/${woId}`, adminToken);
ok('工单级联 IN_PROGRESS', woDetail.body.data.status === 'IN_PROGRESS');
ok('工单 actualStartTime 回填', !!woDetail.body.data.actualStartTime);
const startAgain = await put(`/production/tasks/${task1.id}/start`, {}, adminToken);
ok('重复开工幂等 code=0', startAgain.body.code === 0);

// 4. 暂停/继续往返 + 状态机拦截
const pause1 = await put(`/production/tasks/${task1.id}/pause`, {}, adminToken);
ok('暂停 code=0', pause1.body.code === 0);
t1 = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data[0];
ok('暂停后 PAUSED', t1.status === 'PAUSED');
const pauseAgain = await put(`/production/tasks/${task1.id}/pause`, {}, adminToken);
ok('重复暂停幂等 code=0', pauseAgain.body.code === 0);
const resume1 = await put(`/production/tasks/${task1.id}/resume`, {}, adminToken);
ok('继续 code=0', resume1.body.code === 0);
t1 = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data[0];
ok('继续后 RUNNING', t1.status === 'RUNNING');
const startPending = await put(`/production/tasks/${task13.id}/start`, {}, adminToken);
ok('未派工任务直接开工 409', startPending.status === 409, JSON.stringify(startPending.body).slice(0, 90));

// 5. 权限：planning 无任务操作权限；operator 有（assign/start 放行）
const plAssign = await put(`/production/tasks/${task13.id}/assign`, { operatorId: 2 }, planningToken);
ok('planning 派工 403', plAssign.status === 403);
const opAssign = await put(`/production/tasks/${task13.id}/assign`, { operatorId: 2 }, operatorToken);
ok('operator 派工 code=0', opAssign.body.code === 0);
const opStart = await put(`/production/tasks/${task13.id}/start`, {}, operatorToken);
ok('operator 开工 code=0', opStart.body.code === 0);

// 6. 取消工单级联：全部未完成任务 -> CANCELLED（task1 RUNNING、task4 ASSIGNED、task13 RUNNING、其余 PENDING）
const cancelWo = await put(`/production/work-orders/${woId}/cancel`, {}, adminToken);
ok('取消工单 code=0', cancelWo.body.code === 0);
const afterCancel = (await get(`/production/tasks/for-work-order/${woId}`, adminToken)).body.data;
ok('级联：13 任务全部 CANCELLED', afterCancel.every(t => t.status === 'CANCELLED'), `非取消数=${afterCancel.filter(t => t.status !== 'CANCELLED').length}`);
ok('工单 CANCELLED', (await get(`/production/work-orders/${woId}`, adminToken)).body.data.status === 'CANCELLED');
const cancelAgain = await put(`/production/work-orders/${woId}/cancel`, {}, adminToken);
ok('重复取消幂等（不重复级联）code=0', cancelAgain.body.code === 0);

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
console.log(`\nSQL 复核提示：
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes -e "SELECT action_type, task_id, action_detail FROM mes_trace_record WHERE work_order_id=${woId} ORDER BY id"`);
process.exit(fail > 0 ? 1 : 0);
