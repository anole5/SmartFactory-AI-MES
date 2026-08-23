// T2 知识库后端 + DeepSeek 问答验证（可重复运行）
// 覆盖：文档分页/详情、ask 命中（引用 + 真 LLM）、ask 兜底（fallback）、反馈、
// admin 写 200 / operator 写 403、operator 查询 200、参数校验 400
// 运行：node scripts/verify-t2-ai-knowledge.mjs（后端须已启动，且已导入 07/08 SQL）

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

const admin = await login('admin', 'admin123');
const operator = await login('operator', 'operator123');
ok('admin/operator 登录成功', !!admin.data?.token && !!operator.data?.token);
const at = admin.data.token, ot = operator.data.token;

// 1. 文档分页（admin）
const p1 = await api(at, 'GET', '/ai/knowledge/docs/page?pageNum=1&pageSize=10');
ok('GET /docs/page 200 且 total>=4（种子 4 篇 SOP/FAULT）',
  p1.status === 200 && Number(p1.json.data?.total) >= 4,
  `total=${p1.json.data?.total}`);
const first = p1.json.data?.records?.[0];
ok('分页记录字段齐全（docName/docType/keywords/status 为字符串码）',
  !!first && typeof first.docName === 'string' && typeof first.docType === 'string'
  && typeof first.keywords === 'string' && typeof first.status === 'string',
  `first=${first?.docName}`);

// 2. 文档详情（含 content）
const d1 = await api(at, 'GET', '/ai/knowledge/docs/1');
ok('GET /docs/1 200 且 content 非空（含 ## 段落）',
  d1.status === 200 && typeof d1.json.data?.content === 'string' && d1.json.data.content.includes('## '),
  `docName=${d1.json.data?.docName}`);

// 3. ask 命中：关键词 烧录/BURN_FAIL → 应引用《电视软件烧录作业指导书》（doc id=1）
const ask1 = await api(at, 'POST', '/ai/knowledge/ask', { question: '烧录时报 BURN_FAIL 怎么处理？' });
const refs1 = ask1.json.data?.references ?? [];
ok('POST /ask 命中：200 且 LLM 回答非空且非模板降级（fallback=false）',
  ask1.status === 200 && typeof ask1.json.data?.answer === 'string' && ask1.json.data.answer.length > 10
  && ask1.json.data.fallback === false,
  `len=${ask1.json.data?.answer?.length}`);
ok('命中引用含《电视软件烧录作业指导书》且 recordId 存在',
  refs1.some(r => r.docName?.includes('烧录')) && ask1.json.data?.recordId,
  `refs=${JSON.stringify(refs1.map(r => r.docName))} recordId=${ask1.json.data?.recordId}`);

// 4. ask 兜底：无关问题 → fallback=true、references 空、话术含候选提示
const ask2 = await api(at, 'POST', '/ai/knowledge/ask', { question: '今天天气怎么样' });
ok('POST /ask 兜底：200 fallback=true 且 references 为空',
  ask2.status === 200 && ask2.json.data?.fallback === true
  && Array.isArray(ask2.json.data?.references) && ask2.json.data.references.length === 0,
  `answer=${ask2.json.data?.answer?.slice(0, 30)}...`);
ok('兜底话术含候选文档列表（知识库可查询文档）',
  typeof ask2.json.data?.answer === 'string' && ask2.json.data.answer.includes('知识库'));

// 5. 反馈：命中记录 feedback useful=true → 200；兜底记录 feedback useful=false → 200
const fb1 = await api(at, 'PUT', `/ai/knowledge/qa-records/${ask1.json.data.recordId}/feedback`, { useful: true });
const fb2 = await api(at, 'PUT', `/ai/knowledge/qa-records/${ask2.json.data.recordId}/feedback`, { useful: false });
ok('PUT /qa-records/{id}/feedback 200（有用/无用各一）', fb1.status === 200 && fb2.status === 200,
  `s1=${fb1.status} s2=${fb2.status}`);

// 6. admin 写：create → 200 返回 id；update → 200（验证用文档，keywords 用 VERIFY_TEST 永不干扰召回）
const createDoc = {
  docName: '【验证】T2 临时文档', docType: 'SOP', keywords: 'VERIFY_TEST,验证专用',
  content: '## 验证段落\n本段落仅用于接口验证，不参与业务召回。', status: 'ENABLED', remark: 'T2 verify',
};
const c1 = await api(at, 'POST', '/ai/knowledge/docs', createDoc);
ok('POST /docs（admin）200 返回新 id', c1.status === 200 && Number(c1.json.data) > 0, `id=${c1.json.data}`);
const newId = c1.json.data;
const u1 = await api(at, 'PUT', `/ai/knowledge/docs/${newId}`, { ...createDoc, remark: 'T2 verify updated' });
ok('PUT /docs/{id}（admin）200', u1.status === 200, `status=${u1.status}`);

// 7. operator：查询 200、问答 200、写 403
const p2 = await api(ot, 'GET', '/ai/knowledge/docs/page?pageNum=1&pageSize=5');
const a2 = await api(ot, 'POST', '/ai/knowledge/ask', { question: '黑屏故障怎么排查' });
const c2 = await api(ot, 'POST', '/ai/knowledge/docs', createDoc);
ok('operator 文档分页 200（全员可查 SOP）', p2.status === 200, `total=${p2.json.data?.total}`);
ok('operator ask 200 且命中黑屏手册（引用非空）',
  a2.status === 200 && Array.isArray(a2.json.data?.references) && a2.json.data.references.length > 0,
  `refs=${JSON.stringify((a2.json.data?.references ?? []).map(r => r.docName))}`);
ok('operator POST /docs 403（写权限仅 admin）', c2.status === 403, `status=${c2.status}`);

// 8. 参数校验：question 为空 → 400
const bad = await api(at, 'POST', '/ai/knowledge/ask', { question: '' });
ok('POST /ask 空问题 400（@NotBlank）', bad.status === 400, `status=${bad.status}`);

console.log(`\n结果: ${pass} PASS / ${fail} FAIL`);
process.exit(fail > 0 ? 1 : 0);
