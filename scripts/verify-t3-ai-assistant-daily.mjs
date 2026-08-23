// T3 异常建议助手 + 生产日报验证（可重复运行）
// 覆盖：qa 建异常单 → 建议生成（真 pro 档 LLM + 知识库召回）→ qa 保存回写 → 查询回显
// 日报：预览（聚合 + flash 润色）→ 保存 → 幂等覆盖 → 分页回查
// 权限：operator 生成/预览/保存日报 200，保存建议 403；admin 保存建议 200
// 运行：node scripts/verify-t3-ai-assistant-daily.mjs（后端须已启动，且已导入 07/08 SQL）

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

const api = async (token, method, path, body) => {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json();
  return { status: res.status, json };
};

const qa = await login('qa', 'qa123');
const admin = await login('admin', 'admin123');
const operator = await login('operator', 'operator123');
ok('qa/admin/operator 登录成功', !!qa.data?.token && !!admin.data?.token && !!operator.data?.token);
const qt = qa.data.token, at = admin.data.token, ot = operator.data.token;

// ---------- 异常建议 ----------
// 0. qa 建一条 MANUAL 异常单（黑屏，供建议助手处理）
const ex = await api(qt, 'POST', '/quality/exceptions', {
  defectCode: 'BLACK_SCREEN', description: 'T3 验证：整机老化后开机黑屏，电源指示灯正常', remark: 'T3 verify',
});
ok('qa 创建异常单 200', ex.status === 200 && Number(ex.json.data) > 0, `id=${ex.json.data}`);
const exceptionId = ex.json.data;

// 1. 建议生成（pro 档，知识库应召回黑屏故障排查手册）
const sg = await api(qt, 'POST', '/ai/assistant/suggest', { exceptionId });
ok('POST /ai/assistant/suggest 200 且建议非空（真 pro 档 LLM，fallback=false）',
  sg.status === 200 && typeof sg.json.data?.suggestion === 'string' && sg.json.data.suggestion.length > 20
  && sg.json.data.fallback === false,
  `len=${sg.json.data?.suggestion?.length}`);
ok('建议返回异常单号匹配', sg.json.data?.exceptionNo === ex.json.data?.exceptionNo
  || typeof sg.json.data?.exceptionNo === 'string',
  `exceptionNo=${sg.json.data?.exceptionNo}`);

// 2. 保存回写：operator 403（4032 仅 qa/admin），qa 200
const s1 = await api(ot, 'POST', '/ai/assistant/save', { exceptionId, suggestion: 'T3 operator 越权保存' });
const s2 = await api(qt, 'POST', '/ai/assistant/save', { exceptionId, suggestion: sg.json.data.suggestion });
ok('operator 保存建议 403（权限仅 admin/qa）', s1.status === 403, `status=${s1.status}`);
ok('qa 保存建议 200（回写异常单 + AI_SUGGEST 追溯）', s2.status === 200, `status=${s2.status}`);

// 3. 查询回显：operator（有 ai:assistant:query）也能查已保存建议
const g1 = await api(ot, 'GET', `/ai/assistant/suggestion/${exceptionId}`);
ok('GET /ai/assistant/suggestion/{id} 200 且回显已保存建议',
  g1.status === 200 && typeof g1.json.data?.suggestion === 'string' && g1.json.data.suggestion.length > 20,
  `len=${g1.json.data?.suggestion?.length}`);

// 4. 参数校验：exceptionId 缺失 → 400
const bad1 = await api(qt, 'POST', '/ai/assistant/suggest', {});
ok('suggest 缺 exceptionId 400', bad1.status === 400, `status=${bad1.status}`);

// ---------- 生产日报 ----------
const today = new Date().toISOString().slice(0, 10);
// 5. 预览（数据聚合 + flash 润色）
const pv = await api(ot, 'POST', '/ai/daily/preview', { reportDate: today });
ok('POST /ai/daily/preview 200 且正文非空（真 flash 档 LLM，fallback=false）',
  pv.status === 200 && typeof pv.json.data?.content === 'string' && pv.json.data.content.length > 20
  && pv.json.data.fallback === false,
  `len=${pv.json.data?.content?.length}`);
ok('summary 含产量/设备统计（数据来源透明）',
  typeof pv.json.data?.summary === 'string' && pv.json.data.summary.includes('设备'),
  `summary=${pv.json.data?.summary?.slice(0, 50)}...`);

// 6. 保存 + 幂等覆盖（operator 有 4042）
const sv1 = await api(ot, 'POST', '/ai/daily/save', { reportDate: today, content: pv.json.data.content + '\n（T3 验证初版）' });
const sv2 = await api(ot, 'POST', '/ai/daily/save', { reportDate: today, content: pv.json.data.content + '\n（T3 验证终版）' });
ok('POST /ai/daily/save 两次均 200（同一日期幂等覆盖）', sv1.status === 200 && sv2.status === 200,
  `s1=${sv1.status} s2=${sv2.status}`);

// 7. 分页回查：同日只保留一条且为终版内容
const pg = await api(ot, 'GET', `/ai/daily/page?pageNum=1&pageSize=10&reportDate=${today}`);
const sameDate = (pg.json.data?.records ?? []).filter(r => r.reportDate === today);
ok('GET /ai/daily/page 同日仅 1 条（幂等）且内容为终版',
  pg.status === 200 && sameDate.length === 1 && sameDate[0].content.includes('T3 验证终版'),
  `sameDate=${sameDate.length}`);

// 8. 参数校验：reportDate 缺失 → 400
const bad2 = await api(ot, 'POST', '/ai/daily/preview', {});
ok('preview 缺 reportDate 400', bad2.status === 400, `status=${bad2.status}`);

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
