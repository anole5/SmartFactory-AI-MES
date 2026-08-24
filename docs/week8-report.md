# 第 8 周完成报告：工程化收尾（单测 + OpenAPI/Actuator + Docker + CI + 演示材料终版）

> 日期：2026-08-25
> 范围：核心 Service 单元测试（Mockito 纯单测 40 断言）+ OpenAPI(springdoc)/Actuator + Docker 多阶段镜像与 docker-compose 一键启动 + GitHub Actions CI（构建+单测+无 AI 冒烟）+ 演示材料终版与 README 终版
> 代码：GitHub [anole5/SmartFactory-AI-MES](https://github.com/anole5/SmartFactory-AI-MES)（main 分支，本周 8 条提交，累计 73 条）
> 执行期间逐任务进度见 Obsidian《AI开发实时报告/13-第8周-工程化收尾.md》

---

## 一、本周目标

按《02-第5至8周后续规划》原案执行第 8 周（工程化收尾）6 项（用户拍板全做，不含 AI 改进 A/B/C/D 与质检口径改造）：

1. **核心 Service 单元测试**：Mockito 纯单测，无 Spring 上下文/无 DB，CI 友好
2. **OpenAPI(springdoc) + Actuator**：在线接口文档 + 健康检查（CI/容器就绪探测的前提）
3. **Docker 镜像**：backend 多阶段（maven 打包 + jre 运行）、frontend（node 构建 + nginx 托管）
4. **docker-compose 一键启动**：MySQL+后端+前端三容器，宿主端口 3307/8082/8090
5. **GitHub Actions CI**：push 触发构建+单测+无 AI 环境冒烟，README 挂绿标
6. **演示材料终版 + 干净重放收尾**：demo-script/resume 终版、冒烟 §20 工程化接口断言、206 全绿重放

## 二、完成情况

| # | 任务 | 提交 | 状态 |
|---|---|---|---|
| T1 | 核心 Service 单元测试（5 测试类 40 断言：KnowledgeChunker/OrderNoGenerator/InspectionRecord/WorkReport/KnowledgeService） | `8664f93` | ✅ |
| T2 | OpenAPI(springdoc 2.8.17) + Actuator（白名单最小暴露 health,info） | `40f74b8` | ✅ |
| T3 | Docker 多阶段镜像（backend/frontend）+ .dockerignore + nginx SSE 反代配置 | `cfc9dfa` | ✅ |
| T4 | docker-compose 一键启动（三容器 healthy、SMOKE_BASE 可覆盖）+ CI 工作流 + 冒烟 SKIP_AI 门控 + CI UTC 主机跨日窗口修复（cnToday 统一 +08） | `cfc9dfa` + `2c48a7d` + `60b0645` | ✅ |
| T5 | 演示材料终版 + README 终版（README/week8-report/demo-script/resume） | 本 commit | ✅ |
| T6 | 冒烟 §20（工程化接口 5 断言）+ 本机 mysql UTC 时区根治（JDBC sessionVariables）+ 干净重放 206 全绿 + 推送终版 | 本 commit | ✅ |

## 三、工程化四大件（面试可讲）

### 单元测试（纯 Mockito，无 Spring 上下文）

```
5 个测试类 / 40 断言 / 秒级跑完（无 DB、无网络、无 Spring 容器）
  KnowledgeChunkerTest      纯静态切块器：空/短文本/多段/超长硬切 400+40 重叠/
                            pointId 确定性（UUID.nameUUIDFromBytes）/payload 五字段
  OrderNoGeneratorTest      mock MesSequenceMapper：WO+yyyyMMdd+%04d 格式 /
                            InOrder 三步取号（insertIgnoreToday→increment→lastInsertId）
  InspectionRecordServiceImplTest  @InjectMocks 4 mock：状态机前置校验（不存在/
                            非 INSPECTING/数量不合/CAS 0 行→BusinessException）/
                            happy path 落 record+追溯 / 2 不良行→2 DEF+2 追溯
  WorkReportServiceImplTest  12 mock 构造注入：报工校验链全分支（数量/前后道合格/
                            CAS 超量）+ 末工序 SN 铸号 + 需质检生成质检任务 + ERP 钩子不触发
  KnowledgeServiceImplTest  手动 new 9 参构造器 + ReflectionTestUtils：
                            scoreDoc/scoreChunk/recall 端到端 RRF 同键合并
  惯例：CurrentUserContext.set(LoginUser(...)) + finally clear（模拟登录态，
     AuditMetaObjectHandler 读不到用户会落 created_by=0）
```

### OpenAPI + Actuator（最小暴露）

```
pom 追加 springdoc-openapi-starter-webmvc-ui 2.8.17 + spring-boot-starter-actuator
WebMvcConfig 白名单仅追加 4 行：/swagger-ui.html /swagger-ui/** /v3/api-docs/** /actuator/**
  暴露面靠 management.endpoints.web.exposure.include=health,info 收窄
verify-t8-openapi 9 断言：health 200 UP / api-docs 200 openapi 3.x 含 /auth/login /
  swagger-ui 302 跟随 / css 200 / 非白名单接口匿名 401（白名单没放多）/ 登录后 200（鉴权链未坏）
```

### 容器化（多阶段 + 退化方案）

```
backend/Dockerfile   maven:3.9-temurin-17 打包（先 rm .mvn/maven.config 规避本机
                     Windows 仓库路径坑）→ eclipse-temurin:17-jre 运行
frontend/Dockerfile  node:22-alpine 构建 → nginx:alpine 托管
                     nginx.conf：history 路由 try_files 回落 + /api 反代原样透传
                     + proxy_buffering off（SSE 流式必须关缓冲）
docker-compose.yml   mysql:8.0（./sql 挂 initdb 首启自动 00→14 + named volume）→
                     backend（host-gateway 复用宿主 qdrant/TEI）→ frontend
                     宿主端口 3307/8082/8090；DEEPSEEK_API_KEY 经 gitignored .env 透传
退化方案（受限网络）  deploy/ 单阶段 Dockerfile COPY 宿主机产物（构建期零网络下载）+
                     docker-compose.override.example.yml 切换
```

### CI（无 AI 环境全链冒烟）

```
.github/workflows/ci.yml（push/PR 触发，concurrency 防堆积）
  job build-test   setup-java17 + setup-node22（均带依赖缓存）→ 后端 mvn package
                   （跑 T1 单测）→ 前端 npm ci + build → upload jar artifact
  job smoke        mysql:8.0 service（LANG=C.UTF-8 + TZ=Asia/Shanghai 坑位免疫）
                   → 循环导入 sql/*.sql（字母序=执行序）→ java -jar 后台 →
                   actuator health 轮询就绪（T2 产物）→ SMOKE_SKIP_AI=1 全链冒烟
  设计核心：DeepSeek Key 绝不进仓库 → CI 无 Key 运行；
           冒烟 SKIP_AI 门控跳 5 项 AI 硬依赖断言（reindex 是唯一会报错的向量端点），
           其余 196 断言全跑（模板降级/关键词召回通道覆盖）
```

## 四、验证

| 脚本 | 断言 | 内容 |
|---|---|---|
| backend `./mvn.cmd test` | 40/40 | 5 测试类全绿（纯 Mockito 无上下文） |
| verify-t8-openapi.mjs | 9 | health/api-docs/swagger-ui 全链 + 白名单最小暴露 + 鉴权链未坏 |
| verify-t8-compose.mjs | 8 | 8090 首页/反代 actuator/反代 api-docs、8082 直连 health、登录+menus、镜像与三容器 healthy |
| smoke.mjs（SMOKE_BASE=8082） | 201/201 | compose 栈全量冒烟：容器内 DeepSeek 出网 + host-gateway 向量 RAG + nginx SSE 反代全链通过 |
| smoke.mjs（SMOKE_SKIP_AI=1 仿真） | 195 过 + 5 跳 | 本地无 Key 仿真 CI：AI 项全部 gate，其余全跑（唯一失败为尚硅谷 mysql UTC 时区窗口偏差，与 AI 无关，非 AI 形态不补 gate） |
| GitHub Actions | 双 job 绿 | build-test（构建+单测）+ smoke（无 AI 冒烟）；CI 首跑红灯定位 smoke 的 today 取 Node 进程时区（UTC 主机跨日错位）→ cnToday() 统一 +08 修复后绿灯 |
| smoke.mjs（干净重放终版） | **206/206** | 201 旧零改动 + §20 工程化 5 新断言全绿（含凌晨窗口今日产量——JDBC sessionVariables 根治本机 mysql UTC 时区） |
| smoke.mjs（SMOKE_SKIP_AI=1 终版） | 201 过 + 5 跳 + 0 败 | CI 模式复验；clean-smoke 回种子（产品 3/物料 20/文档 4/工单 0/SN 0 复核） |

## 五、踩坑记录

1. **Docker Hub 直连 EOF / 国内镜像源"short read"**：大镜像（temurin/maven/node）反复死在最后
   几个 layer → 多镜像源轮换 + 按内容哈希续拉可收敛；拉回后 `docker tag` 回官方名
2. **buildkit 容器内大下载不可靠**：容器内经 host.docker.internal:7897 可到宿主机 Clash，
   但 Clash 对 repo.maven.apache.org 节点握手 reset、npmmirror 经代理也被 reset →
   放弃容器内 maven/npm 大下载，走计划内退化方案：宿主机出产物（本机缓存秒级）+ 单阶段 COPY
3. **MySQL 官方镜像 initdb 中文双重编码乱码**：无 LANG 环境变量 → docker_process_sql 按 latin1
   读 UTF-8 SQL（`ç”µè†`=电视）→ mysql service 加 `LANG: C.UTF-8`；compose 与 CI service 同坑同解
4. **尚硅谷 mysql 容器为 UTC**：应用写 +08 created_at → 00:00–08:00(+08) 窗口内
   `DATE(created_at)=CURDATE()` 型断言（今日产量）失败——历史冒烟全在白天未暴露；
   compose/CI 两边 TZ=Asia/Shanghai 无此问题。**根治**：JDBC URL 加
   `sessionVariables=time_zone='%2B08:00'`（连接级 SET SESSION，不动服务器全局、不碰课程库；
   坑：URL 里 + 必须 %2B 编码，否则解码成空格报 Unknown time zone）——本机任意时刻冒烟全绿
5. **`backend/.mvn/maven.config` 写死 Windows 本地仓库路径**：Dockerfile/CI 构建前必须
   `rm -f .mvn/maven.config`（只删工作区副本，仓库文件不动）
6. **.env / application-local.yml 是 Key 的唯一落点**：两者均 gitignore；.dockerignore 排除
   application-local.yml 防 Key 进镜像；CI 全程无 Key 靠 SMOKE_SKIP_AI 门控

## 六、决策记录（README #42-46）

单元测试纯 Mockito 不启 Spring 上下文 · springdoc/actuator 匿名白名单最小暴露 ·
多阶段 Docker 镜像 + 本地产物退化方案 · MySQL 官方镜像必须 LANG=C.UTF-8 ·
CI 无 AI 环境门控（SMOKE_SKIP_AI + Key 绝不进仓库）

## 七、后续可选（未排期）

- 质检口径说明已按用户拍板补入《技术人员操作熟悉手册》第 5 章（不改代码，仅文档）
- CD 化：镜像推 GHCR + compose 远程部署（学习项目本地演示够用，未做）
- 冒烟提速：单测与冒烟并行 job、前端 e2e（Playwright）
- AI 改进 A/B/C/D（向量通道混排实验面板/FAULT_GUIDE 向量化/周报图表化等）留待后续周
