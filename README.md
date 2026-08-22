# SmartFactory-AI-MES

轻量制造执行系统（MES）+ AI 工厂知识库。

> 面向离散制造场景的学习/演示项目，第一版以 **AOC 55 英寸 4K 智能电视** 为 Demo 场景，
> 覆盖产品、物料、BOM、工艺路线、生产工单、派工、报工、质检、追溯、异常、生产看板与 AI 应用。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.5.16、MyBatis-Plus 3.5.16、MySQL 8、Lombok |
| 前端 | Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、Axios |
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
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 < sql/01-schema.sql
docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/02-seed-master.sql
```

> 注意：Windows PowerShell 管道会以 GBK 破坏 UTF-8 内容，请在 Git Bash 中执行。

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

浏览器打开 http://localhost:5173 ，任意用户名/密码登录（第 1 周为演示登录）。

### 4. 演示路径

登录 → 产品管理（可见种子产品 TV-AOC-55U4K-001）→ BOM 管理（查看生效 BOM 的 20 行明细）
→ 工艺路线（查看 13 步工序流）→ 电视 Demo 页（深色大屏总览）。

### 冒烟测试

```bash
# 后端启动后执行（Node 18+ 内置 fetch，无需安装依赖）
node scripts/smoke.mjs
```

## 接口清单（第 1 周）

统一响应结构 `{code, message, data, requestId}`，`code=0` 成功；业务冲突 409、参数错误 400。

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 认证 | POST | `/api/auth/login` | 演示登录，返回固定 token `smartfactory-demo-token` |
| 产品 | GET | `/api/master/products/page` | 分页（keyword/status） |
| 产品 | GET/POST/PUT/DELETE | `/api/master/products/{id}` | 详情/创建/修改/逻辑删除 |
| 产品 | PUT | `/api/master/products/{id}/status` | 启停用（存在生效 BOM/路线时禁停用） |
| 物料 | 同产品结构 | `/api/master/materials/*` | 被 BOM 明细引用禁删 |
| 工序 | 同产品结构（无状态） | `/api/master/processes/*` | 被路线步骤引用禁删 |
| 工位 | 同产品结构 | `/api/master/workstations/*` | 被路线步骤引用禁删 |
| BOM | GET/POST/PUT/DELETE | `/api/master/boms/{id}` | 头+明细整单提交；仅 DRAFT 可改/删 |
| BOM | PUT | `/api/master/boms/{id}/status` | 状态机 DRAFT→ACTIVE→OBSOLETE |
| 工艺路线 | 同 BOM 结构 | `/api/master/routes/*` | 头+步骤整单提交，sequenceNo 按数组顺序生成 |

## 技术决策记录

1. **主键用自增 Long 而非雪花 ID**：演示项目 ID 小、调试直观，且避免雪花 19 位 Long 在前端 JS 丢失精度；
   业务单号（bomNo/routeNo）由后端生成，第 2 周做正式单号生成器。
2. **Long 序列化为字符串**：JacksonConfig 统一处理，前端拿到 `"1"` 而非 `1`，彻底规避精度问题。
3. **编码唯一性靠 Service 校验而非唯一索引**：逻辑删除的行物理保留，唯一索引会卡住编码复用。
4. **快照字段服务端回填**：BOM 明细存 material_code_snapshot 等，物料改名不影响历史 BOM。
5. **BusinessException 继承 RuntimeException**：保证 `@Transactional` 回滚——头+明细整单事务中途失败
   不会出现"头写入了、明细没写入"的半提交状态。
6. **MyBatis-Plus 版本锁 3.5.16**：3.5.17 将 IService 迁移到 `com.baomidou.mybatisplus.spring.service`，
   与主流文档不兼容，不适合学习项目。
7. **分页拦截器独立依赖**：3.5.9 起 `PaginationInnerInterceptor` 拆到 `mybatis-plus-jsqlparser`，
   漏配则分页参数静默失效。
8. **Maven 用仓库内脚本**：`backend/mvn.cmd` 切 JDK 17 + `backend/.mvn/maven.config` 指本地仓库，
   不改全局 JAVA_HOME（旧项目依赖 JDK 8）。
9. **登录第 1 周简化**：固定 token + 前端路由守卫闭环，第 2 周接真实用户表 + JWT + 权限。

## 开发进度

> 各周完成详情见 `docs/` 周报：[第 1 周完成报告](docs/week1-report.md)

- [x] 第 1 周：工程骨架 + 基础资料（产品/物料/BOM/工艺路线/工序/工位）+ 电视 Demo 大屏
- [ ] 第 2 周：生产执行（工单/下发/工序任务/派工/报工）+ 真实登录权限
- [ ] 第 3 周：质量、追溯与看板
- [ ] 第 4 周：AI 应用与项目包装
