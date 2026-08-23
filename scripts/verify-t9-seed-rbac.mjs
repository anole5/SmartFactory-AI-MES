// T9 种子 RBAC 边界验证：四角色登录返回的权限集合边界（第 3 周新增权限）
// 断言依据：06-seed-week3.sql —— admin 全部；operator/planning += 204/205；
// INSPECTOR(qa) = 201-205 + 质检 9 项；设备 F 级 1071-1073 仅 admin
// 运行：node scripts/verify-t9-seed-rbac.mjs（后端须已启动）

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

// 第 3 周全部新增权限（15 项：质检 9 + 追溯/看板 2 + 设备 4）
const WEEK3_PERMS = [
  'quality:inspection-task:query', 'quality:inspection-task:start', 'quality:inspection-record:create',
  'quality:defect:query', 'quality:defect:to-exception',
  'quality:exception:query', 'quality:exception:create', 'quality:exception:process', 'quality:exception:close',
  'production:trace:query', 'production:dashboard:query',
  'master:equipment:list', 'master:equipment:create', 'master:equipment:update', 'master:equipment:status',
];

const admin = await login('admin', 'admin123');
const qa = await login('qa', 'qa123');
const operator = await login('operator', 'operator123');
const planning = await login('planning', 'planning123');
const ap = admin.data?.permissions ?? [];
const qp = qa.data?.permissions ?? [];
const op = operator.data?.permissions ?? [];
const pp = planning.data?.permissions ?? [];
ok('四角色登录成功且都下发权限集合', !!admin.data?.token && !!qa.data?.token && !!operator.data?.token && !!planning.data?.token
  && ap.length > 0 && qp.length > 0 && op.length > 0 && pp.length > 0,
  `admin=${ap.length} qa=${qp.length} operator=${op.length} planning=${pp.length}`);

// 1. admin 含全部第 3 周新权限
const missing = WEEK3_PERMS.filter(p => !ap.includes(p));
ok('admin 含全部 15 项第 3 周新权限', missing.length === 0, `missing=${JSON.stringify(missing)}`);

// 2. qa（INSPECTOR）：含质检 9 项 + 追溯/看板，不含生产/设备写权限
const qaQuality = WEEK3_PERMS.filter(p => p.startsWith('quality:'));
ok('qa 含质检 9 项权限', qaQuality.every(p => qp.includes(p)),
  `missing=${JSON.stringify(qaQuality.filter(p => !qp.includes(p)))}`);
ok('qa 含追溯/看板查询（204/205）',
  qp.includes('production:trace:query') && qp.includes('production:dashboard:query'));
ok('qa 不含生产写权限（work-order:create/release、report:create）',
  !qp.includes('production:work-order:create') && !qp.includes('production:work-order:release')
  && !qp.includes('production:report:create'));
ok('qa 不含设备写权限（master:equipment:create）', !qp.includes('master:equipment:create'));
ok('qa 不含看板/追溯之外的生产管理权限（task:assign）', !qp.includes('production:task:assign'));

// 3. operator：无任何 quality:*，有追溯/看板查询
ok('operator 无任何 quality:* 权限', op.every(p => !p.startsWith('quality:')),
  `quality=${JSON.stringify(op.filter(p => p.startsWith('quality:')))}`);
ok('operator 含追溯/看板查询（204/205）',
  op.includes('production:trace:query') && op.includes('production:dashboard:query'));
ok('operator 不含设备写权限', !op.includes('master:equipment:create')
  && !op.includes('master:equipment:update') && !op.includes('master:equipment:status'));

// 4. planning：同 operator 的质量边界，有看板
ok('planning 无任何 quality:* 权限', pp.every(p => !p.startsWith('quality:')),
  `quality=${JSON.stringify(pp.filter(p => p.startsWith('quality:')))}`);
ok('planning 含看板查询（205）', pp.includes('production:dashboard:query'));
ok('planning 不含设备写权限', !pp.includes('master:equipment:create'));

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
console.log('菜单行 SQL 复核提示（Git Bash）：');
console.log("  docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes \\");
console.log("    -e \"SELECT COUNT(*) menu_cnt FROM sys_menu; SELECT role_id, COUNT(*) FROM sys_role_menu GROUP BY role_id;\"");
process.exit(fail > 0 ? 1 : 0);
