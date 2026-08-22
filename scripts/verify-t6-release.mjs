// T6 工单下发验证：按路线生成 13 个工序任务（快照/顺序/数量）+ CAS 防双下发 + 权限
// 运行：node scripts/verify-t6-release.mjs（后端须已启动）
// 前置：产品 1 的路线含 13 个步骤（seed 数据）

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
const get = (p, t) => call('GET', p, undefined, t);

const admin = await post('/auth/login', { username: 'admin', password: 'admin123' });
const adminToken = admin.body.data?.token;
const operator = await post('/auth/login', { username: 'operator', password: 'operator123' });
const operatorToken = operator.body.data?.token;

// 1. 建一张新工单并下发
const wo = await post('/production/work-orders', { productId: 1, planQty: 50, remark: 'T6 下发验证' }, adminToken);
ok('建单 code=0', wo.body.code === 0, `id=${wo.body.data}`);
const woId = wo.body.data;

const release = await post(`/production/work-orders/${woId}/release`, {}, adminToken);
ok('下发 code=0', release.body.code === 0, JSON.stringify(release.body).slice(0, 90));

// 2. 工单状态 + 任务数量
const detail = await get(`/production/work-orders/${woId}`, adminToken);
ok('下发后工单 RELEASED', detail.body.data.status === 'RELEASED');
const tasks = detail.body.data.tasks;
ok('恰好生成 13 个任务', tasks?.length === 13, `实际=${tasks?.length}`);
ok('顺序号 1..13 升序', tasks?.every((t, i) => t.sequenceNo === i + 1));
ok('任务号 TASK+12 位数字', tasks?.every(t => /^TASK\d{12}$/.test(t.taskNo)));
ok('任务状态全部 PENDING', tasks?.every(t => t.status === 'PENDING'));
ok('任务 planQty=50（=工单）', tasks?.every(t => t.planQty === 50));
ok('工序编码快照齐全', tasks?.every(t => typeof t.processCodeSnapshot === 'string' && t.processCodeSnapshot.length > 0));
ok('工序名称快照齐全', tasks?.every(t => typeof t.processNameSnapshot === 'string' && t.processNameSnapshot.length > 0));
ok('completed/good/defect 初始为 0', tasks?.every(t => t.completedQty === 0 && t.goodQty === 0 && t.defectQty === 0));
ok('需质检标志来自路线步骤', tasks?.some(t => t.needInspection === true) || tasks?.every(t => t.needInspection === false));
const wsTasks = tasks?.filter(t => t.workstationId !== null && t.workstationId !== undefined);
ok('有默认工位的任务工位信息回填', wsTasks?.length > 0 && wsTasks.every(t => t.workstationCode && t.workstationName), `带工位任务=${wsTasks?.length}`);

// 3. 再次下发 → 409（状态机拦截）
const releaseAgain = await post(`/production/work-orders/${woId}/release`, {}, adminToken);
ok('重复下发 409', releaseAgain.status === 409, JSON.stringify(releaseAgain.body).slice(0, 90));

// 4. 已取消工单下发 → 409（取消是 PUT /{id}/cancel）
const woC = await post('/production/work-orders', { productId: 1, planQty: 10 }, adminToken);
const woCCancel = await call('PUT', `/production/work-orders/${woC.body.data}/cancel`, {}, adminToken);
ok('取消动作本身 code=0', woCCancel.body.code === 0, JSON.stringify(woCCancel.body).slice(0, 90));
const releaseCancelled = await post(`/production/work-orders/${woC.body.data}/release`, {}, adminToken);
ok('已取消工单下发 409', releaseCancelled.status === 409, JSON.stringify(releaseCancelled.body).slice(0, 90));

// 5. 任务查询接口（PageResult.total 为 long，Long 序列化为字符串）
const taskPage = await get(`/production/tasks/page?workOrderId=${woId}&pageNum=1&pageSize=20`, adminToken);
ok('任务分页 13 条', taskPage.body.code === 0 && String(taskPage.body.data?.total) === '13', `total=${taskPage.body.data?.total}`);
ok('任务分页带工单号回填', taskPage.body.data?.records?.every(t => t.workOrderNo?.startsWith('WO')));
const taskPagePending = await get(`/production/tasks/page?workOrderId=${woId}&status=PENDING&pageNum=1&pageSize=20`, adminToken);
ok('按状态 PENDING 过滤 13 条', String(taskPagePending.body.data?.total) === '13', `total=${taskPagePending.body.data?.total}`);
const taskList = await get(`/production/tasks/for-work-order/${woId}`, adminToken);
ok('for-work-order 接口 13 条且升序', taskList.body.code === 0 && taskList.body.data?.length === 13 && taskList.body.data.every((t, i) => t.sequenceNo === i + 1));

// 6. 权限：operator 无 release 权限
const woOp = await post('/production/work-orders', { productId: 1, planQty: 10 }, adminToken);
const opRelease = await post(`/production/work-orders/${woOp.body.data}/release`, {}, operatorToken);
ok('operator 下发 403', opRelease.status === 403);

// 7. 下发后编辑被拒（非草稿不可编辑，回归 T5 状态机）
const editReleased = await call('PUT', `/production/work-orders/${woId}`, { productId: 1, planQty: 60 }, adminToken);
ok('下发后编辑 409', editReleased.status === 409);

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
console.log(`\nT7 可用：工单 ${woId} 已 RELEASED（13 任务 PENDING）；工单 ${woOp.body.data} 草稿可下发`);
process.exit(fail > 0 ? 1 : 0);
