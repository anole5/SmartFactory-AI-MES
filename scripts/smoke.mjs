// SmartFactory-MES 冒烟测试脚本（Node 18+ 内置 fetch）
const BASE = 'http://localhost:8080/api';
let pass = 0, fail = 0;

async function req(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* 非 JSON */ }
  return { status: res.status, json };
}

function check(name, cond, detail) {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; console.log(`  FAIL  ${name}  => ${detail}`); }
}

// 1. 产品分页：应返回种子数据（3 条，含 TV-AOC-55U4K-001）
{
  const { status, json } = await req('GET', '/master/products/page?pageNum=1&pageSize=10');
  check('GET /master/products/page -> code:0', status === 200 && json?.code === 0, `status=${status} body=${JSON.stringify(json)?.slice(0, 200)}`);
  const records = json?.data?.records ?? [];
  check('种子产品 3 条', records.length === 3, `got ${records.length}`);
  check('主产品 TV-AOC-55U4K-001 存在且 ENABLED',
    records.some(r => r.productCode === 'TV-AOC-55U4K-001' && r.status === 'ENABLED'),
    JSON.stringify(records.map(r => r.productCode)));
}

// 2. 物料分页：20 种
{
  const { json } = await req('GET', '/master/materials/page?pageNum=1&pageSize=50');
  // 注意：JacksonConfig 将 Long 序列化为字符串（防 JS 丢精度），total 是 "20"
  check('物料 20 种', String(json?.data?.total) === '20', `total=${json?.data?.total}`);
}

// 3. 建产品 -> 重复编码 409
{
  const r1 = await req('POST', '/master/products', { productCode: 'T-001', productName: '测试产品', productType: '测试', specification: 'SP-1', unit: '台' });
  check('POST /master/products 创建成功', r1.status === 200 && r1.json?.code === 0, JSON.stringify(r1.json)?.slice(0, 150));
  const newId = r1.json?.data;
  const r2 = await req('POST', '/master/products', { productCode: 'T-001', productName: '重复', specification: 'X', unit: '台' });
  check('重复编码 -> 409', r2.status === 409 && r2.json?.code === 409, `status=${r2.status} body=${JSON.stringify(r2.json)?.slice(0, 150)}`);

  // 清理：删掉测试产品（新建成 DISABLED，无引用，可删）
  const r3 = await req('DELETE', `/master/products/${newId}`);
  check('删除测试产品', r3.json?.code === 0, JSON.stringify(r3.json));
}

// 4. 用启用产品(id=1)建 BOM 带 2 行明细 -> 详情含快照
let bomId = null;
{
  const r1 = await req('POST', '/master/boms', {
    productId: 1,
    version: 'V1',
    items: [
      { materialId: 1, requiredQty: 1, lossRate: 0.5, remark: '测试行1' },
      { materialId: 2, requiredQty: 2, lossRate: 0, remark: '测试行2' },
    ],
  });
  check('POST /master/boms 创建成功', r1.status === 200 && r1.json?.code === 0, JSON.stringify(r1.json)?.slice(0, 200));
  bomId = r1.json?.data;
  const r2 = await req('GET', `/master/boms/${bomId}`);
  const items = r2.json?.data?.items ?? [];
  check('BOM 详情含 2 行明细', items.length === 2, `got ${items.length}`);
  check('明细快照已回填', items.length > 0 && items.every(i => i.materialCodeSnapshot && i.materialNameSnapshot && i.unitSnapshot),
    JSON.stringify(items.map(i => i.materialCodeSnapshot)));
  check('明细行号 1..2', items.map(i => i.lineNo).join(',') === '1,2', JSON.stringify(items.map(i => i.lineNo)));
}

// 5. BOM 激活 -> 再编辑 409
{
  const r1 = await req('PUT', `/master/boms/${bomId}/status`, { status: 'ACTIVE' });
  check('BOM DRAFT->ACTIVE 成功', r1.json?.code === 0, JSON.stringify(r1.json)?.slice(0, 150));
  const r2 = await req('PUT', `/master/boms/${bomId}`, { productId: 1, items: [{ materialId: 1, requiredQty: 3 }] });
  check('ACTIVE BOM 编辑 -> 409', r2.json?.code === 409, JSON.stringify(r2.json)?.slice(0, 150));
  const r3 = await req('PUT', `/master/boms/${bomId}/status`, { status: 'DRAFT' });
  check('ACTIVE->DRAFT 回退 -> 409', r3.json?.code === 409, JSON.stringify(r3.json)?.slice(0, 150));
}

// 6. 用启用产品(id=1)建工艺路线 3 步 -> 详情含步骤
let routeId = null;
{
  const r1 = await req('POST', '/master/routes', {
    productId: 1,
    version: 'V1',
    steps: [
      { processId: 1, workstationId: 1 },
      { processId: 2, workstationId: 2, needInspection: true },
      { processId: 3, workstationId: null },
    ],
  });
  check('POST /master/routes 创建成功', r1.status === 200 && r1.json?.code === 0, JSON.stringify(r1.json)?.slice(0, 200));
  routeId = r1.json?.data;
  const r2 = await req('GET', `/master/routes/${routeId}`);
  const steps = r2.json?.data?.steps ?? [];
  check('路线详情含 3 步', steps.length === 3, `got ${steps.length}`);
  check('步骤快照已回填', steps.length > 0 && steps.every(s => s.processCodeSnapshot && s.processNameSnapshot),
    JSON.stringify(steps.map(s => s.processCodeSnapshot)));
  check('顺序号 1..3', steps.map(s => s.sequenceNo).join(',') === '1,2,3', JSON.stringify(steps.map(s => s.sequenceNo)));
  check('工位信息已填充', steps[0]?.workstationCode && steps[0]?.workstationName, JSON.stringify(steps[0]));
}

// 7. 简化登录
{
  const r1 = await req('POST', '/auth/login', { username: 'admin', password: '123456' });
  check('POST /auth/login 固定 token', r1.json?.code === 0 && r1.json?.data?.token === 'smartfactory-demo-token',
    JSON.stringify(r1.json));
}

// 8. 参数校验：非法状态 -> 400
{
  const r1 = await req('PUT', `/master/boms/${bomId}/status`, { status: 'FROZEN' });
  check('非法状态 -> 400', r1.json?.code === 400, JSON.stringify(r1.json)?.slice(0, 150));
}

console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail > 0 ? 1 : 0);
