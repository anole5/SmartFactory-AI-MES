// T7-5 AI 周报验证：preview 趋势/环比 / save 幂等 / page reportType 过滤 / 权限 / 参数校验 / 收尾清理
// 前置：后端已重启（含 T5 代码）；13/14 SQL 已导入（近 14 天逐日报工种子在库）
// 运行：node scripts/verify-t7-5-weekly.mjs（仓库根目录）
import { execSync } from 'child_process'

const BASE = 'http://localhost:8080/api'

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'} ${name} ${extra}`)
  cond ? pass++ : fail++
}

const json = async (url, init) => {
  const res = await fetch(url, init)
  return { status: res.status, body: await res.json().catch(() => null) }
}

const login = async (username, password) => {
  const r = await json(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  return r.body?.data?.token
}

const admin = await login('admin', 'admin123')
const operator = await login('operator', 'operator123')
ok('admin/operator 登录成功', !!(admin && operator))

const today = new Date().toISOString().slice(0, 10)
const H = (token) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` })

// ------------------------------------------------------------
// 1. preview：operator 200，content>50，fallback=false，summary 含环比 + 7 行 + 种子良率
// ------------------------------------------------------------
let previewVO = null
{
  const r = await json(BASE + '/ai/weekly/preview', {
    method: 'POST',
    headers: H(operator),
    body: JSON.stringify({ endDate: today }),
  })
  previewVO = r.body?.data
  const dayLines = (previewVO?.summary ?? '').split('\n').filter(l => /^\d{2}-\d{2}：/.test(l))
  ok('preview 200 且 content 长度 > 50',
    r.status === 200 && r.body?.code === 0 && previewVO?.content?.length > 50,
    `status=${r.status} contentLen=${previewVO?.content?.length}`)
  ok('preview fallback=false（pro 档在线生成）', previewVO?.fallback === false,
    `fallback=${previewVO?.fallback}`)
  ok('summary 含"环比"', (previewVO?.summary ?? '').includes('环比'))
  ok('summary 含 7 行本周逐日数据（MM-dd：）', dayLines.length === 7,
    `dayLines=${dayLines.length}\nsummary=\n${previewVO?.summary}`)
  ok('summary 本周/上周合计良率 = 种子故事 98.1%/94.1%',
    (previewVO?.summary ?? '').includes('98.1%') && (previewVO?.summary ?? '').includes('94.1%'),
    `summary=${JSON.stringify(previewVO?.summary)}`)
}

// ------------------------------------------------------------
// 2. save → page reportType=WEEK 1 条；默认 page（DAY）不含 WEEK（冒烟 16.5 回归）
// ------------------------------------------------------------
{
  const s1 = await json(BASE + '/ai/weekly/save', {
    method: 'POST',
    headers: H(operator),
    body: JSON.stringify({ endDate: today, content: previewVO.content }),
  })
  ok('save 200', s1.status === 200 && s1.body?.code === 0, `status=${s1.status} body=${JSON.stringify(s1.body)}`)

  const week = await json(BASE + '/ai/daily/page?pageNum=1&pageSize=10&reportType=WEEK', {
    headers: H(operator),
  })
  const weekRecords = week.body?.data?.records ?? []
  ok('page reportType=WEEK：1 条且 reportDate=today、reportType=WEEK',
    week.status === 200 && weekRecords.length === 1
      && weekRecords[0]?.reportDate === today && weekRecords[0]?.reportType === 'WEEK',
    `total=${week.body?.data?.total} records=${JSON.stringify(weekRecords)}`)

  const day = await json(BASE + '/ai/daily/page?pageNum=1&pageSize=50', {
    headers: H(operator),
  })
  const dayRecords = day.body?.data?.records ?? []
  ok('默认 page（不传 reportType）：全部 reportType=DAY，无 WEEK 混入（16.5 回归）',
    day.status === 200 && dayRecords.length > 0 && dayRecords.every(x => x?.reportType === 'DAY'),
    `total=${day.body?.data?.total} types=${[...new Set(dayRecords.map(x => x?.reportType))].join(',')}`)
}

// ------------------------------------------------------------
// 3. 重复 save 幂等：再存一次 WEEK 仍 1 条
// ------------------------------------------------------------
{
  const s2 = await json(BASE + '/ai/weekly/save', {
    method: 'POST',
    headers: H(operator),
    body: JSON.stringify({ endDate: today, content: previewVO.content + '\n（幂等覆盖验证）' }),
  })
  ok('重复 save 200', s2.status === 200 && s2.body?.code === 0, `status=${s2.status}`)
  const week = await json(BASE + '/ai/daily/page?pageNum=1&pageSize=10&reportType=WEEK', {
    headers: H(operator),
  })
  ok('重复 save 后 reportType=WEEK 仍 1 条且内容已覆盖（幂等 upsert）',
    Number(week.body?.data?.total) === 1 && week.body?.data?.records?.[0]?.content?.includes('幂等覆盖验证'),
    `total=${week.body?.data?.total} content=${JSON.stringify(week.body?.data?.records?.[0]?.content)?.slice(0, 60)}`)
}

// ------------------------------------------------------------
// 4. 参数校验：endDate 缺失 400
// ------------------------------------------------------------
{
  const r = await json(BASE + '/ai/weekly/preview', {
    method: 'POST',
    headers: H(operator),
    body: JSON.stringify({}),
  })
  ok('endDate 缺失 → 400', r.status === 400, `status=${r.status}`)
}

// ------------------------------------------------------------
// 5. 收尾清理：删除本轮保存的 WEEK 周报（保冒烟 T6 干净重放口径不受污染）
// ------------------------------------------------------------
{
  execSync(
    `docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes -e "DELETE FROM mes_ai_report WHERE report_date = '${today}' AND report_type = 'WEEK'"`,
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
  )
  const week = await json(BASE + '/ai/daily/page?pageNum=1&pageSize=10&reportType=WEEK', {
    headers: H(operator),
  })
  ok('清理后 reportType=WEEK 为空（库恢复原状）', Number(week.body?.data?.total) === 0,
    `total=${week.body?.data?.total}`)
}

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`)
// 不主动 process.exit：undici keep-alive 连接未关闭时强制退出会触发 libuv 断言崩溃
process.exitCode = fail > 0 ? 1 : 0
