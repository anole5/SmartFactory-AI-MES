# 第 4 周完成报告：AI 应用与项目包装

> 代码仓库：github.com/anole5/SmartFactory-AI-MES
> 完成日期：2026-08-23（T1-T10 已全部落地）

---

## 一、本周目标回顾

对照《四周开发落地计划》第 4 周"AI 与项目包装"：知识文档管理、SOP 问答、异常建议、生产日报，
另按用户确认新增**统一 AI 助手**（一句话问全局）作为演示核心。全部完成，另有决策表中约定的
ERP/WMS 模拟、动态路由、物料追溯、排程、SSE 流式放第 5 周。

## 二、交付清单

| 模块 | 交付物 |
|---|---|
| 数据 | `sql/07-schema-week4.sql`（知识库文档/问答记录/日报 3 表 + 异常单 ai_suggestion 列）、`sql/08-seed-week4.sql`（AI 菜单 11 条 + 角色授权 33 行 + 4 篇种子 SOP/故障文档） |
| 后端 | `ai` 包：DeepSeekClient（双档）、知识库 RAG 问答、异常建议助手、生产日报、统一助手意图路由；ActionType + AI_SUGGEST |
| 前端 | 4 页：AI 助手（对话窗）/ 工厂知识库 / 异常建议助手 / 生产日报助手；AI 应用菜单目录 |
| 验证 | verify-t2/t3/t4 三个脚本 41 断言全绿；全量冒烟 139/139（124 回归 + 15 AI 断言） |
| 包装 | 本报告、README 更新、演示脚本 `docs/demo-script.md`、简历描述 `docs/resume.md` |

## 三、AI 架构设计（面试可讲）

```
统一入口 POST /api/ai/chat（全员可用）
  │ 意图识别：① 规则关键词前置（确定性、零 token、毫秒级）
  │           ② 未命中 → flash 档 LLM 分类兜底 → ③ 失败降级 KNOWLEDGE
  ├─ OVERVIEW 生产概况 ── 实时数据聚合（当日产量/良率/工单/异常/设备）
  │                        → deepseek-v4-pro 综合分析
  ├─ KNOWLEDGE 知识库 ── 关键词召回文档 → ## 段落切分命中 → flash 生成带引用
  ├─ EXCEPTION 异常建议 ─ EXP 单号识别 → 异常上下文 + FAULT_GUIDE 召回 → pro 推理
  │                        → 保存回写异常单 ai_suggestion + AI_SUGGEST 追溯
  └─ REPORT 生产日报 ── 当日聚合 → flash 润色 → 同日幂等保存
```

**核心工程决策：**

1. **双档模型路由**：flash（deepseek-v4-flash，~0.8s）打高频轻任务（意图识别/SOP 问答/日报润色），
   pro（deepseek-v4-pro，~5s 推理）打重任务（异常原因分析/生产概况综合）——成本/时延/质量的权衡。
2. **RAG 管线借鉴尚硅谷掌柜问数**（LangGraph+DeepSeek+Qdrant/ES）：召回→增强→生成的管线思想完整保留，
   召回通道按用户确认简化为关键词匹配（无向量库），适合中小知识库学习场景。
3. **降级兜底**：LLM 失败/超时/空内容一律模板回答 + `fallback=true` 前端明示——AI 是增强不是依赖，
   演示永不白屏。
4. **Key 安全**：真实 Key 只存 gitignored `application-local.yml`；仓库内 `application.yml`
   用 `${DEEPSEEK_API_KEY:}` 环境变量占位，公开仓库零泄露。
5. **问答留痕**：所有对话落 `mes_ai_qa_record`（intent 列记录路由结果），知识库回答可打有用/无用反馈。

## 四、数据模型

| 表 | 用途 | 关键列 |
|---|---|---|
| mes_knowledge_doc | 知识库文档 | doc_name/doc_type(SOP/QUALITY_STANDARD/EQUIPMENT_MANUAL/FAULT_GUIDE)/keywords(逗号分隔)/content(MEDIUMTEXT，## 段落)/status |
| mes_ai_qa_record | 问答留痕 | question/answer(MEDIUMTEXT)/intent/ref_doc_ids/useful |
| mes_ai_report | 生产日报 | report_date(DATE)/content(MEDIUMTEXT)，同日幂等覆盖 |
| mes_exception_order + ai_suggestion | 异常单 AI 建议列 | 建议助手生成后回写 |

## 五、权限模型

| 权限 | 角色 |
|---|---|
| ai:chat:query / ai:knowledge:query / ai:assistant:query / ai:daily:query | 全员（工人查 SOP 是核心场景） |
| ai:assistant:generate / ai:daily:generate / ai:daily:save | 全员（4031/4041/4042） |
| ai:assistant:save（建议回写异常单） | admin + qa（4032） |
| ai:knowledge:create / update | 仅 admin（4021/4022） |

## 六、验证记录

| 脚本 | 断言 | 结果 |
|---|---|---|
| scripts/verify-t2-ai-knowledge.mjs | 文档分页/详情、ask 命中引用/兜底、反馈、admin 写 200、operator 写 403、真 LLM | 15/15 |
| scripts/verify-t3-ai-assistant-daily.mjs | 建议生成（pro）/保存回写/权限 403/回显、日报预览/幂等覆盖/分页 | 13/13 |
| scripts/verify-t4-ai-chat.mjs | 四意图路由/EXP 单号识别/LLM 分类兜底/recordId 落库/400 校验 | 13/13 |
| scripts/smoke.mjs（第 16 节） | 全量回归 + AI 15 断言 | 139/139 |

## 七、技术决策记录（新增）

- **推理模型 token 分档**：deepseek-v4-pro 是推理模型，reasoning 消耗 max_tokens 预算——
  1500 时 content 为空（实测 reasoning 3k + 回答 1.4k），故 max-tokens-fast=1500 / pro=8000，
  空内容告警日志提示调参。
- **意图识别两段式**：规则关键词覆盖演示高频问法（零成本毫秒级），LLM 分类兜底长尾问法，
  再失败降级知识库（兜底话术不白屏）——确定性优先的工程实践。
- **日报幂等 = 业务幂等**：同一 report_date 保存覆盖而非插入新行，天然支持草稿多次编辑。

## 八、遇到的问题

1. **deepseek-v4-pro 返回空 content**：推理模型 reasoning 吃满预算 → 探针脚本复现定位 →
   分档 max-tokens 解决（见决策记录）。
2. **Comparator API 误用**：`thenComparingInt` 无双参重载，编译期即报错，改手写比较器。
3. **冒烟脚本 req 助手参数错位**：GET 误把 token 传成 body 导致 fetch 抛错，修正参数位后全绿。

## 九、第 5 周计划（溢出项）

ERP/WMS 模拟集成、前端动态路由（菜单表驱动）、物料追溯、生产排程、AI 回答 SSE 流式输出。
