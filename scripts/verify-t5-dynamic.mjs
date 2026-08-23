// T5-6 动态路由契约验证（无头替代浏览器手测的自动化部分）：
// 前端 stores/menu.ts 用 import.meta.glob('@/views/**/index.vue') 按 path 约定反查组件，
// 本脚本拉真实 /auth/menus 菜单树，校验：
// 1) 每个角色菜单树的 C 级 path 都能在 frontend/src/views{path}/index.vue 找到真实文件
//    （菜单路径与视图文件契约不破，动态注册必成功）
// 2) admin 覆盖全部 24 个页面；operator 无 /erp-orders /inventory（角色差异=菜单差异）
// 3) 404 兜底页存在（守卫注册 catch-all 的组件）
// 运行：node scripts/verify-t5-dynamic.mjs（后端须在跑，且已加载 T4 /auth/menus）

import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const BASE = 'http://localhost:8080/api'
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
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

const viewExists = (path) =>
  existsSync(join(ROOT, 'frontend', 'src', 'views', path.replace(/^\//, ''), 'index.vue'))

const ALL_PAGES = [
  '/products', '/materials', '/processes', '/workstations', '/boms', '/routes', '/equipment',
  '/work-orders', '/tasks', '/reports', '/traces', '/dashboard',
  '/inspection-tasks', '/defects', '/exceptions',
  '/ai-chat', '/knowledge', '/ai-assistant', '/ai-daily',
  '/erp-orders', '/inventory', '/tv-demo',
  '/scheduling', '/reports-center',
]

const admin = await login('admin', 'admin123')
const operator = await login('operator', 'operator123')
ok('admin/operator 登录成功', !!admin.data?.token && !!operator.data?.token)

const aPaths = pathsOf(await menus(admin.data.token))
const oPaths = pathsOf(await menus(operator.data.token))

ok('admin 菜单树覆盖全部 24 个页面路径', ALL_PAGES.every(p => aPaths.includes(p)),
  `missing=${ALL_PAGES.filter(p => !aPaths.includes(p)).join(',')}`)
ok('operator 菜单树无 /erp-orders 与 /inventory（动态菜单角色差异）',
  !oPaths.includes('/erp-orders') && !oPaths.includes('/inventory'),
  `paths=${oPaths.join(',')}`)
ok('operator 仍含 /dashboard 与 /tv-demo（login 后 redirect 目标可解析）',
  oPaths.includes('/dashboard') && oPaths.includes('/tv-demo'), '')
ok('admin 每个 C 级 path 都有对应视图文件（glob 反查契约）',
  aPaths.every(viewExists),
  `missing=${aPaths.filter(p => !viewExists(p)).join(',')}`)
ok('operator 每个 C 级 path 都有对应视图文件', oPaths.every(viewExists),
  `missing=${oPaths.filter(p => !viewExists(p)).join(',')}`)
ok('404 兜底页存在（守卫注册 catch-all 的组件）',
  existsSync(join(ROOT, 'frontend', 'src', 'views', 'not-found', 'index.vue')), '')

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
