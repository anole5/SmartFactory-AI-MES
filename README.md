# SmartFactory-AI-MES

轻量制造执行系统（MES）+ AI 工厂知识库。

> 面向离散制造场景的学习/演示项目，第一版以 **AOC 55 英寸 4K 智能电视** 为 Demo 场景，
> 覆盖产品、物料、BOM、工艺路线、生产工单、派工、报工、质检、追溯、异常、生产看板与 AI 应用。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.5.16、MyBatis-Plus 3.5.16、MySQL 8、Lombok |
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
```

> 注意：Windows PowerShell 管道会以 GBK 破坏 UTF-8 内容，请在 Git Bash 中执行；
> 00-init 是幂等建库脚本，如需干净重放请先 `DROP DATABASE smartfactory_mes`。

### 2. 启动后端（8080，context-path `/api`）

```bash
cd backend
./mvn.cmd spring-boot:run     # 内部切 JDK 17 + 本地仓库
```

数据源账号 `smartfactory / Smartfactory@123`（见 `backend/src/main/resources/application.yml`）。

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

### 4. 演示路径（第 3 周质量追溯闭环）

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

### 冒烟测试

```bash
# 后端启动后执行（Node 18+ 内置 fetch，无需安装依赖）
# 124 项断言：第 1/2 周回归 + 质量链路（质检/不良/异常）+ SN/批次追溯 + 生产看板 + 权限边界
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
| 产品 | GET | `/api/master/products/page` | 分页（keyword/status） |
| 产品 | GET/POST/PUT/DELETE | `/api/master/products/{id}` | 详情/创建/修改/逻辑删除 |
| 产品 | PUT | `/api/master/products/{id}/status` | 启停用（存在生效 BOM/路线时禁停用） |
| 物料 | 同产品结构 | `/api/master/materials/*` | 被 BOM 明细引用禁删 |
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
| 追溯 | GET | `/api/production/traces?workOrderId=` | 工单追溯时间线 |
| 追溯 | GET | `/api/production/traces/sn?sn=` | 按 SN 追溯（出生信息 + 时间线，未知 404） |
| 追溯 | GET | `/api/production/traces/batch?batchNo=` | 按批次追溯（报工列表 + 工单去重） |
| SN | GET | `/api/production/sns/page` | 整机 SN 分页（workOrderId/keyword） |
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

## 开发进度

> 各周完成详情见 `docs/` 周报：[第 1 周完成报告](docs/week1-report.md) · [第 2 周完成报告](docs/week2-report.md) · [第 3 周完成报告](docs/week3-report.md)

- [x] 第 1 周：工程骨架 + 基础资料（产品/物料/BOM/工艺路线/工序/工位）+ 电视 Demo 大屏
- [x] 第 2 周：生产执行（工单/下发/工序任务/派工/报工）+ 真实登录权限（JWT/RBAC）
- [x] 第 3 周：质量追溯看板（质检任务/检验录入/不良/异常 + SN/批次追溯 + 设备漂移模拟 + ECharts 大屏）
- [ ] 第 4 周：AI 应用与项目包装
