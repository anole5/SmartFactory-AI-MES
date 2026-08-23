// T6-1 第 6 周 SQL 层契约验证：/auth/menus 角色菜单树 + 登录权限列表
// 验证 12-seed 新增菜单（2041/206/2061/207/2071）与角色授权边界：
// 1) planning 树含 /scheduling、/reports-center；operator 不含（角色差异）
// 2) 排程/报表/批次新增三个新权限：admin、planning 有，operator 无
// 3) 生产管理目录下 205 看板 < 206 排程 < 207 报表（order_num 生效）
// 运行：node scripts/verify-t6-1-schema.mjs（后端须在跑）

const BASE = 'http://localhost:8080/api'
let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'} ${name} ${extra}`)
  cond ? pass++ : fail++
}

const login = async (username, password) => {
  const res = await fetch(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  return res.json()
}

const menus = async (token) => {
  const res = await fetch(BASE + '/auth/menus', {
    headers: { Authorization: `Bearer ${token}` },
  })
  return (await res.json()).data
}

const pathsOf = (tree, out = []) => {
  for (const node of tree ?? []) {
    if (node.menuType === 'C' && node.path) out.push(node.path)
    pathsOf(node.children, out)
  }
  return out
}

const NEW_PERMS = ['production:schedule:run', 'production:report:export', 'production:material-batch:create']

const admin = await login('admin', 'admin123')
const planning = await login('planning', 'planning123')
const operator = await login('operator', 'operator123')
ok('admin/planning/operator 登录成功',
  !!admin.data?.token && !!planning.data?.token && !!operator.data?.token)

const pPaths = pathsOf(await menus(planning.data.token))
ok('planning 菜单树含 /scheduling 与 /reports-center',
  pPaths.includes('/scheduling') && pPaths.includes('/reports-center'),
  `paths=${pPaths.join(',')}`)

const oPaths = pathsOf(await menus(operator.data.token))
ok('operator 菜单树不含 /scheduling /reports-center（角色差异）',
  !oPaths.includes('/scheduling') && !oPaths.includes('/reports-center'), '')

const aPaths = pathsOf(await menus(admin.data.token))
ok('admin 菜单树含 /scheduling 与 /reports-center',
  aPaths.includes('/scheduling') && aPaths.includes('/reports-center'), '')

const aPerms = admin.data.permissions ?? []
ok('admin 含排程/报表导出/批次新增权限',
  NEW_PERMS.every(p => aPerms.includes(p)),
  `missing=${NEW_PERMS.filter(p => !aPerms.includes(p)).join(',')}`)
const pPerms = planning.data.permissions ?? []
ok('planning 含排程/报表导出/批次新增权限',
  NEW_PERMS.every(p => pPerms.includes(p)),
  `missing=${NEW_PERMS.filter(p => !pPerms.includes(p)).join(',')}`)
const oPerms = operator.data.permissions ?? []
ok('operator 无排程/报表导出/批次新增权限',
  NEW_PERMS.every(p => !oPerms.includes(p)),
  `has=${NEW_PERMS.filter(p => oPerms.includes(p)).join(',')}`)

const aTree = await menus(admin.data.token)
const prodDir = aTree.find(n => String(n.id) === '2')
const prodIds = (prodDir?.children ?? []).map(c => String(c.id))
ok('生产管理目录下 205 看板 < 206 排程 < 207 报表',
  prodIds.indexOf('205') < prodIds.indexOf('206') && prodIds.indexOf('206') < prodIds.indexOf('207'),
  `ids=${prodIds.join(',')}`)

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
