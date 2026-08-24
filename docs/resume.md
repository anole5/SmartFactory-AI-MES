# 简历项目描述（SmartFactory-AI-MES）

## 一句话版

从零到一实现的制造执行系统（MES）全栈项目，覆盖离散制造"工单→派工→报工→质检→追溯→看板"完整链路，
并落地 DeepSeek 大模型驱动的工厂 AI 助手（意图路由 + 关键词/向量双路 RAG + 异常建议 + 生产日报），
工程化闭环完整（单元测试 + 全量冒烟 + Docker Compose 一键启动 + GitHub Actions CI）。

## 项目经历版（STAR 口径）

**SmartFactory-AI-MES —— 工业 SaaS + AI 应用落地（2026.08，个人学习/演示项目）**

以 AOC 55 英寸 4K 智能电视为 Demo 场景，四周交付一个可演示的轻量 MES 系统：

- **S（背景）**：以《MES-工厂信息化系统-工业 SaaS-AI 应用落地》四周计划为路线图，
  目标是从业务域到 AI 应用逐层落地一个"面试可讲"的完整项目。
- **T（任务）**：基础资料（产品/物料/BOM/工艺路线）→ 生产执行（工单/派工/报工）
  → 质量追溯（质检/不良/异常/SN）→ 看板 → AI 应用与包装，四周 40+ 任务全部完成。
- **A（行动）**：
  - 后端 Spring Boot 3 + MyBatis-Plus 模块化单体：JWT + 自研拦截器 RBAC 按钮级权限
    （@RequirePermission + v-permission），原子单号生成器（mes_sequence +
    LAST_INSERT_ID 行锁），CAS 条件更新防并发（防双下发/防超量报工），状态机显式流转，
    追溯动作全留痕（19 种 ActionType）
  - AI 应用接入 DeepSeek 双档模型（flash 快档 + pro 推理档）：意图路由两段式
    （规则关键词前置 + LLM 分类兜底），知识库问答 RAG 管线（关键词召回 → ## 段落切分 →
    LLM 生成带引用，借鉴尚硅谷掌柜问数），异常建议回写异常单并写追溯，
    生产日报数据聚合 + 润色 + 幂等保存；LLM 故障全链路模板降级，演示永不白屏；
    API Key 走 gitignored 本地配置 + 环境变量占位
  - 前端 Vue 3 + TypeScript + Element Plus + ECharts：15+ 业务页 + 4 个 AI 页 +
    电视 Demo 大屏；后端 60+ 接口
  - 工程质量：全量冒烟 201 断言（Node 原生脚本，覆盖主链路/质量链路/AI 场景/权限边界），
    每任务独立验证 + 独立 commit + Obsidian 实时周报；核心 Service 单元测试 40 断言
    （Mockito 纯单测不启 Spring 上下文，CI 秒级跑完）；Docker 多阶段镜像 +
    docker-compose 一键启动（MySQL/后端/前端三容器，中文 initdb 编码坑/Windows 本地仓库
    路径坑/受限网络退化方案逐坑免疫）；GitHub Actions CI 双 job——push 触发构建+单测+
    无 AI 环境冒烟（DeepSeek Key 绝不进仓库，冒烟 SKIP_AI 门控）；springdoc OpenAPI
    在线文档 + Actuator 健康检查（CI/容器就绪探测复用）
- **R（结果）**：四周交付可演示闭环 + 一周工程化收尾，GitHub 开源（anole5/SmartFactory-AI-MES，
  CI 绿灯），形成完整面试叙事：业务设计（数据模型/状态机/快照原则）→ 工程能力
  （并发/事务/权限/追溯）→ AI 落地（RAG/意图路由/降级/模型路由）→ 工程质量
  （单测/冒烟/容器化/CI）四层可讲。

## 面试高频问题与答案要点

1. **为什么知识库召回用关键词 + 向量双路，而不是纯向量？** 关键词通道确定性高、零成本、
   词面命中必中（SOP 场景问法多为专业术语）；向量通道覆盖语义近义问法（"开机后屏幕全暗"
   命中《黑屏故障排查手册》）；两通道 RRF(k=60) 按 (docId,idx) 对齐排名合并取 top3。
   Qdrant/TEI 宕机时向量通道自动退化纯关键词（reindex 是唯一报错的向量端点），
   AI 是增强不是依赖。规模小、双通道已有单测 + 冒烟语义探针覆盖。
2. **LLM 挂了怎么办？** 全链路 try/catch 降级：异常建议按不良代码模板、知识库直出命中
   段落原文、日报直出统计数据，响应里 fallback=true 前端明示"模板回答"——AI 是增强不是依赖。
3. **为什么意图识别用规则 + LLM 两段式？** 演示高频问法（日报/概况/SOP/异常单号）规则
   全覆盖，零 token 成本毫秒级；LLM 分类兜底长尾；再失败降级知识库。确定性优先。
4. **模型为什么分档？** flash 0.8s 打高频轻任务，pro 5s 推理打异常分析/概况综合——
   成本/时延/质量的工程权衡；pro 是推理模型，reasoning 吃 token 预算，故 max-tokens 分档配置。
5. **单元测试怎么做的？** 核心 Service（报工/检验/取号/召回）纯 Mockito 单测：@InjectMocks/
   构造器注入 mock Mapper 与外部客户端，不启 Spring 上下文、不连 DB，CI 秒级跑完；
   校验链全分支（CAS 0 行/前后道数量/状态机非法流转）+ 登录态 ThreadLocal set/finally clear。
   冒烟测试用 Node 原生脚本打真实 HTTP（201 断言），CI 无 AI 环境用 SKIP_AI 门控。
6. **CI 怎么设计的？** push 触发双 job：构建+单测（Maven/Node 依赖缓存）→ 无 AI 环境冒烟
   （MySQL service 容器 + 干净种子 + SMOKE_SKIP_AI）。DeepSeek Key 绝不进仓库；容器化与 CI
   共用同一套坑位免疫（mysql 容器 LANG=C.UTF-8 中文编码、TZ 对齐、maven.config 本机路径隔离）。
