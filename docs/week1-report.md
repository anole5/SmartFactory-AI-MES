# SmartFactory-AI-MES 第 1 周完成报告

> 日期：2026-08-22 ~ 2026-08-23
> 范围：环境确认与修正、建库建表、前后端工程骨架、基础资料模块（产品/物料/BOM/工艺路线/工序/工位）、电视 Demo 种子数据与大屏页、首个 git 提交推送
> 代码：GitHub [anole5/SmartFactory-AI-MES](https://github.com/anole5/SmartFactory-AI-MES)（main 分支，7 条提交）

---

## 一、本周目标

按 Obsidian 方案《四周开发落地计划》执行第 1 周：

1. 环境确认与修正（Docker/MySQL/JDK/Maven/Node）
2. 数据库设计建表 + 电视 Demo 种子数据
3. 后端工程骨架（统一返回/异常/分页/审计）
4. 基础资料全模块 CRUD（产品/物料/工序/工位/BOM/工艺路线）
5. 前端工程与页面（登录/布局/7 个页面/电视大屏）
6. 端到端验证 + 推送 GitHub

## 二、完成情况

| # | 任务 | 提交 | 状态 |
|---|---|---|---|
| T1 | 仓库骨架与 Maven 构建脚本 | `f319764` | ✅ |
| T2 | 建库建表与种子数据 | `5940d41` | ✅ |
| T3 | 后端骨架与 common/config | `5b236a6` | ✅ |
| T4 | 产品/物料/工序/工位 CRUD | `448428b` | ✅ |
| T5 | BOM 与工艺路线 + 简化登录 | `f5ac2b3` | ✅ |
| T6 | 前端工程与页面 | `e612e2f` | ✅ |
| T7 | README 完善与推送 | `77ba67e` | ✅ |

**验证结果**：

- 后端冒烟测试 `scripts/smoke.mjs`：**21/21 通过**（覆盖种子数据读取、CRUD、编码唯一 409、BOM 整单事务与快照回填、状态机流转与非法流转拒绝、路线步骤与工位填充、固定 token 登录、参数校验 400）
- 前端 `npm run build`（vue-tsc 类型检查 + 打包）通过；dev server 页面可服务，Vite 代理 → 后端链路验证通过
- 冒烟测试产生的数据已清理，数据库保持干净种子状态

## 三、环境与基础设施决策

| 项 | 决策 | 原因 |
|---|---|---|
| MySQL | 复用尚硅谷课程 Docker 容器（3306），新建 `smartfactory_mes` 库 + 专用账号 | 不重复装环境；**绝不动课程 meta/dw 库** |
| JDK | 仓库内 `backend/mvn.cmd` 切 JDK 17 | 全局 JAVA_HOME 指向 JDK 8（旧项目依赖），不能改 |
| Maven 本地仓库 | `backend/.mvn/maven.config` 指定 `D:\mvn-repository` | 复用已有仓库，避免重新下载 |
| SQL 导入 | Git Bash 重定向 + `--default-character-set=utf8mb4` | Windows PowerShell 管道按 GBK 处理会破坏 UTF-8 中文 |
| 主键 | 自增 BIGINT（`IdType.AUTO`） | 演示项目 ID 小、调试直观；避免雪花 19 位 Long 前端丢精度 |
| Long 序列化 | JacksonConfig 全局 Long → String | 前端 JS number 精度只有 53 位，字符串彻底规避 |

## 四、系统架构

### 4.1 后端分层（模块化单体）

```text
com.smartfactory.mes
├── SmartFactoryMesApplication        # 启动类，@MapperScan
├── common/api        # ApiResult/ResultCode/PageResult/PageQuery/EnumUtils
├── common/exception  # BusinessException(RuntimeException) + GlobalExceptionHandler
├── common/web        # RequestIdFilter（MDC + X-Request-Id 响应头）
├── config            # MybatisPlusConfig/JacksonConfig/AuditMetaObjectHandler
├── auth              # 简化登录（固定 token）
└── master            # 基础资料：entity/enums/mapper/dto/service/controller
    ├── entity        # 8 个实体 + BaseEntity（审计字段 + @TableLogic）
    ├── enums         # 状态枚举（@EnumValue 持久化 + @JsonValue 序列化）
    ├── mapper        # 8 个 Mapper（继承 BaseMapper，零 XML）
    ├── dto           # QueryDTO / SaveDTO(含 @Valid 校验) / VO(含 of 转换)
    ├── service       # 接口 + impl（业务规则全部在这里）
    └── controller    # 薄控制器，只做参数接收与返回
```

分层原则：**Controller 只做参数接收与返回，业务规则全部在 Service**；DTO 入参带 jakarta 校验注解，VO 出参不带实体直接暴露。

### 4.2 前端结构（Vue 3 + Vite + TS + Element Plus）

```text
src
├── api/          # request.ts(axios 拦截器统一解包/错误提示) + index.ts(模块 API) + types.ts
├── constants/    # dict.ts（状态字典中文映射）
├── stores/       # auth.ts（Pinia，token 存 localStorage）
├── router/       # 路由 + 守卫（无 token 跳登录）
├── layout/       # 侧边菜单 + 顶栏 + 退出
└── views/
    ├── login/            # 演示登录
    ├── products|materials|processes|workstations/   # 四模块同构列表页
    ├── boms/             # 列表 + BomDrawer（头+明细整单编辑）
    ├── routes/           # 列表 + RouteDrawer（步骤上移/下移）
    └── tv-demo/          # 深色大屏：BOM 清单 + 工序流 + 统计
```

关键点：axios 响应拦截器统一解包 `{code,message,data}`，`code!==0` 弹错误提示并 reject；请求拦截器统一携带 `Authorization` 头。

### 4.3 数据模型（8 张表）

| 表 | 说明 | 关键字段 |
|---|---|---|
| mes_product | 产品 | product_code/name/type/spec/unit/status(ENABLED/DISABLED) |
| mes_material | 物料 | material_code/name/type/unit/trace_required/status |
| mes_process | 工序（无状态） | process_code/name/need_inspection/standard_minutes |
| mes_workstation | 工位 | workstation_code/name/equipment_code/name/status |
| mes_bom | BOM 头 | bom_no/product_id/version/status(DRAFT/ACTIVE/OBSOLETE)/effective_date |
| mes_bom_item | BOM 明细 | bom_id/line_no/material_id/**快照字段**/required_qty/loss_rate |
| mes_route | 工艺路线头 | route_no/product_id/version/status |
| mes_route_step | 工艺步骤 | route_id/sequence_no/process_id/**快照字段**/workstation_id/need_inspection/standard_minutes |

统一模板：`id BIGINT AUTO_INCREMENT PK`、`tenant_id` 默认 1（多租户预留）、created_by/at、updated_by/at、deleted 逻辑删除标记。

## 五、核心业务规则实现

1. **编码唯一性**：Service 层 LambdaQueryWrapper 校验（`checkCodeUnique`）→ 冲突抛 BusinessException(409)。
   数据库**不建唯一索引**——逻辑删除的行物理保留，唯一索引会卡住编码复用。
2. **引用保护**：产品被 BOM/路线引用禁删；物料被 BOM 明细引用禁删；工序/工位被路线步骤引用禁删。
3. **状态机**（BOM/工艺路线）：`DRAFT → ACTIVE → OBSOLETE`，同值幂等、回退/跳级一律 409。
   仅 DRAFT 可编辑/删除；激活时再校验产品仍为 ENABLED。
4. **产品启停用联动**：停用产品前检查是否存在生效中的 BOM/工艺路线，存在则拒绝；
   反之产品未启用时不能维护/激活 BOM 与路线。
5. **整单事务**：BOM = 头 + 明细数组一次提交（`@Transactional`），明细先删后插、行号按数组顺序重排；
   工艺路线同构，sequenceNo 按数组顺序生成（前端"上移/下移"即交换数组位置）。
6. **快照字段服务端回填**：BOM 明细存 `material_code_snapshot/material_name_snapshot/unit_snapshot`，
   路线步骤存工序编码/名称/标准工时快照——**物料/工序主数据后续改名不影响历史 BOM/路线**。
7. **单号生成**：bomNo = `BOM`+时间戳、routeNo = `RT`+时间戳（第 2 周换正式单号生成器，代码已留 TODO）。
8. **逻辑删除**：`@TableLogic` 全局配置；BOM/路线删除时明细随头一起逻辑删除（MP 不级联，需手动）。

## 六、接口清单

统一响应 `{code, message, data, requestId}`：0 成功 / 400 参数错误 / 401 未授权 / 404 不存在 / 409 业务冲突 / 500 系统错误；context-path `/api`。

| 模块 | 端点 | 说明 |
|---|---|---|
| 认证 | `POST /api/auth/login` | 演示登录，固定 token `smartfactory-demo-token` |
| 产品/物料/工位 | `/api/master/{products,materials,workstations}/...` | page/get/post/put/delete + `PUT /{id}/status` |
| 工序 | `/api/master/processes/...` | 同构，无启停用 |
| BOM | `/api/master/boms/...` | 头+明细整单；`PUT /{id}/status` 状态机 |
| 工艺路线 | `/api/master/routes/...` | 头+步骤整单；状态机同 BOM |

## 七、技术难点与踩坑记录

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| 1 | `mvn.cmd` 中文注释致命令损坏 | cmd.exe 按 GBK 解析 .cmd，UTF-8 中文被乱码吞并 | 脚本改纯 ASCII 注释 |
| 2 | 脚本不生效，mvn 仍跑 Java 1.8 | PATH 优先解析到全局 mvn.cmd | 必须 `cd backend && ./mvn.cmd` 显式调用 |
| 3 | maven.config 报"无法识别条目" | 不支持注释与多余空格 | 单行一个参数 |
| 4 | MyBatis-Plus 3.5.17 编译报 IService 不存在 | 3.5.17 把 IService 迁到 `com.baomidou.mybatisplus.spring.service` | **锁 3.5.16**（与主流文档兼容，适合学习项目） |
| 5 | PaginationInnerInterceptor 找不到 | 3.5.9 起分页拦截器拆到 `mybatis-plus-jsqlparser` 可选模块 | pom 显式加该依赖，漏配则分页静默失效 |
| 6 | `Page.convert()` 返回 `IPage` 与 `Page` 不兼容 | MP 类型设计 | `PageResult.of(IPage<T>)` 用接口类型 |
| 7 | PowerShell 管道导入 SQL 中文乱码 | PS 管道按 GBK 编码 | Git Bash 重定向 + `--default-character-set=utf8mb4` |
| 8 | 前端 `@` 别名 vue-tsc 报找不到模块 | vite alias 与 tsconfig 是两套配置 | tsconfig.app.json 加 `paths`（TS6 已弃用 baseUrl，直接用相对 paths） |
| 9 | 抽屉组件 v-model 属性名不匹配 | props 定义 `visible` 与 v-model 的 `modelValue` 不一致 | props 统一为 `modelValue` |

## 八、面试考点提炼（本周代码可直接讲的点）

1. **BusinessException 必须继承 RuntimeException**：Spring `@Transactional` 默认只对 RuntimeException
   回滚；若用受检异常，BOM"头+明细"整单事务会半提交（头写入、明细没写入）。代码注释里已点明。
2. **逻辑删除与唯一索引的冲突**：唯一索引会阻止已删编码复用 → 编码唯一性放 Service 层。
3. **快照字段的意义**：主数据会变，历史单据不能变 → 下单时回填快照。
4. **状态机设计**：显式枚举合法流转，拒绝回退/跳级，而非散落 if-else。
5. **分页拦截器拆分**：MyBatis-Plus 3.5.9+ 的模块化演进，漏配 jsqlparser 分页参数静默失效——排查经验。
6. **Long 精度问题**：雪花 ID 19 位超出 JS number 安全范围，方案选型时就要考虑（本项目用自增 + Long→String 双保险）。
7. **前端"上移/下移"的实现**：不存顺序链表，数组顺序即业务顺序，后端按数组下标生成序号。
8. **代理不重写路径**：后端 context-path `/api` 与 Vite proxy `/api` 对齐，避免一套 URL 两套拼接逻辑。

## 九、第 2 周计划（衔接）

- 生产执行主链路：生产工单 → 下发 → 工序任务 → 派工 → 报工
- 真实登录：用户表 + JWT + 权限拦截（替换固定 token）
- BOM/路线版本升级：激活新版本自动作废旧版本（代码已留 TODO）
- 业务单号生成器（替换时间戳单号）
- 操作人回填（当前审计字段填 0，接入登录后取真实用户）
