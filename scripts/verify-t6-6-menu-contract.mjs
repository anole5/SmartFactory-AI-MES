// T6-6 前端追溯改造契约验证：菜单路径↔视图文件契约 + 前端源码契约（静态 grep）+ 菜单元数据
// 一、文件契约：/scheduling /reports-center 两个新菜单路径对应视图文件存在
//   （T6 建骨架保证动态路由不 404，T7 替换为甘特图/报表实现）
// 二、源码契约：traces 四入口 + :179 修复 + 新建批次弹窗；tasks 报工弹窗关键件批次区；
//   api/types/dict 同步（含 ACTION_TYPE 四枚缺失 label 与后端枚举一致）
// 三、后端契约：206/207 菜单图标/权限元数据正确、admin 树 C 级恰好 23 页、F 级不进树
// 运行：node scripts/verify-t6-6-menu-contract.mjs（后端须在跑）

import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const BASE = 'http://localhost:8080/api'
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const FE = (...p) => join(ROOT, 'frontend', 'src', ...p)
const txt = (...p) => readFileSync(FE(...p), 'utf-8')

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'} ${name} ${extra}`)
  cond ? pass++ : fail++
}

// ---------- 一、文件契约 ----------
ok('/scheduling 视图文件存在（动态路由契约）',
  existsSync(FE('views', 'scheduling', 'index.vue')), '')
ok('/reports-center 视图文件存在（动态路由契约）',
  existsSync(FE('views', 'reports-center', 'index.vue')), '')

// ---------- 二、源码契约 ----------
const tracesVue = txt('views', 'traces', 'index.vue')
ok('traces 页四个入口齐全（sn/batch/materialBatch/workOrder）',
  ['value="sn"', 'value="batch"', 'value="materialBatch"', 'value="workOrder"']
    .every(v => tracesVue.includes(v)), '')
ok('traces :179 修复：外部订单号 label 存在且无"生产批次号"误标',
  tracesVue.includes('label="外部订单号"') && !tracesVue.includes('label="生产批次号"'), '')
ok('traces 物料批次反查区：批次信息卡 + 绑定表 + 工单表 + SN 表',
  tracesVue.includes('绑定报工记录') && tracesVue.includes('使用本批次的整机 SN')
    && tracesVue.includes('批次/已用'), '')
ok('traces 新建批次弹窗 + 权限指令（material-batch:create）',
  tracesVue.includes("v-permission=\"'production:material-batch:create'\"")
    && tracesVue.includes('新建物料批次'), '')
ok('traces SN 结果卡含关键件批次表（materialBatches）',
  tracesVue.includes('整机关键件批次') && tracesVue.includes('snResult.materialBatches'), '')

const tasksVue = txt('views', 'tasks', 'index.vue')
ok('tasks 报工弹窗含关键件批次区（traceRequired 过滤生成行）',
  tasksVue.includes('关键件批次') && tasksVue.includes('.filter((m) => m.traceRequired)'), '')
ok('tasks 报工弹窗批次下拉按物料过滤 + 剩余量 label',
  tasksVue.includes('materialId: material.id') && tasksVue.includes('剩 ${b.remainingQty}'), '')
ok('tasks 提交组装 materialBatchBindings（仅含已选项）',
  tasksVue.includes('materialBatchBindings') && tasksVue.includes('row.batchNo'), '')

const apiIndex = txt('api', 'index.ts')
ok('api/index.ts：materialBatchApi.page/create + reportApi.bindBatches + traceApi.byMaterialBatch',
  apiIndex.includes('material-batches/page') && apiIndex.includes('bind-batch')
    && apiIndex.includes('batch-sns'), '')

const typesTs = txt('api', 'types.ts')
ok('types.ts：MaterialBatch/BatchSnTrace/MaterialBatchUsage 类型齐全',
  ['export interface MaterialBatch ', 'export interface BatchSnTrace',
    'export interface MaterialBatchUsage', 'export interface MaterialBatchBind'].every(s => typesTs.includes(s)), '')
ok('types.ts：SnTrace 扩展 materialBatches + WorkReportSave 扩展 materialBatchBindings',
  typesTs.includes('materialBatches?: MaterialBatchUsage[]')
    && typesTs.includes('materialBatchBindings?: MaterialBatchBind[]'), '')

const dictTs = txt('constants', 'dict.ts')
ok('dict.ts ACTION_TYPE 补齐 4 枚缺失 label（与后端枚举中文一致）',
  dictTs.includes("BATCH_BIND: '关键件批次绑定'") && dictTs.includes("AI_SUGGEST: 'AI 处理建议回写'")
    && dictTs.includes("ERP_DONE: '工单完工回传 ERP'") && dictTs.includes("WMS_FINISHED_IN: '成品完工入库'"), '')

// ---------- 三、后端菜单元数据契约 ----------
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

const admin = await login('admin', 'admin123')
ok('admin 登录成功', !!admin.data?.token, '')

const tree = await menus(admin.data.token)
const prodDir = tree.find(n => String(n.id) === '2')
const prodChildren = prodDir?.children ?? []
const sched = prodChildren.find(c => String(c.id) === '206')
const rpt = prodChildren.find(c => String(c.id) === '207')
ok('206 生产排程元数据：path/perm/icon 正确',
  sched?.path === '/scheduling' && sched?.perm === 'production:schedule:query'
    && sched?.icon === 'Histogram',
  `path=${sched?.path} perm=${sched?.perm} icon=${sched?.icon}`)
ok('207 报表中心元数据：path/perm/icon 正确',
  rpt?.path === '/reports-center' && rpt?.perm === 'production:report:center:query'
    && rpt?.icon === 'PieChart',
  `path=${rpt?.path} perm=${rpt?.perm} icon=${rpt?.icon}`)

const cCount = (nodes) => nodes.reduce((acc, n) => acc + (n.menuType === 'C' ? 1 : 0)
  + cCount(n.children ?? []), 0)
ok('admin 树 C 级菜单恰好 24 页（F 级按钮不进树）', cCount(tree) === 24, `c=${cCount(tree)}`)

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
