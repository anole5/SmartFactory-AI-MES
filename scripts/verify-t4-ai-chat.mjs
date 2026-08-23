// T4 统一 AI 助手验证（可重复运行）
// 覆盖：四类意图路由（KNOWLEDGE/EXCEPTION/REPORT/OVERVIEW）、异常单号识别、
// LLM 分类兜底、问答记录落库（recordId）、空问题 400
// 运行：node scripts/verify-t4-ai-chat.mjs（后端须已启动，且已导入 07/08 SQL）

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

const operator = await login('operator', 'operator123');
const qa = await login('qa', 'qa123');
ok('operator/qa 登录成功', !!operator.data?.token && !!qa.data?.token);
const ot = operator.data.token, qt = qa.data.token;

// 1. 知识库意图：规则命中（SOP/流程）→ RAG 问答带引用
const c1 = await api(ot, 'POST', '/ai/chat', { question: '软件烧录的SOP流程是什么' });
ok('知识库意图：intent=KNOWLEDGE 且引用含烧录指导书',
  c1.status === 200 && c1.json.data?.intent === 'KNOWLEDGE'
  && (c1.json.data?.references ?? []).some(r => r.docName?.includes('烧录')),
  `refs=${JSON.stringify((c1.json.data?.references ?? []).map(r => r.docName))}`);
ok('知识库意图：answer 非空且 recordId 已落库', typeof c1.json.data?.answer === 'string'
  && c1.json.data.answer.length > 10 && c1.json.data.recordId, `recordId=${c1.json.data?.recordId}`);

// 2. 异常意图（无单号）：故障问题 → 转知识库 FAULT_GUIDE 检索
const c2 = await api(ot, 'POST', '/ai/chat', { question: '黑屏故障怎么排查' });
ok('异常意图（无单号）转知识库：intent=KNOWLEDGE 且引用含黑屏手册',
  c2.status === 200 && c2.json.data?.intent === 'KNOWLEDGE'
  && (c2.json.data?.references ?? []).some(r => r.docName?.includes('黑屏')),
  `refs=${JSON.stringify((c2.json.data?.references ?? []).map(r => r.docName))}`);

// 3. 异常意图（带单号）：qa 建异常单 → 问 "EXPxxx 怎么处理" → pro 建议
const ex = await api(qt, 'POST', '/quality/exceptions', {
  defectCode: 'FLOWER_SCREEN', description: 'T4 验证：整机画面花屏闪烁', remark: 'T4 verify',
});
ok('qa 创建异常单 200', ex.status === 200 && Number(ex.json.data) > 0, `id=${ex.json.data}`);
const g = await api(qt, 'GET', `/ai/assistant/suggestion/${ex.json.data}`);
const exceptionNo = g.json.data?.exceptionNo;
const c3 = await api(ot, 'POST', '/ai/chat', { question: `${exceptionNo} 怎么处理？` });
ok('异常意图（带单号）：intent=EXCEPTION 且 exceptionId 匹配',
  c3.status === 200 && c3.json.data?.intent === 'EXCEPTION'
  && String(c3.json.data?.exceptionId) === String(ex.json.data),
  `exceptionId=${c3.json.data?.exceptionId} exceptionNo=${exceptionNo}`);
ok('异常意图：pro 档真实推理建议（非降级）',
  typeof c3.json.data?.answer === 'string' && c3.json.data.answer.length > 50 && c3.json.data.fallback === false,
  `len=${c3.json.data?.answer?.length}`);

// 4. 日报意图：规则命中 → 数据聚合 + flash 润色
const c4 = await api(ot, 'POST', '/ai/chat', { question: '生成今天的生产日报' });
ok('日报意图：intent=REPORT 且 reportDate=今天、summary 含设备统计',
  c4.status === 200 && c4.json.data?.intent === 'REPORT' && c4.json.data?.reportDate
  && typeof c4.json.data?.summary === 'string' && c4.json.data.summary.includes('设备'),
  `reportDate=${c4.json.data?.reportDate}`);
ok('日报意图：answer 非空（flash 润色）', typeof c4.json.data?.answer === 'string'
  && c4.json.data.answer.length > 20, `len=${c4.json.data?.answer?.length}`);

// 5. 概况意图：规则命中 → 实时数据 + pro 综合分析
const c5 = await api(ot, 'POST', '/ai/chat', { question: '现在工厂整体情况怎么样' });
ok('概况意图：intent=OVERVIEW 且 summary 含工单/设备数据',
  c5.status === 200 && c5.json.data?.intent === 'OVERVIEW'
  && typeof c5.json.data?.summary === 'string' && c5.json.data.summary.includes('工单'),
  `summary=${c5.json.data?.summary?.slice(0, 40)}...`);
ok('概况意图：pro 档综合分析非空（非降级）',
  typeof c5.json.data?.answer === 'string' && c5.json.data.answer.length > 30 && c5.json.data.fallback === false,
  `len=${c5.json.data?.answer?.length}`);

// 6. LLM 分类兜底：无规则关键词 → flash 分类，仍 200 且 intent ∈ 四类
const c6 = await api(ot, 'POST', '/ai/chat', { question: '我们工厂最近的表现如何' });
ok('LLM 分类兜底：200 且 intent ∈ 四类', c6.status === 200
  && ['OVERVIEW', 'KNOWLEDGE', 'EXCEPTION', 'REPORT'].includes(c6.json.data?.intent),
  `intent=${c6.json.data?.intent}`);

// 7. 参数校验：空问题 400
const bad = await api(ot, 'POST', '/ai/chat', { question: '' });
ok('空问题 400（@NotBlank）', bad.status === 400, `status=${bad.status}`);

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
