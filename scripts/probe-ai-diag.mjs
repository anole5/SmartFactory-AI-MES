// 诊断探针（未提交，一次性排查用）：
// 1) /ai/chat 问"生产日期"三个变体 → 意图/回答/fallback/引用
// 2) TEI embed 问题 → 直查 Qdrant：无阈值 top8 分数 + 0.30 阈值下命中数
// 3) 集合点数
import process from 'node:process';

const API = 'http://localhost:8080/api';
const TEI = 'http://localhost:8081';
const QDRANT = 'http://localhost:6333';
const COLLECTION = 'mes-knowledge-sections';

const QUESTIONS = [
  '生产日期是什么时候？',
  '电视的生产日期怎么查？',
  '这台电视是什么时候生产的？',
];

async function api(path, opts = {}) {
  const res = await fetch(API + path, {
    headers: { 'Content-Type': 'application/json', ...(opts.token ? { Authorization: 'Bearer ' + opts.token } : {}) },
    method: opts.method || (opts.body ? 'POST' : 'GET'),
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { json = { raw: text.slice(0, 200) }; }
  return { status: res.status, json };
}

let fail = 0;
const check = (label, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${extra ? '  | ' + extra : ''}`);
  if (!ok) fail++;
};

// ---------- 1. 登录 ----------
const login = await api('/auth/login', { body: { username: 'admin', password: 'admin123' } });
check('登录 admin', login.status === 200 && login.json?.code === 200);
const token = login.json?.data?.token;
if (!token) {
  console.error('无法登录，终止');
  process.exitCode = 1;
} else {
  // ---------- 2. 三个问法走 /ai/chat ----------
  for (const q of QUESTIONS) {
    const r = await api('/ai/chat', { token, body: { question: q } });
    const d = r.json?.data;
    console.log(`\n=== /ai/chat 问：「${q}」`);
    console.log(`  HTTP ${r.status}`);
    if (r.status !== 200 || !d) { console.log('  body:', JSON.stringify(r.json).slice(0, 300)); continue; }
    console.log(`  intent=${d.intent}  fallback=${d.fallback}  recordId=${d.recordId}`);
    console.log(`  references=${JSON.stringify(d.references)}`);
    console.log(`  answer=${JSON.stringify(d.answer)}`);
    check(`chat「${q}」200`, true, `intent=${d.intent}`);
  }

  // ---------- 3. TEI embed + Qdrant 直查（无阈值） ----------
  const tei = await fetch(TEI + '/embed', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ inputs: [QUESTIONS[0]] }),
  });
  const teiJson = await tei.json();
  const vec = teiJson?.[0];
  console.log(`\n=== TEI embed「${QUESTIONS[0]}」→ ${vec?.length} 维`);
  check('TEI embed 1024 维', vec?.length === 1024);

  if (vec) {
    const searchNoCut = await fetch(QDRANT + '/collections/' + COLLECTION + '/points/search', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ vector: vec, limit: 8, with_payload: true }),
    }).then(r => r.json());
    console.log('\n=== Qdrant 检索（无阈值，top8 原始分数）===');
    for (const p of searchNoCut?.result ?? []) {
      console.log(`  score=${p.score.toFixed(4)}  doc_id=${p.payload?.doc_id}  [${p.payload?.doc_name}]  idx=${p.payload?.section_idx}`);
    }

    const searchCut = await fetch(QDRANT + '/collections/' + COLLECTION + '/points/search', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ vector: vec, limit: 8, score_threshold: 0.30, with_payload: true }),
    }).then(r => r.json());
    const nCut = (searchCut?.result ?? []).length;
    console.log(`\n=== Qdrant 检索（阈值 0.30，线上配置）→ 命中 ${nCut} 个点 ===`);
    check('阈值 0.30 下命中数记录', true, `hit=${nCut}`);
  }

  // ---------- 4. 集合信息 ----------
  const info = await fetch(QDRANT + '/collections/' + COLLECTION).then(r => r.json());
  console.log(`\n=== 集合 ${COLLECTION}：points_count=${info?.result?.points_count}，向量=${JSON.stringify(info?.result?.config?.params?.vectors)} ===`);
}

process.exitCode = fail > 0 ? 1 : 0;
