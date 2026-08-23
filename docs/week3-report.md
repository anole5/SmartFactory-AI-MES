# SmartFactory-AI-MES 第 3 周完成报告

> 日期：2026-08-23
> 范围：质量管理（质检任务/检验录入/不良/异常）+ 追溯查询（SN/批次）+ 整机 SN 明细 + 设备主数据与状态漂移模拟 + 生产看板大屏
> 代码：GitHub [anole5/SmartFactory-AI-MES](https://github.com/anole5/SmartFactory-AI-MES)（main 分支，本周 13 条提交，累计 33 条）

---

## 一、本周目标

按 Obsidian 方案《四周开发落地计划》执行第 3 周，另加用户确认的 SN 明细：

1. 质检任务生成（需要质检的工序报工后自动生成）
2. 质检记录接口和页面（可录入合格、不合格、检验说明）
3. 不良记录（不合格时生成不良记录）
4. 异常管理（可创建、处理、关闭异常）
5. 追溯记录服务（工单、报工、质检、异常写追溯）+ 追溯查询页面（按 SN / 批次 / 工单展示时间线）
6. 生产看板（产量、进度、良率、异常、设备状态）
7. 设备状态模拟（运行、停机、维护状态漂移）
8. 整机 SN 明细（最后一道报工完成时按合格数量批量生成，按 SN 追溯）

## 二、完成情况

| # | 任务 | 提交 | 状态 |
|---|---|---|---|
| T1 | 建表 + 种子（质量 4 表 + SN + 设备 + 质检角色/菜单） | `9f3a4e8` | ✅ |
| T2 | 枚举 + 单号扩展（SN 批量取号）+ 实体/Mapper | `e0db58e` | ✅ |
| T3 | 报工接入质检任务 + SN 生成 + 取消级联 | `eb08a4b` | ✅ |
| T4 | 质检任务/检验录入/不良记录 | `5458dbd` | ✅ |
| T5 | 异常单创建/处理/关闭 | `207595d` | ✅ |
| T6 | 追溯查询 + SN 分页 | `66fee89` | ✅ |
| T7 | 设备管理 + 状态漂移模拟 | `1ff7aab` | ✅ |
| T8 | 生产看板聚合接口 | `73b91ca` | ✅ |
| T9 | 种子 RBAC 边界验证 | `500affc` | ✅ |
| T10 | 前端质量三页 | `b1037c5` | ✅ |
| T11 | 前端设备 + 追溯页 | `33ea3be` | ✅ |
| T12 | 前端生产看板（ECharts） | `fd1e156` | ✅ |
| T13 | 收尾：冒烟 + 清理 + 文档 | 本提交 | ✅ |

**验证结果**：

- T1~T9 每个任务独立验证脚本，断言总数 **24 表 + 14 + 27 + 30 + 23 + 17 + 17 + 13 = 141 项全部通过**，
  覆盖质检/异常状态机、分次录入 CAS、SN 批量铸号与守卫、批次追溯去重、漂移集合断言、RBAC 权限边界（qa 9 项质检权限）
- T13 干净重放 00→06 后全量冒烟 `scripts/smoke.mjs`：**124/124 通过**
  （第 1/2 周回归 + 质量链路：IQC 检 10 良、AGING 检 9 良 1 不良 → 异常单全流转、
  追溯 52→58 精确计数、SN 分页 10 条、批次追溯、看板四接口、qa 权限边界）
- `scripts/clean-smoke.sql` 升级：事务类数据整表清空 + 种子设备复位 RUNNING + 第 3 周 5 种单号前缀重置，
  实测一键回到干净种子状态
- 前端 `npm run build`（vue-tsc + vite）通过 ×3（T10/T11/T12）；dev 手测见"九、演示路径"

## 三、已确认的技术决策

| 决策 | 选择 | 理由 |
|---|---|---|
| SN 明细 | 做：最后一道工序报工完成时按合格数批量生成（SN+日期+连续流水，批量取号） | 按 SN 追溯是 MES 追溯闭环的终点 |
| ERP/WMS 集成 | 第 3 周不做（external_order_no 保持手填） | 优先级让位质量链路与看板 |
| 前端动态路由 | 第 3 周不做（静态菜单继续，菜单表 perm 列就位） | 功能收益低、风险高，留第 4 周 |
| 设备 | 新建设备主数据表 + @Scheduled 状态漂移模拟 | 看板"设备状态分布"需要动态数据 |
| 质检触发点 | 报工事务内（need_inspection=1 且任务报工达 COMPLETED） | 失败随报工整单回滚，不留半成品 |
| 质检录入 | 允许分次录入，CAS 累计至 plan_qty | 真实质检场景一台一台检，不可能一次录完 |
| 不良→异常 | 手动"生成异常单"（source=DEFECT），同不良已有未关闭异常单则 409 | 防重复开单 |
| 看板包 | 独立 com.smartfactory.mes.dashboard 包（跨模块只走 Service 接口） | 对齐启动类模块划分 javadoc，保持模块化单体 |
| 设备写权限 | 仅 admin（种子未给其他角色设备写权限） | 设备主数据是基础资料，归属 master 管理线 |

## 四、系统架构

### 4.1 后端新增分层（模块化单体）

```text
com.smartfactory.mes
├── quality                        # 质量管理（第 3 周新增模块）
│   ├── enums                      # InspectionTaskStatus/ExceptionStatus/ExceptionSourceType
│   ├── entity/mapper              # MesInspectionTask/MesInspectionRecord/MesDefectRecord/MesExceptionOrder
│   └── service                    # InspectionTaskService/InspectionRecordService/DefectService/ExceptionService
├── dashboard                      # 生产看板（第 3 周新增模块，聚合查询，不落新表）
│   ├── mapper                     # DashboardMapper：8 条注解 SQL（全部显式 deleted=0）
│   ├── service                    # DashboardServiceImpl：4 个聚合方法
│   └── controller                 # GET /dashboard/summary|work-orders|quality|equipment
├── production                     # 扩展：MesProductSn + SnService（分页/批量铸号）
│                                  # TraceService 扩展：snTrace/batchTrace
│                                  # WorkReportServiceImpl：报工事务接入质检任务生成 + SN 铸号
├── master                         # 扩展：MesEquipment + EquipmentService（CRUD/状态切换）
│   ├── enums                      # EquipmentStatus（RUNNING/IDLE/STOPPED/MAINTENANCE）
│   └── simulator                  # EquipmentSimulator：@Scheduled 每 15s 随机 1-2 台漂移
└── common/sequence                # OrderNoGenerator：+4 方法，+nextSnBatch（区间批量取号）
```

### 4.2 前端新增

```text
src
├── constants/dict.ts              # +INSPECTION_TASK_STATUS/EXCEPTION_STATUS/EXCEPTION_SOURCE_TYPE/
│                                  #  EQUIPMENT_STATUS/DEFECT_CODES；ACTION_TYPE +6；STATUS_TAG_TYPE 补全
├── api/                           # +inspectionTask/inspectionRecord/defect/exception/
│                                  #  equipment/sn/dashboard 七组接口；traceApi 扩展 bySn/byBatch
└── views/
    ├── inspection-tasks/          # 质检任务列表 + 开始检验 + 检验录入弹窗（不良行子表，合计校验）
    ├── defects/                   # 不良记录列表 + 生成异常单（确认框）
    ├── exceptions/                # 异常列表 + 新建/处理/关闭（关闭必填处理结论）
    ├── equipment/                 # 设备 CRUD + 状态切换弹窗（四状态单选，无删除端点故无删除按钮）
    ├── traces/                    # SN/批次/工单三入口 + 时间线抽屉 + SN 分页列表（点击行直达追溯）
    └── dashboard/                 # 暗色大屏：6 KPI + 4 ECharts 图表 + 10s 轮询 + resize + 卸载清理
```

### 4.3 数据模型（第 3 周新增 6 张表）

| 表 | 说明 | 关键字段 |
|---|---|---|
| mes_inspection_task | 质检任务 | inspection_task_no(INP)/work_order_id/operation_task_id/工序·工位快照/plan_qty(=触发任务累计完成数)/inspected_good_defect_qty/status(PENDING→INSPECTING→COMPLETED,+CANCELLED 级联)/inspector_id/start·end_time |
| mes_inspection_record | 质检记录 | inspection_record_no(INS)/inspection_task_id/good_defect_qty/inspect_time/inspector_id（允许分次录入，只增不改） |
| mes_defect_record | 不良记录 | defect_no(DEF)/inspection_record_id/inspection_task_id/work_order_id/defect_code（前端字典 7 种）/defect_qty |
| mes_exception_order | 异常单 | exception_no(EXP)/source_type(DEFECT/MANUAL)/defect_record_id(可空)/work·task·inspection id(可空)/defect_code/description/status(OPEN→PROCESSING→CLOSED)/handler_id/resolve_remark/resolved_at |
| mes_product_sn | 成品 SN | sn(SN+yyyyMMdd+4位流水)/work_order_id/product 快照/report_id(出生报工) |
| mes_equipment | 设备 | equipment_code/name/model/workstation_id/status(RUNNING/IDLE/STOPPED/MAINTENANCE)/remark |

种子：质检角色 INSPECTOR + 用户 qa/qa123（BCrypt 现算）、菜单 6 页 9 按钮 + 角色授权、设备 10 台、5 种单号前缀预置。

## 五、核心业务规则实现

1. **质检任务生成（报工事务内）**：报工事务第 ⑥ 步——`need_inspection=1 && 任务达 COMPLETED`
   → 插质检任务（plan_qty=工序累计完成数、工序/工位快照、PENDING）+ 写 INSPECT_TASK 追溯。
   失败随报工整单回滚，验证过"失败报工零残留"延伸到质检任务。
2. **SN 批量铸号（报工事务内）**：最后一道工序任务 COMPLETED 且 good_qty>0 时（守卫防部分报工提前铸号），
   `nextSnBatch(goodQty)` 一次 `UPDATE mes_sequence SET current_value=LAST_INSERT_ID(current_value+批量数)`
   取连续区段 [end-count+1, end]，批量插 mes_product_sn——比逐台取号少 N-1 次行锁竞争，
   与单号生成器同 @Transactional（连接级 LAST_INSERT_ID 陷阱同第 2 周）。
3. **质检分次录入 CAS（核心事务）**：校验链——任务 INSPECTING → good+defect≥1 →
   不良行数量合计=defectQty → **一条 UPDATE 完成校验+累加+结转**：
   `WHERE status='INSPECTING' AND inspected_qty+本次<=plan_qty` + `status=IF(达标,'COMPLETED',status)`
   → 插 INS 记录 + INSPECT 追溯 → 逐不良行插 DEF 记录 + DEFECT 追溯。与报工同款技巧。
4. **异常状态机**：OPEN→PROCESSING→CLOSED，显式流转/同值幂等/非法 409；
   close 必填处理结论（@NotBlank→400）；process 回填 handler=当前用户；CLOSED 回填 resolved_at。
   不良"生成异常单"时同不良已有未关闭异常单则 409（防重复开单）。各态写 EXCEPTION_* 追溯
   （异常单无工单时不写 trace——mes_trace_record.work_order_id NOT NULL 约束）。
5. **追溯查询扩展**：按 SN（SN 出生信息 + 工单摘要 + 全时间线，未知 SN 404）；
   按批次号（复用 WorkReportService.toVOs 回填工单号/工序/操作人 + 涉及工单 selectBatchIds 去重）；
   SN 分页按工单/关键字过滤。TraceController 类级 `production:trace:query`。
6. **设备状态漂移模拟**：`@ConditionalOnProperty(equipment.simulate.enabled 默认 true)` +
   `@Scheduled(fixedDelayString="${equipment.simulate.interval-ms:15000}")` 每轮随机 1-2 台漂移到随机状态。
   **启动类必须加 @EnableScheduling**——第 3 周最高概率坑（全工程此前没有，漏加则定时器静默不触发）。
   手动切换走 `PUT /master/equipment/{id}/status`（EnumUtils.parse 非法值 400，与 Material 同款约定）。
7. **看板聚合 SQL**：8 条注解 SQL 全部显式 `deleted = 0`——MP 逻辑删除只作用于 wrapper，
   自定义 SQL 不吃自动过滤。今日良率/工序良率 = good/(good+defect)，无数据返回 null（前端显示 '-'）；
   工序良率按 process_name_snapshot 分组且仅统计 COMPLETED 任务。
8. **RBAC 边界**：qa（INSPECTOR）9 项质检权限 + 追溯/看板查询，无工单/设备写权限；
   operator/planning 有追溯/看板查询、无任何 quality:*；设备写权限仅 admin。

## 六、接口清单

统一响应 `{code, message, data, requestId}`；context-path `/api`；除登录外全部需要 `Authorization: Bearer <token>`。

| 模块 | 端点 | 说明 |
|---|---|---|
| 认证 | `POST /api/auth/login` | 新增账号 qa/qa123（INSPECTOR） |
| 质检 | `GET /api/quality/inspection-tasks/page` | 分页（workOrderId/status/keyword） |
| 质检 | `GET /api/quality/inspection-tasks/{id}` | 详情 |
| 质检 | `GET /api/quality/inspection-tasks/{id}/records` | 任务的全部质检记录 |
| 质检 | `PUT /api/quality/inspection-tasks/{id}/start` | 开始检验（CAS PENDING→INSPECTING，回填质检员） |
| 质检 | `POST /api/quality/inspection-records` | 检验录入（合格/不良数 + 不良行子表，分次累计） |
| 不良 | `GET /api/quality/defects/page` | 分页（workOrderId/defectCode/keyword） |
| 不良 | `PUT /api/quality/defects/{id}/to-exception` | 生成异常单（防重复 409） |
| 异常 | `GET /api/quality/exceptions/page` | 分页（workOrderId/status/keyword） |
| 异常 | `POST /api/quality/exceptions` | 手工创建（MANUAL） |
| 异常 | `PUT /api/quality/exceptions/{id}/process`、`/{id}/close` | 处理/关闭（close 必填处理结论） |
| SN | `GET /api/production/sns/page` | 分页（workOrderId/keyword） |
| 追溯 | `GET /api/production/traces?workOrderId=` | 工单追溯时间线 |
| 追溯 | `GET /api/production/traces/sn?sn=` | 按 SN 追溯（出生信息+时间线，未知 404） |
| 追溯 | `GET /api/production/traces/batch?batchNo=` | 按批次追溯（报工列表+工单去重） |
| 设备 | `GET /api/master/equipment/page`、`/{id}` | 分页（keyword/workstationId/status）/详情（无权限注解，master 风格） |
| 设备 | `POST/PUT /api/master/equipment`、`PUT /{id}/status` | 新增/编辑/状态切换（写权限仅 admin） |
| 看板 | `GET /api/dashboard/summary` | 今日产量/报工数/不良/良率/进行中工单/未关闭异常/设备分布 |
| 看板 | `GET /api/dashboard/work-orders` | 进行中工单进度（含 progressPercent） |
| 看板 | `GET /api/dashboard/quality` | 整体良率 + 工序良率 + 不良分布 |
| 看板 | `GET /api/dashboard/equipment` | 设备列表 + 状态分布 |

## 七、技术难点与踩坑记录

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| 1 | 一次性 BCrypt 程序 javac 报"不可映射字符" | Windows javac 默认按 GBK 读 UTF-8 源码 | `javac -encoding UTF-8` |
| 2 | shell 传 `$2a$10$...` 哈希被篡改 | Git Bash 对 `$` 做变量展开，哈希到手已损坏 | 校验逻辑内联进 Java 代码，不走 shell 参数 |
| 3 | 取消端点 405 | cancel 是 PUT（与 release 是 POST 同类坑，第 2 次踩） | 写验证脚本前对照 Controller 实际注解 |
| 4 | CANCEL 追溯明细 cancelledTaskCount 断言失败 | Long 被全局 Jackson Long→String 序列化 | 断言用 String(...) 比较（与 PageResult.total 同款约定） |
| 5 | `Map.of()` 空表 get(null) 抛 NPE | 不可变 Map 规范禁止 null 键查询 | 空集短路为 `new HashMap<>()` |
| 6 | `selectBatchIds(空集)` 生成非法 `IN ()` SQL | MP 3.5.16 对空集合不短路（wrapper .in 是安全的） | 批量回填前显式 isEmpty() 短路 |
| 7 | 设备状态切非法值预期 409 实际 400 | EnumUtils.parse 抛 PARAM_ERROR(400)，与 Material 同款约定 | 写验证前先看公共工具的异常语义 |
| 8 | T7 验证脚本首跑后重跑 500 | 首跑创建的 EQ-T7-TEST 残留撞唯一约束，eqId=null 拼进路径 | 复用模式（keyword 找到已存在则复用），断言改集合成员 |
| 9 | 漂移日志 grep 0 命中 | Windows 控制台中文日志是 GBK 编码 | 用 ASCII 标记 `' -> '` grep 验证 |
| 10 | 检验录入弹窗模板报 TS18048 | 后端 DTO 可选数组字段在模板中 splice/push 被判 possibly undefined | 本地 `interface RecordForm extends InspectionRecordSave { defectItems: DefectItem[] }` 桥接 |
| 11 | ECharts 表单器回调 TS7006 隐式 any | option 带条件展开后 setOption 上下文类型推断丢失 | 表单器参数手写最小结构类型 `{ dataIndex: number }` |

## 八、面试考点提炼（本周代码可直接讲的点）

1. **质检任务生成在报工事务内**：半成品质检任务不可接受，BusinessException(RuntimeException) 保整单回滚——
   "事务边界画在哪"是 MES 设计题的经典考法。
2. **SN 批量取号**：`UPDATE ... SET current_value=LAST_INSERT_ID(current_value+N)` 一次取连续区段，
   vs 逐台取号 N 次行锁竞争——顺序号发放的并发设计题；守卫"任务 COMPLETED 且 good>0"防部分报工提前铸号。
3. **分次录入 CAS**：与报工同款一条 UPDATE 完成"校验+累加+结转"，`IF()` 表达式读累加后的新值
   （UPDATE 赋值自左向右）——并发安全的质检累计。
4. **逻辑删除 vs 自定义 SQL**：MP 逻辑删除只作用于 wrapper，注解 SQL 必须显式 `deleted=0`，
   否则被逻辑删除的行会"复活"在看板统计里——用了逻辑删除的团队几乎必踩的坑。
5. **@EnableScheduling 静默失败**：定时器注解配齐但启动类没开总开关，无报错、无日志、功能"消失"——
   "配置开关的开关"类问题排查思路。
6. **状态漂移模拟的意义**：看板/大屏演示需要动态数据，用 @Scheduled 模拟真实设备心跳上送；
   生产场景换成 MQTT/OPC-UA 采集，接口层不变——模拟与真实的边界设计。
7. **异常单可空工单**：mes_trace_record.work_order_id NOT NULL → 无工单异常不写 trace，写与不写的边界
   由数据约束驱动——追溯链完整性 vs 灵活性权衡。
8. **跨模块只走 Service 接口**：quality/dashboard/production 之间的依赖（报工→质检任务、批次追溯→报工列表、
   取消级联）全部接口化，模块化单体不演化成大泥球——"单体也要模块化"的工程素养。
9. **大屏轮询三件套**：setInterval 10s + window resize 重绘 + onUnmounted 清理定时器与 ECharts 实例——
   前端资源泄漏的三个经典入口；生产可换 WebSocket 推送（接口形状已分离）。
10. **看板 null 语义**：无数据时良率返回 null 而非 0——"没数据"和"零"在业务上不是一回事，
   前端 '-' 展示，避免 0% 良率的误报警。

## 九、演示路径（dev 手测剧本）

环境：后端 `cd backend && ./mvn.cmd spring-boot:run`（8080），前端 `cd frontend && npm run dev`；
数据库已回干净种子状态（`scripts/clean-smoke.sql`）。

1. **登录 admin/admin123**：看侧边栏新增 设备管理/质量管理/追溯查询/生产看板 菜单
2. **生产工单** → 新建（产品 AOC 55 英寸 4K 智能电视，数量 10，外部订单号手填）→ 下发 → 13 个任务
3. **工序任务** → 全部派工（operator）→ 逐道开工报工 10/10/0（第 13 道报完工单自动 COMPLETED）
4. **质量管理 → 质检任务**：9 个待检验任务（9 个需质检工序），选 IQC 开始检验 → 录入 10 合格 → COMPLETED；
   选 AGING 开始检验 → 9 合格 1 不良（不良行 FLOWER_SCREEN）→ 提交
5. **不良记录**：FLOWER_SCREEN 1 条 → 生成异常单
6. **异常管理**：异常单 OPEN → 处理 → 关闭（填处理结论）
7. **追溯查询**：按工单（工单号+时间线）、按 SN（列表点行 → 出生信息 + 58 条时间线）、
   按批次（报工批次号 → 报工列表 + 工单去重）
8. **设备管理**：10 台种子设备状态每 15s 自动漂移，手动状态切换
9. **生产看板**：6 KPI + 4 图表实时刷新，设备状态分布随漂移变化
10. **退出 → 登录 qa/qa123**：只见 工序任务/报工/质量管理/追溯/看板，按钮级权限生效
    （qa 无工单下发、设备写、异常越权按钮全部消失）

## 十、第 4 周计划（衔接）

- ERP/WMS 模拟集成（external_order_no 已留字段，演示文档对外接口定义待定）
- 前端动态路由（菜单表 perm 列已就位）
- 物料追溯升级（报工反写物料批次消耗，BOM 齐套校验）
- 生产排程（工单优先级/交期简单排程）
