# 第 5 周完成报告：系统集成（ERP/WMS 模拟 + 前端动态路由）

> 日期：2026-08-23
> 范围：ERP 外部订单一键转工单 + WMS 库存领料/入库闭环 + 生产开工/完工集成钩子 + 前端动态路由（菜单表驱动）
> 代码：GitHub [anole5/SmartFactory-AI-MES](https://github.com/anole5/SmartFactory-AI-MES)（main 分支，本周 7 条提交，累计 50 条）
> 执行期间逐任务进度见 Obsidian《AI开发实时报告/07-第5周-系统集成.md》

---

## 一、本周目标

按《02-第5至8周后续规划》执行第 5 周——把 MES 从"单系统"接到"系统之系统"：

1. **ERP 模拟集成**：外部订单一键转工单，外部订单全生命周期 PENDING → SYNCED → DONE
2. **WMS 模拟集成**：采购入库、工单领料（关键物料齐套校验）、完工成品入库、库存流水
3. **生产钩子**：ERP 来源工单开工前须完成领料；完工自动回传 ERP + 成品入库（集成失败不阻断生产）
4. **前端动态路由**：登录后按角色菜单树动态注册路由 + 递归侧边栏（菜单表驱动，角色差异即菜单差异）
5. **收尾**：全量冒烟回归（老 139 项零改动）+ 文档 + 推送 + 验收

## 二、完成情况

| # | 任务 | 提交 | 状态 |
|---|---|---|---|
| T1 | 建表 + 种子（3 表 + 系统集成菜单 + tv-demo 补行 + 角色授权 + 库存种子 + ERP/STK 序列 + ActionType+2 + clean-smoke 同步补） | `c14af12` | ✅ |
| T2 | ERP 外部订单后端（模拟下单 / 一键转工单 CAS / 完工回传） | `8f0f141` | ✅ |
| T3 | WMS 库存后端 + 生产钩子（采购入库 / 工单领料 / 开工校验 / 完工回传 + 成品入库） | `567886f` | ✅ |
| T4 | 动态路由后端（GET /auth/menus 角色菜单树） | `9b5a62d` | ✅ |
| T5 | 前端两页（ERP 订单 / WMS 库存，静态路由先行注册，全功能中间态） | `ce66cdf` | ✅ |
| T6 | 前端动态路由改造（menu store / 守卫 / 递归菜单 / 404 / 降级） | `328dc7b` | ✅ |
| T7 | 冒烟扩展 + 收尾（第 17 节 / README / 周报 / 推送） | — | ✅ |

## 三、系统集成架构（面试可讲）

```
ERP 模拟下单 POST /integration/erp/orders（PENDING）
  └─ 一键转工单 PUT /{id}/to-work-order
       ├─ CAS：WHERE id=? AND status='PENDING'（并发重复转单 409）
       └─ 先建工单再翻转状态，失败全回滚（不留孤儿工单）→ 回填 SYNCED + work_order_id
生产执行（沿用第 2 周链路：下发 → 13 任务 → 派工 → 开工 → 报工）
  ├─ 开工校验钩子（REQUIRES_NEW，异常静默放行——集成失败不阻断生产）
  │    ERP 来源（EXISTS mes_external_order）→ 关键物料须已足额领用，否则 409「请先领料」
  ├─ WMS 领料 POST /integration/wms/pick {workOrderId}
  │    BOM trace_required=1 物料 × planQty → 条件 UPDATE 原子扣减 → STK 流水同事务
  └─ 完工钩子（报工 COMPLETED 翻转那一刻才触发，只对 ERP 工单）
       ├─ ERP 回传：SYNCED → DONE + 追溯 ERP_DONE
       └─ WMS 成品入库：合格数入库 FINISHED_IN + 追溯 WMS_FINISHED_IN（幂等）
采购入库 POST /integration/wms/stock-in（ON DUPLICATE KEY 累加 + PURCHASE_IN 流水）
```

**核心工程决策：**

1. **ERP 转工单 CAS 翻转**：`WHERE id=? AND status='PENDING'` 更新 0 行即并发重复转单 → 抛异常回滚刚创建的工单（先建工单再翻转状态，失败即全回滚）。
2. **集成钩子 REQUIRES_NEW + 静默降级**：开工校验/完工回传以独立事务执行且异常吞掉只告警——"集成失败不阻断生产"；ERP/WMS 挂了照常生产，外部订单停留 SYNCED 可人工重试。
3. **工单 ERP 来源统一判 `EXISTS(mes_external_order WHERE work_order_id=?)`**：不依赖 external_order_no 字段——手建工单即使手填外部单号也不触发集成钩子（老冒烟链路零影响）。
4. **库存并发安全靠单行原子 SQL**：累加 `ON DUPLICATE KEY UPDATE qty=qty+VALUES(qty)`、扣减 `WHERE qty>=?` 条件 UPDATE——全程无"先读后写"丢失更新窗口。
5. **完工钩子 STK 流水号在外层事务预取**：REQUIRES_NEW 子事务内再取 mes_sequence 号会与报工主事务竞争序列行锁（实测锁等待超时），故 STK 号在外层先取好传入。
6. **前端动态路由 = 后端菜单树驱动**：登录后拉 `/auth/menus`，组件按 `import.meta.glob` 路径约定反查（/products → views/products/index.vue，新页面零注册）；菜单接口失败降级本地静态树（不白屏）；退出/401 时 removeRoute 防换账号残留旧路由。

## 四、数据模型与权限

- **3 张新表**（无既有表 schema 变更）：`mes_external_order`（PENDING→SYNCED→DONE）、`mes_inventory`（item_type MATERIAL/FINISHED + item_ref_id 单列 + 唯一键）、`mes_stock_transaction`（IN/OUT + biz_type PURCHASE_IN/PICK_OUT/FINISHED_IN）
- **菜单**：目录 5 系统集成 + C 501 ERP 订单 / C 502 WMS 库存 + F 5011/5012/5021/5022；tv-demo 补菜单行（动态化后侧边栏不丢大屏入口）
- **权限**：`erp:order:create` / `erp:order:to-work-order` / `wms:inventory:in` / `wms:pick`——admin 全量、planning 集成全功能、operator/qa 不给（动态菜单角色差异的演示素材）

## 五、验证记录

| 脚本 | 覆盖 | 结果 |
|---|---|---|
| verify-t5-erp.mjs | 下单/列表/转工单透传/重复转单 409/权限 403/回传 | 18/18 |
| verify-t5-wms.mjs | 入库累加/领料/重复领料 409/库存不足 409/未领料开工 409/领料后开工 200/手建工单放行/完工回传 + 成品入库 | 33/33 |
| verify-t5-menus.mjs | admin/planning/operator/qa 菜单树差异 + 未登录 401 | 18/18 |
| verify-t5-dynamic.mjs | 菜单 path 与视图文件契约 / 角色差异 / 404 兜底 | 7/7 |
| smoke.mjs 第 17 节 | 干净重放 00→10 全量回归（139 旧断言零改动 + 23 新断言） | **162/162** |

前端 `npm run build`（vue-tsc + vite）通过 ×2；`vite preview` 产物服务性检查通过。

## 六、技术决策记录（新增，README #24-28）

- **ERP 转工单 CAS 翻转**：并发重复转单 409，先建工单再翻转状态，失败全回滚不留孤儿工单
- **集成钩子 REQUIRES_NEW + 静默降级**："集成失败不阻断生产"；ERP 来源判定用 EXISTS 而非外部单号字段
- **完工钩子流水号在主事务预取**：子事务内取 mes_sequence 会锁竞争超时，STK 号外层预取传入
- **库存并发靠单行原子 SQL**：累加 ON DUPLICATE KEY、扣减条件 UPDATE，无丢失更新窗口
- **前端动态路由 = 后端菜单树驱动**：glob 路径约定反查 + epoch 防串会话 + 接口失败降级静态树 + removeRoute 清理

## 七、遇到的问题

1. **REQUIRES_NEW 子事务取号死锁（最隐蔽）**：完工钩子子事务内再取 mes_sequence 号，与报工主事务竞争序列行锁 → 锁等待超时。改 STK 号外层预取传入子事务解决。
2. **externalOrderNo 手填陷阱**：老冒烟第 246 步手建工单就手填了外部单号——若用 `external_order_no IS NOT NULL` 判 ERP 来源会打破 139 回归。改用 EXISTS 关联外部订单表判定。
3. **process.exit 触发 libuv 断言崩溃**：verify 脚本全部断言打印 PASS 后强制退出，undici keep-alive 连接未关导致 `Assertion failed: !(handle->flags & UV_HANDLE_CLOSING)`（exit 127）。改 `process.exitCode` 优雅退出。
4. **TS2774 恒真条件**：import.meta.glob 的值为恒定义函数类型，`if (component)` 报"条件恒为真"——改 `viewKey in viewModules` 判存在。
5. **后端重启杀进程**：taskkill 杀 mvn wrapper 会留孤儿 java 进程占 8080，须按端口找 java PID 直接杀，再以日志中 "Started SmartFactoryMesApplication" 确认启动完成。

## 八、项目包装交付

- README：演示路径 14-16 步（ERP 订单 / WMS 库存 / 动态菜单角色差异）、接口清单 9 行（ERP 4 + WMS 4 + 菜单 1）、技术决策记录 #24-28、冒烟 162 项、开发进度勾选第 5 周
- `clean-smoke.sql`：3 张新表清理（子先父后删）+ ERP/STK 序列复位——干净重放一键回种子
- 周报：本文件 + Obsidian《AI开发实时报告》07 实时报告 / 08 完成报告

## 九、第 6 周计划（可选剩余项）

物料追溯升级（按 SN/批次串联关键件）、生产排程（优先级/交期排产）、AI 回答 SSE 流式输出。
