# 第 6 周完成报告：生产深化（物料批次追溯 + 生产排程甘特图 + 报表中心）

> 日期：2026-08-23
> 范围：关键件物料批次正反向追溯（SN↔批次闭环）+ 生产排程（优先级/交期前向排程 + 甘特图）+ 报表中心（日/周/月聚合 + EasyExcel 导出）
> 代码：GitHub [anole5/SmartFactory-AI-MES](https://github.com/anole5/SmartFactory-AI-MES)（main 分支，本周 9 条提交，累计 59 条）
> 执行期间逐任务进度见 Obsidian《AI开发实时报告/09-第6周-生产深化.md》

---

## 一、本周目标

按《02-第5至8周后续规划》原案执行第 6 周三项（SSE/向量 RAG 留第 7 周）：

1. **物料批次追溯升级**：批次主数据 + 报工绑定关键件批次（录入校验批次存在）+ 按 SN 反查整机用到的关键件批次、按批次反查报工/工单/SN（正反向闭环）
2. **生产排程**：按优先级+交期排序返回排程计划 + 甘特图按工位展示横道、逾期标红
3. **报表中心**：日/周/月三粒度报表 + EasyExcel 导出
4. **收尾**：全量冒烟回归（139 旧断言零改动）+ 文档 + 推送 + 验收

## 二、完成情况

| # | 任务 | 提交 | 状态 |
|---|---|---|---|
| T1 | 建表 + 种子（物料批次主数据/报工批次绑定 2 表 + ALTER 任务表计划时间列 + 菜单 2041/206/2061/207/2071 + 角色授权 + MB 序列 + clean-smoke 第 8 节） | `fba96cd` | ✅ |
| T2 | 物料批次主数据后端（实体/Mapper/分页/创建，MB 生成器取号 + 编码名称快照） | `c7f2e0a` | ✅ |
| T3 | 绑定+反查后端（报工内嵌绑定/补录接口/BATCH_BIND 追溯/batch-sns 反向查 SN） | `1456d3c` | ✅ |
| T4 | 排程后端（ScheduleService.run 前向排程 + gantt 甘特数据） | `d5f1f11` | ✅ |
| T5 | 报表后端（EasyExcel 4.0.3 + summary 三粒度 + export 双 sheet） | `9efc45b` | ✅ |
| T6 | 前端追溯改造（traces 四入口 + 报工弹窗批次区 + 新建批次弹窗 + 修外部订单号标签） | `3bc518a` | ✅ |
| T7 | 前端两页（排程甘特图 renderItem + 报表中心三粒度导出） | `bdf3005` | ✅ |
| T8 | 冒烟扩展 + 收尾（第 18 节 / 修复 bindBatches 前端契约 / README / 周报 / 推送） | `d4fb79f` + 本提交 | ✅ |

## 三、三大功能架构（面试可讲）

### 物料批次追溯（SN ↔ 批次正反向闭环）

```
物料批次主数据 POST /production/material-batches（batchNo = MB+日期+4 位流水，mes_sequence 取号）
报工绑定（双通道，共用同一私有校验方法）
  ├─ 主通道：报工 DTO 可选字段 materialBatchBindings（旧 payload 无此字段 → 跳过，139 旧断言零影响）
  └─ 补录通道：POST /reports/{id}/bind-batch（body 为裸数组 [{materialId,batchNo}]，报工漏绑补录）
       ├─ 校验：批次存在 409 / 批次与物料不匹配 409 / 物料非 trace_required 409
       │        同 (report,material,batch) 重放幂等 200 / 同 (report,material) 换批 409
       └─ 成功：插绑定行（编码/名称快照回填）+ 批次 used_qty 累加 + BATCH_BIND 追溯（同事务）
正向：GET /traces/sn?sn= → SnTraceVO.materialBatches（SN→出生工单→全部报工→绑定行聚合去重）
反向：GET /traces/batch-sns?batchNo= → 批次主数据 + 绑定报工列表 + 涉及工单去重 + 整机 SN 列表
```

### 生产排程（前向排程 + 甘特图）

```
POST /schedule/run（纯内存算法）
  ① 工单排序：priorityRank（HIGH<NORMAL<LOW）→ planEndTime ASC（NULL 最后）→ id ASC
  ② 取 RELEASED/IN_PROGRESS 工单下 PENDING/ASSIGNED/RUNNING/PAUSED 任务（完成/取消任务保留旧值不重算）
  ③ 按工位分组（NULL 归「未分配工位」虚拟组）→ 组内按工单全局序 → sequence_no 排
  ④ cursor = 今日 08:00；start = max(工单 planStartTime, cursor)；时长 = ceil(standardMinutes×planQty)
  ⑤ 结果落任务表 plan_start_time/plan_end_time（重跑 UPDATE 覆盖 = 幂等）
GET /schedule/gantt?date= → 计划窗口跨该日的任务 + isOverdue（planEnd<now 且未完成）
前端：ECharts custom series renderItem——x 轴 value 毫秒、y 轴 category 工位、
      横道 = api.coord 两端点换算、颜色 = 工单 id%调色板、逾期红色加粗、跨日裁切到当日窗口
```

### 报表中心（三粒度 + Excel）

```
GET /reports-center/summary?type=day|week|month&date=
  ├─ 口径：created_at >= start AND created_at < end（左闭右开，与看板 DATE(created_at) 口径一致，显式 deleted=0）
  ├─ day 按工序分组（JOIN 任务表取工序快照）、week ISO 周按日期分组、month 自然月按日期分组
  └─ 返回：totalGoodQty/totalDefectQty/yieldRate/reportCount/workOrderCount/rangeStart/rangeEnd + rows
GET /reports-center/export → EasyExcel 双 sheet（汇总/明细），裸文件流不包 ApiResult，
  Content-Disposition filename*=UTF-8''；前端 downloadRequest 裸 axios（仅 token 拦截器）→ Blob 落盘
```

**核心工程决策：**

1. **物料批次双通道绑定**：报工主通道（可选字段）+ 补录通道（独立接口）共用同一私有校验方法——「报工漏绑后补录」的演示闭环；重放幂等、换批 409 兼顾用户体验与数据一致性。
2. **排程结果直接落任务表**：ALTER 两列 plan_start_time/plan_end_time 而非独立排程表——gantt 零 join 直读、重跑 UPDATE 覆盖即幂等、clean-smoke 无新增清理；完成/取消任务保留旧值不重算（历史单据不可变原则）。
3. **排程算法纯内存 + 单行 UPDATE**：演示规模内存排程简单直观、可调试可解释；生产可换 APS 引擎不动接口。
4. **EasyExcel 裸文件流不包 ApiResult**：Blob 会被 JSON 解包拦截器误解析，故前端单独 downloadRequest 实例（仅 token 拦截器）绕开；后端直写字节流 + RFC 5987 中文文件名。
5. **报表聚合口径统一 created_at**：与 DashboardMapper 口径一致；左闭右开区间防跨粒度重复计数。

## 四、数据模型与权限

- **2 张新表 + 1 表 ALTER**：`mes_material_batch`（batch_no UNIQUE 键、material 编码/名称快照、used_qty 绑定累加）、`mes_report_material_batch`（只增不改绑定行，report/batch/material 快照回填）；`mes_operation_task` ADD plan_start_time/plan_end_time（排程结果落库，可空）
- **ActionType +1**：BATCH_BIND（关键件批次绑定，枚举累计 20 个值）
- **菜单**：F 2041 物料批次新增（204 追溯下）+ C 206 生产排程 /scheduling + F 2061 排程执行 + C 207 报表中心 /reports-center + F 2071 报表导出（生产管理目录）；admin 全量、planning 全部新菜单、operator/qa 不给（批次列表复用既有 trace:query，operator 报工绑定可用）
- **权限**：`production:material-batch:create` / `production:schedule:run` / `production:schedule:query` / `production:report:center:query` / `production:report:export`

## 五、验证记录

| 脚本 | 覆盖 | 结果 |
|---|---|---|
| verify-t6-1-schema.mjs | planning 树含 /scheduling /reports-center、operator 不含；admin 全量含新 F；F 不进树；32 张表 | 8/8 |
| verify-t6-2-batch.mjs | 种子 12 批分页/料 1 过滤 2 批/创建 batchNo MB 格式/物料不存在 409/operator 创建 403 | 13/13 |
| verify-t6-3-trace-bind.mjs | 批次不存在 409/物料不匹配 409/非关键件 409/同料换批 409/重放幂等/SN 反查含 6 料批次/batch-sns 反向/BATCH_BIND 追溯/qa+planning bind 403 | 23/23 |
| verify-t6-4-schedule.mjs | HIGH 先于 NORMAL/同工位不重叠/AGING 时长 120×planQty/重跑幂等/未 run 空/operator run 403 | 16/16 |
| verify-t6-5-report.mjs | day good=5 良率 100%/week、month 含当天/type 非法 400/export PK 魔数/operator+qa 403 | 17/17 |
| verify-t6-6-menu-contract.mjs | 新页面 path↔views 文件契约/traces 四入口/tasks 批次区/字典 BATCH_BIND/菜单 24 C 级 | 18/18 |
| verify-t6-7-pages-data.mjs | gantt 26 条字段契约/同工位串行/isOverdue 公式/远期 0 条/三粒度字段契约 | 16/16 |
| verify-t5-dynamic.mjs（回归） | 第 5 周动态路由 24 页（含 2 新页） | 7/7 |
| smoke.mjs 第 18 节 | 干净重放 00→12 全量回归（139 旧断言零改动 + 21 新断言） | **183/183** |

前端 `npm run build`（vue-tsc + vite）通过 ×3（T6/T7/T8a）；T8 收尾后 clean-smoke 回种子状态复核通过（wo=0、mb=12、rmb=0、wr=0、sn=0）。

## 六、技术决策记录（新增，README #29-34）

- **物料批次双通道绑定**：报工主通道 + 补录通道共用私有校验方法；重放幂等、换批 409
- **排程结果直接落任务表**：ALTER 两列而非独立排程表；完成/取消任务保留旧值不重算
- **排程算法纯内存 + 单行 UPDATE**：优先级→交期→id 排序、工位组串行 cursor、时长 ceil(标准工时×计划数)
- **甘特图 custom series renderItem**：毫秒 value + 工位 category、api.coord 换算、工单配色、逾期红、跨日裁切
- **EasyExcel 裸文件流不包 ApiResult**：downloadRequest 裸 axios 绕开 JSON 解包拦截器
- **报表聚合口径统一 created_at**：左闭右开区间、显式 deleted=0，与看板口径一致

## 七、遇到的问题

1. **bind-batch 裸数组契约（T8 冒烟才暴露）**：端点 `@RequestBody List<DTO>` 要求 body 是裸 JSON 数组，前端误发 `{items:[...]}` → 后端 400 "Cannot deserialize ArrayList"。冒烟第 18 节首跑暴露，顺手修复前端同一隐患（reportApi.bindBatches 改发裸数组）。
2. **gantt 残留工单**：取消工单保留旧计划列（后端口径「完成/取消任务保留旧值不重算」），verify 与页面均会看到残留横道——断言收敛到本次自建工单（设计如此，README 演示路径已注明）。
3. **isOverdue 时区坑**：verify 脚本用 `toISOString()`（UTC）与后端 LocalDateTime（本地 +8）比较导致公式断言误报——改本地时间串（pad2）比较。
4. **POST material-batches 返回 id 而非 batchNo**：创建接口返回新行 id，冒烟断言 batchNo 格式改为回查列表校验（Long→String 序列化惯例 + 单号生成器解耦）。
5. **菜单 C 级计数 24 vs 23**：tv-demo 大屏是 C 级菜单（parent_id=0），旧 ALL_PAGES 标签「21」早已 stale——两处 verify 计数同步校正为 24。

## 八、项目包装交付

- README：演示路径 17-19 步（物料批次追溯 / 生产排程甘特图 / 报表中心）、初始化 SQL 补 09-12、接口清单 9 行新接口、技术决策记录 #29-34、冒烟 183 项、开发进度勾选第 6 周（第 7 周留 SSE/向量 RAG）
- `clean-smoke.sql` 第 8 节：2 张新表清理（子先父后删）+ MB 序列复位——干净重放一键回种子
- 周报：仓库 `docs/week6-report.md` + Obsidian《AI开发实时报告》09 实时报告 / 10 完成报告

## 九、第 7 周计划（可选剩余项）

AI 回答 SSE 流式输出 + 向量 RAG 升级（本机 qdrant/embedding 容器已就绪，升级召回通道即可，管线结构不变）。
