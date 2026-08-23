# SmartFactory-AI-MES 第 2 周完成报告

> 日期：2026-08-22 ~ 2026-08-23
> 范围：生产执行主链路（工单/下发/工序任务/派工/报工/追溯）+ 真实登录权限（RBAC 五表 + JWT）+ 业务单号生成器 + BOM/路线升版
> 代码：GitHub [anole5/SmartFactory-AI-MES](https://github.com/anole5/SmartFactory-AI-MES)（main 分支，11 条提交）

---

## 一、本周目标

按 Obsidian 方案《四周开发落地计划》执行第 2 周：

1. 生产工单表与接口（创建/查询/编辑草稿/下发/取消）
2. 工单下发 → 按工艺路线生成工序任务（事务）
3. 工序任务列表（按工单/状态/工位查询）
4. 派工接口与页面（分配操作员/工位/设备）
5. 开工、暂停、继续
6. 报工接口（校验数量、生成报工记录、回写工单进度、写追溯记录）
7. 工单进度汇总（完成数量 = 最后一道工序合格数量）
8. 真实登录：RBAC 五表 + JWT + 权限拦截 + 前端按钮级权限
9. 业务单号生成器 + 操作人回填 + BOM/路线升版 TODO 补齐
10. 演示数据：55 英寸电视工单"创建 → 下发 → 派工 → 报工"全流程

## 二、完成情况

| # | 任务 | 提交 | 状态 |
|---|---|---|---|
| T1 | 建表脚本（生产执行 5 表 + RBAC 5 表 + 单号序列） | `372faa3` | ✅ |
| T2 | 状态枚举 + 原子单号生成器 | `caf6a93` | ✅ |
| T3 | 真实登录：JWT + 拦截器 + 操作人回填 | `8f3267b` | ✅ |
| T4 | RBAC 权限校验 + 用户/角色/菜单种子 | `b49c3db` | ✅ |
| T5 | 工单 CRUD（草稿创建/编辑/取消） | `6266e05` | ✅ |
| T6 | 工单下发 → 生成工序任务（整单事务 + CAS） | `018f054` | ✅ |
| T7 | 派工/开工/暂停/继续状态流转 | `e991b95` | ✅ |
| T8 | 报工 + 工单进度回写 + 追溯记录 | `d248bda` | ✅ |
| T9 | 前端认证改造（登录/权限指令/字典） | `0b0d3cb` | ✅ |
| T10 | 前端生产三页面（工单/工序任务/报工记录） | `7e449c7` | ✅ |
| T11 | 收尾：BOM 升版 + 全量冒烟 + 文档 | `48148f6` | ✅ |

**验证结果**：

- T3~T8 每个任务独立验证脚本，断言总数 **16 + 29 + 33 + 22 + 30 + 27 = 157 项全部通过**，
  覆盖状态机流转、权限边界（三角色）、校验链 409 负例、追溯链 SQL 复核、失败事务零残留
- T11 改造后全量冒烟 `scripts/smoke.mjs`：**78/78 通过**（真实登录 + 第 1 周断言回归 +
  生产执行全链路 + BOM/路线升版联动），配 `scripts/clean-smoke.sql` 一键回种子状态
- 前端 `npm run build`（vue-tsc 类型检查 + 打包）通过；dev 手测见"九、演示路径"

## 三、已确认的技术决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 鉴权 | JWT + 自研拦截器（jjwt 0.12.6，不引 Spring Security 全家桶） | 学习项目要看清每一步；Spring Security 自动配置黑盒，JWT 不跨微服务无分布式会话诉求 |
| 权限 | 全套 RBAC 五表 + `@RequirePermission` 后端注解 + 前端 v-permission 按钮级 | 动态路由留第 3/4 周（菜单表照建，perm 列就位） |
| 单号 | mes_sequence 序列表 + `LAST_INSERT_ID` 原子自增 | 零额外组件、单号可读可排序；Redis INCR 留作面试对比点 |
| 并发 | 条件更新 CAS（防双下发、报工超量校验） | 一条 UPDATE 同时完成校验+累加+状态结转 |
| 密码 | BCrypt（单引 spring-security-crypto） | 不引整个 Security 避免自动配置干扰 |
| 权限查询 | 每次请求查库，不塞 JWT claims、无缓存 | 权限变更即时生效；生产用 Redis 缓存（注释点明，面试对比点） |
| 第 2 周不做 | ERP/WMS 集成模拟、SN 绑定、质检任务生成与异常单 | 代码留触发点 TODO，第 3 周接 |

## 四、系统架构

### 4.1 后端新增分层（模块化单体）

```text
com.smartfactory.mes
├── common/sequence    # OrderNoGenerator：单号生成器（WO/TASK/RPT/TRC/BOM/RT）
├── auth               # 真实登录：entity/mapper（sys_user）+ JwtUtil + LoginUser
│                      # + CurrentUserContext(ThreadLocal) + AuthInterceptor
│                      # + RequirePermission 注解 + PermissionService
└── production         # 生产执行：entity/enums/mapper/dto/service/controller
    ├── enums          # WorkOrderStatus/TaskStatus/ActionType/OrderPriority
    ├── entity         # MesWorkOrder/MesOperationTask/MesWorkReport/MesTraceRecord/MesSequence
    └── service        # WorkOrderService/OperationTaskService/WorkReportService/TraceService
```

请求链路：`RequestIdFilter → AuthInterceptor（Bearer token → 查用户 → @RequirePermission 校验 → CurrentUserContext）→ Controller → Service`。

### 4.2 前端新增

```text
src
├── directives/permission.ts   # v-permission：无权限直接移除元素（按钮级）
├── stores/auth.ts             # userInfo + hasPerm()（SUPER_ADMIN 绕过）+ 持久化
└── views/
    ├── work-orders/   # 列表（进度条/状态筛选）+ 新建编辑 + 详情抽屉（任务表 + 追溯时间线）
    ├── tasks/         # 任务列表 + 派工弹窗 + 开工/暂停/继续 + 报工弹窗
    └── reports/       # 报工记录只读列表（支持 ?workOrderId= 跳转过滤）
```

### 4.3 数据模型（第 2 周新增 10 张表）

| 表 | 说明 | 关键字段 |
|---|---|---|
| mes_work_order | 生产工单 | work_order_no/external_order_no/product 快照/bom_id/route_id/plan_qty/completed_good_defect_qty/status/priority/计划与实际起止 |
| mes_operation_task | 工序任务 | task_no/work_order_id/process 快照/sequence_no/workstation_id/operator_id/need_inspection/standard_minutes |
| mes_work_report | 报工记录 | report_no/task_id/operator_id/product_batch_no/report_good_defect_qty/起止时间 |
| mes_trace_record | 追溯记录 | trace_no/work_order_id/task_id/action_type/action_time/operator_id/action_detail(JSON) |
| mes_sequence | 单号序列 | seq_type/seq_date/current_value，UNIQUE(seq_type, seq_date, tenant_id) |
| sys_user/sys_role/sys_menu/sys_user_role/sys_role_menu | RBAC 五表 | 菜单三级 M-C-F + perm 权限标识列 |

## 五、核心业务规则实现

1. **原子单号生成器**（`OrderNoGenerator`，可面试讲解）：
   同事务内 ① `INSERT IGNORE` 当日行 → ② `UPDATE ... SET current_value = LAST_INSERT_ID(current_value + 1)`
   → ③ `SELECT LAST_INSERT_ID()` 拼 `前缀+yyyyMMdd+4位流水`。UPDATE 行锁串行并发请求、
   LAST_INSERT_ID 连接级取值不串号；**必须同 @Transactional**（否则每次 mapper 调用换连接取错值）。
   格式如 `WO202608230001`。种子 BOM/RT 已在 04-seed 预留序列起点，不会发重复号。
2. **状态机**：工单 `DRAFT→RELEASED→IN_PROGRESS→COMPLETED→CLOSED`（DRAFT/RELEASED/IN_PROGRESS 可 CANCELLED）；
   任务 `PENDING→ASSIGNED→RUNNING↔PAUSED→COMPLETED`（PENDING/ASSIGNED 可 CANCELLED）。
   显式合法流转、同值幂等、非法 409——与第 1 周 BOM/路线同构。
3. **下发（整单事务 + CAS 防重）**：校验 DRAFT + 产品 ENABLED + BOM/路线 ACTIVE →
   按 route_step 升序生成 13 个任务（工序/工位快照、plan_qty=工单、need_inspection 快照）→
   `UPDATE ... WHERE id=? AND status='DRAFT'` CAS 防并发双下发（0 行→409）→ 写 RELEASE trace。
4. **报工校验链（核心事务，T8）**：① 任务 RUNNING ② report_qty = good + defect 且 ≥1
   ③ **CAS 累加一条 UPDATE**：`completed_qty = completed_qty + ?` 且
   `WHERE status='RUNNING' AND completed_qty + ? <= plan_qty`，
   `status = IF(completed_qty >= plan_qty, 'COMPLETED', status)`（MySQL UPDATE 赋值自左向右，
   IF 条件读到的 completed_qty 已是累加后的新值）④ 后道累计合格 ≤ 前道累计合格（首道跳过）
   ⑤ 插 mes_work_report + 写 REPORT trace ⑥ need_inspection=1 留第 3 周质检触发点 TODO
   ⑦ 最后一道回写工单 completed/good/defect，最后一道 COMPLETED → 工单 COMPLETED + actual_end_time。
   失败任一步 BusinessException 整单回滚——验证过"失败报工零残留"。
5. **RBAC 权限校验**：`@RequirePermission` 先方法后类；登录返回 roles/permissions；
   SUPER_ADMIN 角色前端绕过检查；每次请求查库（权限变更即时生效）。
6. **BOM/路线升版（T11）**：changeStatus 激活新版本时
   `UPDATE ... SET status='OBSOLETE' WHERE product_id=? AND status='ACTIVE' AND id<>?`——
   同产品任意时刻只有一个生效版本，与激活同事务。
7. **操作人回填**：AuditMetaObjectHandler 从 CurrentUserContext（ThreadLocal）取当前用户，
   拦截器 afterCompletion 必须 clear（防线程池复用串号）。
8. **工单取消级联**：DRAFT/RELEASED/IN_PROGRESS → CANCELLED，未完成任务（PENDING/ASSIGNED）一并 CANCELLED。

## 六、接口清单

统一响应 `{code, message, data, requestId}`：0 成功 / 400 参数错误 / 401 未授权 / 403 无权限 / 404 不存在 / 409 业务冲突 / 500 系统错误；context-path `/api`；除登录外全部需要 `Authorization: Bearer <token>`。

| 模块 | 端点 | 说明 |
|---|---|---|
| 认证 | `POST /api/auth/login` | 真实登录（admin/admin123、operator/operator123、planning/planning123），返回 token + 用户信息 + 角色 + 权限集合 |
| 认证 | `GET /api/auth/users` | 用户下拉（密码 @JsonIgnore 不泄露） |
| 工单 | `GET /api/production/work-orders/page` | 分页（keyword/status/时间范围） |
| 工单 | `GET/POST/PUT/DELETE /api/production/work-orders/{id}` | 详情（含任务列表+报工统计）/创建/编辑草稿/取消 |
| 工单 | `POST /api/production/work-orders/{id}/release` | 下发 → 按路线生成工序任务 |
| 工单 | `PUT /api/production/work-orders/{id}/cancel` | 取消 + 级联取消未完成任务 |
| 任务 | `GET /api/production/tasks/page` | 分页（workOrderId/status/workstationId） |
| 任务 | `GET /api/production/tasks/for-work-order/{workOrderId}` | 按工单查任务 |
| 任务 | `PUT /api/production/tasks/{id}/assign` | 派工（operatorId 必填，工位/设备可选覆盖） |
| 任务 | `PUT /api/production/tasks/{id}/start`、`/pause`、`/resume` | 开工（级联工单 IN_PROGRESS）/暂停/继续 |
| 报工 | `POST /api/production/reports` | 报工（校验链见上） |
| 报工 | `GET /api/production/reports/page` | 分页（workOrderId/operatorId） |
| 追溯 | `GET /api/production/traces?workOrderId=` | 工单追溯时间线 |

## 七、技术难点与踩坑记录

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| 1 | 登录失败返回 HTTP 409 | `GlobalExceptionHandler` 的 `@ResponseStatus(CONFLICT)` 硬编码，业务码 401 也对齐 409 | `handleBusinessException` 改返回 `ResponseEntity`，`HttpStatus.resolve(业务码)` 动态对齐（401/403/409 各归其位） |
| 2 | 临时程序跑 BCrypt 报 NoClassDefFoundError | Git Bash 下 `/d/...` 路径 classpath 不识别；spring-jcl 版本错 | 用 Windows 风格 `D:/...`；核对依赖版本 6.2.19 |
| 3 | sed 批量加权限注解两次失配 | 凭记忆写模式，javadoc/注解顺序与假设不符 | 先 grep 实际文本再写模式；规范 javadoc 在上、注解在下 |
| 4 | 权限集合断言 42 错写 44 | 44 个菜单中 2 个目录无 perm 标识 | 断言前先推演期望值来源 |
| 5 | 日期 JSON 反序列化 500 | 脚本发 ISO 格式（带 T），项目约定 `yyyy-MM-dd HH:mm:ss`（空格） | 脚本对齐约定；顺手补 400 处理器（JSON 解析失败归客户端错误） |
| 6 | 产品默认 DISABLED 踩空负例 | 01-schema 约定 status 默认 'DISABLED' | 新建临时产品先调 status 接口启用 |
| 7 | 取消端点 POST 调用 500 | 端点是 PUT，脚本误用 POST → 方法不支持落兜底 500 | 脚本改正 + GlobalExceptionHandler 补 405 处理器 |
| 8 | T7 脚本 13 项假失败 | 无 body 的 PUT 写成 `put(path, token)`，token 落进 body 位、Authorization 头缺失 | 无 body 也传 `{}` 占位；对照"同类调用谁成谁败"二分定位 |
| 9 | CAS 更新编译报 int 转 boolean | Mapper 接口 update 返回 int 影响行数，ServiceImpl 同名方法返回 boolean | `int updated = mapper.update(...)` + `if (updated == 0)` |
| 10 | 冒烟脚本下发 405 | 全量冒烟按计划文本误写 PUT，实际 T6 端点是 POST（`POST /{id}/release`） | 写冒烟前对照 Controller 实际注解，不凭计划记忆 |

## 八、面试考点提炼（本周代码可直接讲的点）

1. **JWT + 自研拦截器 vs Spring Security**：学习项目要看清每一步（token 解析、401/403 直写、ThreadLocal 清理）；
   生产项目权衡：Security 全家桶功能全但黑盒、自定义 Filter 链自由但责任自担。
2. **权限不塞 JWT**：每次请求查库，权限变更即时生效、无踢人诉求；生产用 Redis 缓存 + 版本号失效——
   两种方案的取舍本身就是考点。
3. **LAST_INSERT_ID 连接级陷阱**：单号生成器必须同事务，否则换连接取错值——MySQL 会话变量的经典坑。
4. **CAS 一条 UPDATE 完成校验+累加+结转**：`WHERE status='RUNNING' AND completed_qty+?<=plan_qty` 防超量、
   `IF()` 表达式内引用累加后的新值（UPDATE 赋值自左向右）——并发安全的报工核心。
5. **报工前道校验的读后写窗口**：生产用 FOR UPDATE/乐观锁，学习项目注释点明取舍。
6. **失败事务零残留**：BusinessException(RuntimeException) 保证回滚，验证时用 SQL 复核"失败尝试不留行"。
7. **BOM 升版联动**：激活新版本自动作废旧版本，一条条件 UPDATE 与激活同事务——版本管理的一致性边界。
8. **快照字段**：工单存产品快照、任务存工序/工位快照——主数据后续改名不影响历史单据。
9. **ThreadLocal 审计回填**：createdBy/updatedBy 从 CurrentUserContext 取，afterCompletion 必须 clear
   （线程池复用串号——经典内存泄漏/串数据问题）。
10. **405/400 边界干净**：方法不支持、JSON 解析失败都是调用方问题（4xx），不该落 500。

## 九、第 3 周计划（衔接）

- 质量模块：质检任务生成（报工 need_inspection 触发点已留 TODO）、异常单
- SN 绑定与追溯升级（报工 product_batch_no → SN 明细）
- 生产看板与统计（工单进度、报工趋势、大屏升级）
- ERP/WMS 模拟集成（external_order_no 已留字段）
- 前端动态路由（菜单表 perm 列就位）
