# SmartFactory-AI-MES

轻量制造执行系统（MES）+ AI 工厂知识库。

> 面向离散制造场景的学习/演示项目，第一版以 **AOC 55 英寸 4K 智能电视** 为 Demo 场景，
> 覆盖产品、物料、BOM、工艺路线、生产工单、派工、报工、质检、追溯、物料批次追溯、生产排程、报表中心、生产看板与 AI 应用。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.5.16、MyBatis-Plus 3.5.16、MySQL 8、Lombok、EasyExcel 4.0.3 |
| 前端 | Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、Axios、ECharts |
| 部署 | Docker（开发环境复用本机已有 MySQL 容器） |

## 目录结构

```text
SmartFactory-AI-MES
├── backend/     # Spring Boot 后端（模块化单体，包结构 com.smartfactory.mes）
├── frontend/    # Vue 3 前端
├── sql/         # 建库建表脚本与电视 Demo 种子数据
├── scripts/     # 冒烟测试脚本
└── docs/        # 设计文档与方案（来源：Obsidian 代码与文档规划）
```

## 快速开始

### 环境要求

- JDK 17（本机系统 JAVA_HOME 指向 JDK 8，故用仓库内 `backend/mvn.cmd` 切换）
- Maven 3.8+（本地仓库 `D:\mvn-repository`，见 `backend/.mvn/maven.config`，可自行修改）
- Node.js 18+
- Docker Desktop（提供 MySQL 8 容器，端口 3306）

### 1. 初始化数据库（一次性）

```bash
# 建库 + 专用账号（root 密码按本机容器实际值修改）
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 < sql/00-init.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/01-schema.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/02-seed-master.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/03-schema-week2.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/04-seed-week2.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/05-schema-week3.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/06-seed-week3.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/07-schema-week4.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/08-seed-week4.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/09-schema-week5.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/10-seed-week5.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/11-schema-week6.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/12-seed-week6.sql
```

> 注意：Windows PowerShell 管道会以 GBK 破坏 UTF-8 内容，请在 Git Bash 中执行；
> 00-init 是幂等建库脚本，如需干净重放请先 `DROP DATABASE smartfactory_mes`。

### 2. 启动后端（8080，context-path `/api`）

```bash
cd backend
./mvn.cmd spring-boot:run     # 内部切 JDK 17 + 本地仓库
```

数据源账号 `smartfactory / Smartfactory@123`（见 `backend/src/main/resources/application.yml`）。

AI 功能（知识库问答/异常建议/日报/统一助手）需配置 DeepSeek Key：
复制 `backend/src/main/resources/application-local.yml.example` 为 `application-local.yml` 填入 Key，
或启动时注入环境变量 `DEEPSEEK_API_KEY`（该文件已 gitignore，真实 Key 绝不入库）。
未配置 Key 时 AI 接口自动降级模板回答（`fallback=true`），其余功能不受影响。

### 3. 启动前端（5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173，演示账号（密码为 BCrypt 哈希）：

| 账号 | 密码 | 角色 | 权限 |
|---|---|---|---|
| admin | admin123 | 系统管理员 | 全部菜单与按钮 |
| operator | operator123 | 操作工 | 工单/任务查询 + 派工/开工/暂停/继续 + 报工 + 追溯/看板查询 |
| planning | planning123 | 计划员 | 工单全操作 + 基础资料只读（无报工）+ 追溯/看板查询 |
| qa | qa123 | 质检员 | 质检任务/检验录入/不良/异常全流程 + 追溯/看板查询 |

AI 应用四页（AI 助手/工厂知识库/异常建议助手/生产日报助手）**四角色全员可用**（工人查 SOP 是核心场景）；
差异只在写动作：知识库新建/编辑仅 admin，异常建议保存回写异常单仅 admin + qa。

### 4. 演示路径（第 1-3 周业务闭环 → 第 4 周 AI 应用 → 第 5 周系统集成 → 第 6 周生产深化）

1. **admin** 登录 → 生产工单 → 新建（选 TV-AOC-55U4K-001，自动解析其生效 BOM/工艺路线）
   → 下发 → 自动生成 13 个工序任务（详情抽屉可看追溯时间线）
2. **operator** 登录 → 工序任务 → 派工 → 开工 → 暂停/继续 → 逐道报工（合格/不良数量，
   后道合格不能超过前道）
3. 13 道全部报满 → 工单自动 COMPLETED（完成数量 = 最后一道工序合格数量）→ 报工记录页核对；
   其中 9 个需质检工序各自生成质检任务，最后一道报工完成时按合格数批量生成整机 SN
4. **qa** 登录 → 质检任务 → 开始检验 → 检验录入（合格/不良数 + 不良行子表，可分次录入）
5. 不良记录 → 生成异常单 → 异常管理 → 处理 → 关闭（填处理结论）
6. **追溯查询**：按 SN（出生信息 + 工单时间线）/ 按批次（批次报工列表 + 工单去重）/ 按工单
7. **设备管理**：10 台种子设备状态每 15s 自动漂移，可手动切换
8. **生产看板**：6 KPI + 4 图表（工单进度/工序良率/不良分布/设备状态）10s 自动刷新
9. 权限差异：admin 看到全部按钮，operator 只见任务操作与报工按钮，qa 只见质检/追溯/看板
10. **AI 助手**（重头戏）：一句话问全局——"现在工厂整体情况怎么样"（pro 档综合概况）/
    "软件烧录的SOP流程是什么"（知识库 RAG，带引用）/ 粘贴异常单号 "EXP…怎么处理？"（pro 档
    推理排查建议）/ "生成今天的生产日报"（flash 润色）；意图标签实时展示路由结果，回答可打有用/无用反馈
11. **工厂知识库**：文档列表 + ## 段落详情 + SOP 问答（命中带引用、无命中兜底话术）；admin 可新建/编辑文档
12. **异常建议助手**：下拉选异常单 → 生成处理建议（pro 深度推理约 5~20s）→ 保存回写异常单
    ai_suggestion 并留 AI_SUGGEST 追溯（保存按钮仅 admin/qa 可见）
13. **生产日报助手**：选日期 → 聚合当日产量/良率/工单/异常/设备 → LLM 润色 → 编辑保存（同日幂等覆盖）→ 历史列表
14. **ERP 订单（系统集成）**：planning 登录 → 模拟下单（外部订单 PENDING）→ admin 一键转工单（自动生成生产工单并回填关联）→ 工单按正常流程生产
15. **WMS 库存（系统集成）**：planning 对 ERP 推单工单领料（按 BOM 关键物料自动计算用量）→ 未领料开工被 409 拦截 → 13 道报满后外部订单自动 DONE + 合格品自动成品入库（流水可查）
16. **动态菜单（角色差异）**：admin/planning 侧边栏多出「系统集成」目录（ERP 订单 + WMS 库存），operator/qa 登录看不到——同一套代码按登录人菜单树渲染；刷新不丢、退出换账号不残留旧路由
17. **物料批次追溯**：admin 在追溯查询页「按物料批次」新建关键件批次 → operator 报工弹窗内为关键件
    选批次（下拉带剩余量）→ 按 SN 反查整机用到的关键件批次 / 按批次反查绑定报工 + 涉及工单 + 整机 SN
    （正反向闭环）；漏绑可对报工记录补录（幂等）
18. **生产排程（甘特图）**：planning 建单下发（不派工不报工）→ 生产排程页点「执行排程」→ 甘特图按工位
    展示横道（工单配色图例、同工位串行不重叠、tooltip 完整信息）→ 重跑覆盖幂等；排程起点为当日 08:00，
    下午执行时横道标红（已逾期）属预期
19. **报表中心**：日/周/月三粒度切换 → 汇总卡片（合格/不良/良率/报工数/工单数/统计窗口）+ 明细表
    （日报按工序分组）→ 导出 Excel（汇总 + 明细双 sheet，中文文件名）

### 冒烟测试

```bash
# 后端启动后执行（Node 18+ 内置 fetch，无需安装依赖）
# 183 项断言：第 1/2/3 周回归 + AI 应用 + 第 5 周系统集成（ERP 外单全链/WMS/菜单树角色差异）+ 第 6 周生产深化（物料批次/排程/报表）
node scripts/smoke.mjs

# 冒烟数据一键清理回种子状态（Git Bash）
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < scripts/clean-smoke.sql
```

## 接口清单

统一响应结构 `{code, message, data, requestId}`：0 成功 / 400 参数错误 / 401 未授权 / 403 无权限 / 404 不存在 / 409 业务冲突 / 500 系统错误；context-path `/api`；除登录外全部需要 `Authorization: Bearer <token>`。

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 认证 | POST | `/api/auth/login` | 真实登录，返回 token + 用户信息 + 角色 + 权限集合 |
| 认证 | GET | `/api/auth/users` | 用户下拉（密码不泄露） |
| 认证 | GET | `/api/auth/menus` | 当前用户菜单树（前端动态路由数据源，SUPER_ADMIN 全量） |
| 产品 | GET | `/api/master/products/page` | 分页（keyword/status） |
| 产品 | GET/POST/PUT/DELETE | `/api/master/products/{id}` | 详情/创建/修改/逻辑删除 |
| 产品 | PUT | `/api/master/products/{id}/status` | 启停用（存在生效 BOM/路线时禁停用） |
| 物料 | 同产品结构 | `/api/master/materials/*` | 被 BOM 明细引用禁删 |
| 物料批次 | GET | `/api/production/material-batches/page` | 批次分页（materialId/keyword，含剩余量 remainingQty） |
| 物料批次 | POST | `/api/production/material-batches` | 新建批次（batchNo 自动生成 MB+日期+流水；perm material-batch:create） |
| 工序 | 同产品结构（无状态） | `/api/master/processes/*` | 被路线步骤引用禁删 |
| 工位 | 同产品结构 | `/api/master/workstations/*` | 被路线步骤引用禁删 |
| BOM | GET/POST/PUT/DELETE | `/api/master/boms/{id}` | 头+明细整单提交；仅 DRAFT 可改/删 |
| BOM | PUT | `/api/master/boms/{id}/status` | 状态机 DRAFT→ACTIVE→OBSOLETE；激活自动作废同产品旧版本 |
| 工艺路线 | 同 BOM 结构 | `/api/master/routes/*` | 头+步骤整单提交，sequenceNo 按数组顺序生成 |
| 工单 | GET | `/api/production/work-orders/page` | 分页（keyword/status/时间范围） |
| 工单 | GET/POST/PUT/DELETE | `/api/production/work-orders/{id}` | 详情（含任务列表+报工统计）/创建/编辑草稿/取消 |
| 工单 | POST | `/api/production/work-orders/{id}/release` | 下发 → 按路线生成工序任务（CAS 防重） |
| 工单 | PUT | `/api/production/work-orders/{id}/cancel` | 取消 + 级联取消未完成任务 |
| 任务 | GET | `/api/production/tasks/page` | 分页（workOrderId/status/workstationId） |
| 任务 | GET | `/api/production/tasks/for-work-order/{workOrderId}` | 按工单查任务 |
| 任务 | PUT | `/api/production/tasks/{id}/assign` | 派工（operatorId 必填，工位可选覆盖） |
| 任务 | PUT | `/api/production/tasks/{id}/start`、`/pause`、`/resume` | 开工（级联工单 IN_PROGRESS）/暂停/继续 |
| 报工 | POST | `/api/production/reports` | 报工（数量校验链 + 进度回写 + 追溯记录） |
| 报工 | GET | `/api/production/reports/page` | 分页（workOrderId/operatorId） |
| 报工 | POST | `/api/production/reports/{id}/bind-batch` | 补录关键件批次绑定（body 为裸数组 [{materialId,batchNo}]，幂等重放 200） |
| 追溯 | GET | `/api/production/traces?workOrderId=` | 工单追溯时间线 |
| 追溯 | GET | `/api/production/traces/sn?sn=` | 按 SN 追溯（出生信息 + 时间线，未知 404） |
| 追溯 | GET | `/api/production/traces/batch?batchNo=` | 按批次追溯（报工列表 + 工单去重） |
| 追溯 | GET | `/api/production/traces/batch-sns?batchNo=` | 按物料批次反查（批次主数据 + 绑定报工 + 涉及工单 + 整机 SN） |
| SN | GET | `/api/production/sns/page` | 整机 SN 分页（workOrderId/keyword） |
| 排程 | POST | `/api/production/schedule/run` | 执行排程（优先级→交期排序，同工位串行；重跑覆盖幂等） |
| 排程 | GET | `/api/production/schedule/gantt?date=` | 甘特图数据（工位/计划起止/状态/priority/isOverdue） |
| 报表 | GET | `/api/production/reports-center/summary?type=&date=` | 日/周/月汇总（type=day/week/month，缺省今天；含良率与统计窗口） |
| 报表 | GET | `/api/production/reports-center/export?type=&date=` | Excel 导出（裸文件流不包 ApiResult；双 sheet + UTF-8 文件名） |
| 质检 | GET | `/api/quality/inspection-tasks/page`、`/{id}`、`/{id}/records` | 任务分页/详情/任务全部记录 |
| 质检 | PUT | `/api/quality/inspection-tasks/{id}/start` | 开始检验（CAS PENDING→INSPECTING） |
| 质检 | POST | `/api/quality/inspection-records` | 检验录入（合格/不良数 + 不良行子表，分次累计 CAS） |
| 不良 | GET | `/api/quality/defects/page` | 不良记录分页（workOrderId/defectCode/keyword） |
| 不良 | PUT | `/api/quality/defects/{id}/to-exception` | 生成异常单（已有未关闭异常单 409） |
| 异常 | GET | `/api/quality/exceptions/page` | 异常单分页（workOrderId/status/keyword） |
| 异常 | POST | `/api/quality/exceptions` | 手工创建异常单 |
| 异常 | PUT | `/api/quality/exceptions/{id}/process`、`/{id}/close` | 处理/关闭（close 必填处理结论） |
| 设备 | GET | `/api/master/equipment/page`、`/{id}` | 设备分页/详情 |
| 设备 | POST/PUT | `/api/master/equipment` | 新增/编辑（写权限仅 admin） |
| 设备 | PUT | `/api/master/equipment/{id}/status` | 状态切换（@Scheduled 每 15s 随机漂移） |
| 看板 | GET | `/api/dashboard/summary` | 今日产量/报工/不良/良率/进行中工单/未关闭异常/设备分布 |
| 看板 | GET | `/api/dashboard/work-orders` | 进行中工单进度（progressPercent） |
| 看板 | GET | `/api/dashboard/quality` | 整体良率 + 工序良率 + 不良分布 |
| 看板 | GET | `/api/dashboard/equipment` | 设备列表 + 状态分布 |
| AI 助手 | POST | `/api/ai/chat` | 统一对话入口：意图路由（OVERVIEW/KNOWLEDGE/EXCEPTION/REPORT）→ 分发四类处理 |
| 知识库 | GET | `/api/ai/knowledge/docs/page`、`/{id}` | 文档分页（keyword/docType/status）/详情 |
| 知识库 | POST/PUT | `/api/ai/knowledge/docs` | 新建/编辑文档（仅 admin） |
| 知识库 | POST | `/api/ai/knowledge/ask` | SOP 问答（关键词召回 + 段落切分 + LLM 生成带引用） |
| 知识库 | PUT | `/api/ai/knowledge/qa-records/{id}/feedback` | 回答有用/无用反馈 |
| 异常建议 | POST | `/api/ai/assistant/suggest` | 生成处理建议（pro 档推理 + FAULT_GUIDE 召回） |
| 异常建议 | POST | `/api/ai/assistant/save` | 保存建议回写异常单 + AI_SUGGEST 追溯（admin/qa） |
| 异常建议 | GET | `/api/ai/assistant/suggestion/{exceptionId}` | 回显已保存建议 |
| 日报 | GET | `/api/ai/daily/page` | 日报历史分页 |
| 日报 | POST | `/api/ai/daily/preview` | 聚合当日数据 + LLM 润色生成草稿 |
| 日报 | POST | `/api/ai/daily/save` | 保存（同日幂等覆盖） |
| ERP | GET | `/api/integration/erp/orders/page`、`/{id}` | 外部订单分页/详情 |
| ERP | POST | `/api/integration/erp/orders` | 模拟下单（PENDING，ERP 单号 UNIQUE） |
| ERP | PUT | `/api/integration/erp/orders/{id}/to-work-order` | 一键转工单（CAS 防重，SYNCED + 回填工单 ID） |
| WMS | GET | `/api/integration/wms/inventory/page` | 库存分页（itemType/keyword，物料/成品名称回填） |
| WMS | GET | `/api/integration/wms/transactions/page` | 库存流水分页（workOrderId/itemType/bizType） |
| WMS | POST | `/api/integration/wms/stock-in` | 采购入库（ON DUPLICATE KEY 累加 + 流水） |
| WMS | POST | `/api/integration/wms/pick` | 工单领料（BOM 关键物料 × 计划数，幂等：已足额领用 409） |

## 技术决策记录

1. **主键用自增 Long 而非雪花 ID**：演示项目 ID 小、调试直观，且避免雪花 19 位 Long 在前端 JS 丢失精度。
2. **Long 序列化为字符串**：JacksonConfig 统一处理，前端拿到 `"1"` 而非 `1`，彻底规避精度问题。
3. **编码唯一性靠 Service 校验而非唯一索引**：逻辑删除的行物理保留，唯一索引会卡住编码复用。
4. **快照字段服务端回填**：BOM 明细存 material_code_snapshot 等，物料改名不影响历史 BOM；
   工单/任务同样快照产品与工序（历史单据不可变原则）。
5. **BusinessException 继承 RuntimeException**：保证 `@Transactional` 回滚——头+明细整单事务中途失败
   不会出现"头写入了、明细没写入"的半提交状态。
6. **MyBatis-Plus 版本锁 3.5.16**：3.5.17 将 IService 迁移到 `com.baomidou.mybatisplus.spring.service`，
   与主流文档不兼容，不适合学习项目。
7. **分页拦截器独立依赖**：3.5.9 起 `PaginationInnerInterceptor` 拆到 `mybatis-plus-jsqlparser`，
   漏配则分页参数静默失效。
8. **Maven 用仓库内脚本**：`backend/mvn.cmd` 切 JDK 17 + `backend/.mvn/maven.config` 指本地仓库，
   不改全局 JAVA_HOME（旧项目依赖 JDK 8）。
9. **鉴权 = JWT + 自研拦截器**（jjwt 0.12.6 + BCrypt，不引 Spring Security 全家桶）：学习项目要看清
   每一步；`@RequirePermission` 后端注解 + 前端 v-permission 按钮级权限。
10. **权限每次请求查库**（不塞 JWT claims、无缓存）：权限变更即时生效；生产用 Redis 缓存（面试对比点）。
11. **原子单号生成器**：mes_sequence 表 + `LAST_INSERT_ID` 三步取号，必须同事务（连接级变量），
    格式 `WO202608230001`；UPDATE 行锁串行并发请求。
12. **并发安全靠条件更新 CAS**：下发 `WHERE status='DRAFT'` 防双下发；报工 `WHERE status='RUNNING'
    AND completed_qty+?<=plan_qty` 防超量，一条 UPDATE 完成校验+累加+状态结转。
13. **质检任务生成在报工事务内**：`need_inspection=1 && 任务达 COMPLETED` → 插质检任务 + 写追溯，
    失败随报工整单回滚；工单取消级联取消 PENDING/INSPECTING 质检任务。
14. **SN 批量取号**：`UPDATE mes_sequence SET current_value=LAST_INSERT_ID(current_value+N)` 一次取连续
    区段 [end-count+1, end]（比逐台取号少 N-1 次锁竞争）；守卫"最后一道 COMPLETED 且 good>0"防提前铸号。
15. **质检分次录入 CAS**：`WHERE status='INSPECTING' AND inspected_qty+本次<=plan_qty` +
    `status=IF(达标,'COMPLETED',status)`，与报工同款一条 UPDATE 完成校验+累加+结转。
16. **看板自定义 SQL 显式 `deleted=0`**：MP 逻辑删除只作用于 wrapper，注解 SQL 不带会被删除数据
    "复活"进统计。
17. **设备漂移模拟需 @EnableScheduling**：@Scheduled 注解配齐但启动类缺总开关会静默不触发
    （无报错无日志）；`@ConditionalOnProperty(equipment.simulate.enabled)` 可一键关闭。
18. **看板良率无数据返回 null**：good+defect=0 时良率 null 而非 0，前端显示 '-'——"没数据"与"零"
    业务语义不同。
19. **DeepSeek 双档模型路由**：flash 快档（~0.8s）打高频轻任务（意图识别/SOP 问答/日报润色），
    pro 推理档（~5s）打重任务（异常原因分析/生产概况综合）——模型路由 = 成本/时延/质量的工程权衡。
20. **推理模型 token 预算分档**：v4-pro 的 reasoning 消耗 max_tokens 预算（1500 时 content 为空，
    实测推理 3k + 回答 1.4k），故 `max-tokens-fast=1500` / `max-tokens-pro=8000`；空内容时日志
    带 hasReasoning 提示调参。
21. **意图识别两段式**：规则关键词前置覆盖演示高频问法（确定性、零 token、毫秒级）→ flash LLM
    分类兜底长尾问法 → 再失败降级 KNOWLEDGE——确定性优先的工程实践。
22. **关键词 RAG 管线**（借鉴尚硅谷掌柜问数）：关键词召回文档 → `##` 段落切分命中 → 拼上下文 →
    LLM 生成带引用；规模上来只需替换召回通道（向量库），管线结构不变。
23. **AI 降级兜底永不白屏**：LLM 失败/超时/空内容一律模板回答 + `fallback=true` 前端明示——
    AI 是增强不是依赖；AI Key 只存 gitignored `application-local.yml`，仓库内环境变量占位。
24. **ERP 转工单 CAS 翻转**：`WHERE id=? AND status='PENDING'` 更新 0 行即并发重复转单 → 抛异常
    回滚刚创建的工单（先建工单再翻转状态，失败即全回滚，不留孤儿工单）。
25. **集成钩子 REQUIRES_NEW + 静默降级**：开工校验/完工回传钩子以独立事务执行且异常吞掉只告警——
    "集成失败不阻断生产"；工单是否 ERP 来源统一判 `EXISTS(mes_external_order WHERE work_order_id=?)`，
    手建工单即使手填外部单号也不触发（老冒烟链路零影响）。
26. **完工钩子流水号在主事务预取**：REQUIRES_NEW 子事务内再取 mes_sequence 号会与报工主事务竞争
    序列行锁（实测锁等待超时），故 STK 号在外层先取好传入。
27. **库存并发靠单行原子 SQL**：累加用 `ON DUPLICATE KEY UPDATE qty=qty+VALUES(qty)`，
    扣减用 `WHERE qty>=?` 条件 UPDATE，全程无"先读后写"丢失更新窗口。
28. **前端动态路由 = 后端菜单树驱动**：登录后拉 `/auth/menus`，组件按
    `import.meta.glob` 路径约定反查（/products → views/products/index.vue，新页面零注册）；
    菜单接口失败降级本地静态树（不白屏）；退出/401 时 removeRoute 防换账号残留旧路由。
29. **物料批次双通道绑定**：报工主通道（DTO 可选字段，旧 payload 零影响）+ 补录通道
    （POST bind-batch）共用同一私有校验方法；同 (report,material) 重放幂等、换批 409——
    「报工漏绑后补录」的演示闭环设计。
30. **排程结果直接落任务表**：ALTER 两列 plan_start_time/plan_end_time 而非独立排程表——
    gantt 零 join 直读、重跑 UPDATE 覆盖即幂等、clean-smoke 无新增清理；
    完成/取消任务保留旧值不重算（历史单据不可变原则）。
31. **排程算法纯内存 + 单行 UPDATE**：优先级→交期→id 全局排序，工位分组内串行推进
    cursor（今日 08:00 起），时长 = ceil(标准工时×计划数)——演示规模内存排程简单直观，
    生产可换 APS 引擎不动接口。
32. **甘特图 custom series renderItem**：x 轴 value 毫秒 + y 轴 category 工位，
    api.coord 两端点换算横道；颜色 = 工单 id%调色板，逾期红色加粗；
    跨日任务两天各显示一次、裁切到当日窗口。
33. **EasyExcel 裸文件流不包 ApiResult**：导出接口直写字节流（Content-Disposition
    `filename*=UTF-8''` 中文名）；前端 downloadRequest 裸 axios 实例（仅 token 拦截器）
    绕开解包拦截器——Blob 会被 JSON 拦截器误解析。
34. **报表聚合口径统一 created_at**：mes_work_report 无 report_time 列，与 DashboardMapper
    `DATE(created_at)=CURDATE()` 口径一致；区间左闭右开 `created_at >= start AND < end`。

## 开发进度

> 各周完成详情见 `docs/` 周报：[第 1 周完成报告](docs/week1-report.md) · [第 2 周完成报告](docs/week2-report.md) · [第 3 周完成报告](docs/week3-report.md) · [第 4 周完成报告](docs/week4-report.md) · [第 5 周完成报告](docs/week5-report.md) · [第 6 周完成报告](docs/week6-report.md)
> 另有 [10 步演示脚本](docs/demo-script.md) 与 [简历项目描述](docs/resume.md)。

- [x] 第 1 周：工程骨架 + 基础资料（产品/物料/BOM/工艺路线/工序/工位）+ 电视 Demo 大屏
- [x] 第 2 周：生产执行（工单/下发/工序任务/派工/报工）+ 真实登录权限（JWT/RBAC）
- [x] 第 3 周：质量追溯看板（质检任务/检验录入/不良/异常 + SN/批次追溯 + 设备漂移模拟 + ECharts 大屏）
- [x] 第 4 周：AI 应用与项目包装（DeepSeek 双档接入 + 知识库 RAG + 异常建议 + 生产日报 + 统一 AI 助手）
- [x] 第 5 周：系统集成（ERP 模拟下单一键转工单 / WMS 采购入库与工单领料 / 前端动态路由菜单树驱动）
- [x] 第 6 周：生产深化（物料批次追溯正反闭环 / 生产排程甘特图 / 报表中心三粒度 + Excel 导出）
- [ ] 第 7 周（可选）：AI 回答 SSE 流式 / 向量 RAG 升级
