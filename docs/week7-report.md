# 第 7 周完成报告：AI 进阶（SSE 流式输出 + 向量 RAG 双路召回 + AI 周报）

> 日期：2026-08-23
> 范围：四类 AI 回答 SSE 逐 token 流式（前端打字机 + 停止按钮）+ 知识库向量化入库与双路召回（关键词 + 向量 RRF）+ AI 周报（近两周趋势聚合 + pro 档趋势描述 + 环比）
> 代码：GitHub [anole5/SmartFactory-AI-MES](https://github.com/anole5/SmartFactory-AI-MES)（main 分支，本周 6 条提交，累计 65 条）
> 执行期间逐任务进度见 Obsidian《AI开发实时报告/11-第7周-AI进阶.md》

---

## 一、本周目标

按《02-第5至8周后续规划》原案执行第 7 周（AI 进阶）：

1. **SSE 流式输出**：SseEmitter 中转 DeepSeek stream，四类 AI 回答逐 token 输出；前端聊天页/知识库页打字机渲染 + 停止按钮
2. **向量化入库**：知识库文档切块 embed 入 Qdrant（复用本机 embedding 容器），写路径自动同步 + reindex 重建
3. **双路召回合并**：关键词打分 + 向量相似度 RRF 合并——语义近义问法也能命中（如"烧录失败"召回"烧录不良"文档）
4. **AI 周报/趋势分析**：pro 档跨天聚合良率/产量趋势，含趋势描述与环比
5. **收尾**：冒烟第 19 节扩展（旧 183 断言零改动）+ 文档 + 推送 + 验收

## 二、完成情况

| # | 任务 | 提交 | 状态 |
|---|---|---|---|
| T1 | 基础设施（13/14 SQL 报表类型迁移+历史种子 / EmbeddingClient / QdrantClient 手写 HTTP / DeepSeekClient 流式通道 / aiExecutor 线程池） | `b3ed339` | ✅ |
| T2 | SSE 流式后端（SseEmitterSink + 4 流式端点 + 事件协议 meta→delta*→done + 取消语义 + 异步线程恢复登录上下文） | `a5d64a1` | ✅ |
| T3 | 向量索引 + 双路召回（切块器 400 字/40 重叠 / 写路径自动同步 / reindex / RRF k=60 合并 / 引用改从合并段落派生） | `c96a067` | ✅ |
| T4 | 前端流式（sse.ts 裸 fetch 手写 SSE 解析 / 聊天页打字机+停止按钮+非流式兜底 / 知识库页流式问答） | `6e38565` | ✅ |
| T5 | AI 周报（WeeklyReportMapper 14 天逐日聚合 / 趋势摘要环比 / pro 档生成 / 前端日周 Tab / reportType 隔离） | `a152def` | ✅ |
| T6 | 冒烟第 19 节 + 收尾（201 断言全绿 / clean-smoke 第 9 节注释 / README / 周报 / 推送） | `9c20e00` 附近 | ✅ |

## 三、三大功能架构（面试可讲）

### SSE 流式输出（四类 AI 回答逐 token）

```
POST /ai/{chat, knowledge/ask, assistant/suggest, daily/preview}/stream
  Controller（请求线程）
    ├─ 校验登录/权限（AuthInterceptor 标准 401/403，裸 SseEmitter 是 ApiResult 的文档化例外）
    ├─ 捕获 LoginUser → 返回 SseEmitter(120s)，SseSupport.start 派发 aiExecutor worker 线程
  Worker 线程
    ├─ CurrentUserContext.set(user) ... finally clear()（坑：不恢复则落库 created_by=0）
    ├─ 意图识别（规则前置 + flash 分类，零 token 成本不可省）→ sendMeta{intent, ...}
    ├─ 流式路由：KNOWLEDGE→askStream / OVERVIEW→chatProStream / EXCEPTION→suggestStream / REPORT→previewStream
    │     （DeepSeekClient stream:true + RestClient.exchange 回调内 BufferedReader 逐行读 data: 行）
    │     （过滤 content.isNull()——pro 档推理中间分块 content 为 JSON null，asText() 返回字符串 "null"）
    ├─ sendDelta{content}×N（打字机）
    └─ sendDone{recordId, intent, answer, references, fallback, ...}（契约：answer === 全部 delta 拼接）
  取消语义：客户端断开 → SseEmitterSink.cancelled 置位 → sendDelta 抛 StreamCancelledException
           → 中止 LLM 读取 → 不落库不落日志（正常用户行为）
  前端 sse.ts：裸 fetch + getReader 手写行解析（axios 会缓冲响应体）；abort 静默；
  EOF 无 done → "连接中断"；首事件前失败 → 自动回退非流式接口（后端降级不白屏）
```

### 向量 RAG（关键词 + 向量双路召回）

```
写路径
  create/update 事务内 syncDocVector（DISABLED→deleteByDocId；ENABLED→先删后 embed+upsert）
    └─ 失败只告警不阻断 CRUD（决策：reindex 可修复，文档管理不因向量库宕机瘫痪）
  POST /ai/knowledge/reindex（admin）→ deleteAll + 全量 ENABLED 重建 → {docCount, sectionCount}
    └─ 幂等；唯一"真实金丝雀"（Qdrant/TEI 宕机时只有它报错）
切块：## 段落切分 + 400 字/40 重叠硬切（防 TEI 512 token 上限）
  点 id = UUID.nameUUIDFromBytes("docId#idx")——索引与召回共用切块器，id 天然对齐
  EmbeddingClient：TEI /embed bge-large-zh-v1.5 1024 维 Cosine，batch≤4
  QdrantClient：手写 HTTP 客户端零新依赖（upsert 分批 100 ?wait=true / search limit 8
    score_threshold 0.30 / with_payload）

读路径 recall(question)
  keywordRecall：文档打分 top3 → 切块 → 块内关键词命中打分（确定性、快）
  vectorRecall：embed(question) → search（语义、慢；AiServiceException 自动退化空列表）
  两通道任一为空 → 单通道 top3；都非空 → RRF(k=60) 按 (docId,idx) 对齐排名合并 → top3
  引用/refDocIds 从合并段落派生（向量命中的文档也进引用）
  两通道均空 → fallbackNoHit 模板兜底（原语义）
语义探针（词面零重叠）："程序写不进芯片"→烧录 / "开机后屏幕全暗"→黑屏 /
  "长时间通电测试时机器自己重新启动"→老化 / "左右喇叭都不出声"→功能测试
```

### AI 周报（趋势 + 环比）

```
POST /ai/weekly/preview {endDate}
  WeeklyReportMapper.dailyAgg：mes_work_report 近 14 天逐日 GROUP BY DATE(created_at)
    （显式 deleted=0；窗口 [E-14 00:00, E 00:00) 一次查询，服务层按周切分）
  窗口口径（与 14-seed 注释对齐）：本周 = E-7..E-1，上周 = E-14..E-8
    ——种子不种今天（今天报工留给冒烟），纳入会出现"末一天无报工"干扰行
  buildSummary：7 行逐日（MM-dd：报工/合格/不良/良率）→ 本周合计 → 上周合计
    → 环比（报工数 ±x.x% / 合格数 ±x.x% / 良率 ±x.x 个百分点；"无上期数据"兜底）
  chatPro 生成趋势描述（pro 档推理 + 250 字内）→ fallback 模板周报
POST /ai/weekly/save → 按 (report_date, report_type='WEEK') 幂等 upsert
  类型隔离：日报 save/page 限定 reportType='DAY'（默认），WEEK 记录不混入旧链路
  13-schema：ADD report_type DEFAULT 'DAY' + UNIQUE(report_date, report_type)（去重先行）
种子故事：本周 687/700=98.1% vs 上周 659/700=94.1%（"产量稳定、良率提升"）
  周报不流式（决策：pro 档推理耗时长，前端一次性渲染趋势分析）
```

## 四、验证

| 脚本 | 断言 | 内容 |
|---|---|---|
| verify-t7-1-infra.mjs | 10 | TEI embed 1024 维 / Qdrant 建 collection（不碰 data-agent-*）/ DeepSeek 原始流探测 / 登录 + 旧 ask 回归 |
| verify-t7-2-stream.mjs | 60 | 4 流式端点事件契约（meta→delta*→done、answer===delta 拼接、无 null 分块污染）+ created_by 落库断言 + 401/权限边界 + 旧接口回归 |
| verify-t7-3-vector.mjs | 18 | reindex {4,14} 与 Qdrant points_count 对齐 / 4 语义探针 / 关键词排序保持 / 写路径 upsert+删除 / 幂等 / 403 |
| verify-t7-4-sse-contract.mjs | 21 | sse.ts 同款解析逻辑重放 4 端点契约 + 401 错误体 + 中途 abort 静默（停止按钮语义） |
| verify-t7-5-weekly.mjs | 13 | preview 趋势/环比（98.1%/94.1%）+ 7 行逐日 / save 幂等 / WEEK 与 DAY 隔离 / operator 200 / endDate 缺失 400 |
| smoke.mjs 第 19 节 | +18（共 201） | 流式四端点契约 / reindex + 权限 / 语义召回 + 排序 / 周报预览环比 / 保存类型隔离幂等 / 收尾 reindex 归位 |

全量冒烟：干净重放 00→14 后 **201/201 通过，0 失败**（旧 183 断言零改动，第 19 节仅追加）；
clean-smoke.sql 复核通过（新增第 9 节注释：向量态由冒烟 19.9 收尾 reindex 归位种子）。

## 五、踩坑记录

1. **pro 档流式 delta 输出满屏 "null"**：推理模型中间分块 `delta.content` 是 JSON null，
   `JsonNode.asText()` 返回字符串 "null" 能通过 `isEmpty()` 过滤 → 流式/非流式均显式 `isNull()` 过滤
2. **异步线程落库 created_by=0**：SSE worker 线程无请求 ThreadLocal（AuditMetaObjectHandler 读不到用户）
   → controller 捕获 LoginUser，worker `try{set}finally{clear}`
3. **verify 脚本 Qdrant 数字 filter 查 0 点**：JacksonConfig 把 Long 序列化为字符串，
   测试文档 id 传入 match value 需 `Number()` 转换
4. **sse.ts flush() 事件名被提前重置**：先重置 currentEvent 再判断事件名恒为 message
   → 先 `const eventName = currentEvent` 再重置
5. **Node/undici 中 abort() 会同步抛 AbortError**（浏览器不抛）：停止按钮语义验证脚本需
   try/catch 包裹 abort 调用（sse.ts 在浏览器环境不受影响）
6. **`yield` 是 Java 受限标识符**：不能作方法名（switch 表达式关键字），周报良率格式化方法取名 `yieldOf`

## 六、决策记录（README #35-41）

SSE 用 SseEmitter 而非 WebFlux · 断开连接=取消 · content null 过滤 · Qdrant 手写 HTTP 客户端 ·
RRF(k=60) 双路合并 · 写路径向量同步只告警不阻断 · 周报窗口 D-7..D-1 vs D-14..D-8 +
(report_date, report_type) 唯一键类型隔离

## 七、后续可选（未排期）

- 向量通道混排参数（score_threshold/RRF k）做成可配置实验面板
- 异常建议 FAULT_GUIDE 召回向量化（本周保留关键词）
- 周报图表化（ECharts 趋势折线 + 良率柱线组合）
