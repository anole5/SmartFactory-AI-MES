-- ============================================================
-- SmartFactory-MES 第 6 周建表脚本：生产深化（物料批次追溯 + 生产排程）
-- 执行方式（Git Bash，勿用 PowerShell——GBK 管道会破坏 UTF-8）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/11-schema-week6.sql
--
-- 设计约定（与 01/03/05/07/09-schema 一致）：
--  1. 通用字段统一：id(BIGINT AUTO_INCREMENT) / tenant_id / created_by / created_at /
--     updated_by / updated_at / deleted(逻辑删除)
--  2. 批次号由 mes_sequence 生成（MB 前缀），永不重复，故 UNIQUE 索引安全
--     （与 09-schema 外部订单号同款理由：序列号不复用）
--  3. 报工-批次绑定表只增不改（审计口径），"同报工同物料只绑一次"由 Service
--     层校验（与报工明细只增不改一致），不建唯一索引
--  4. 排程结果直接写 mes_operation_task 计划时间两列（ALTER 加列）：可空列，
--     generateTasks 等既有链路不触碰（NULL 无碍）；MySQL 8 不支持
--     ADD COLUMN IF NOT EXISTS，本脚本依赖干净重放（DROP → 00→12）不重复执行
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 物料批次主数据（关键件 trace_required=1 的来料批次台账）
--    批次一经创建不复用（序列号），报工绑定时校验存在性+物料匹配
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_material_batch (
  id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  batch_no               VARCHAR(64)  NOT NULL                COMMENT '批次号（MB+日期+流水）',
  material_id            BIGINT       NOT NULL                COMMENT '物料 ID',
  material_code_snapshot VARCHAR(64)  NOT NULL                COMMENT '物料编码快照',
  material_name_snapshot VARCHAR(128) NOT NULL                COMMENT '物料名称快照',
  batch_qty              INT          NOT NULL DEFAULT 0      COMMENT '批次入库数量',
  used_qty               INT          NOT NULL DEFAULT 0      COMMENT '已绑定消耗数量（绑定时按台数累加，展示口径）',
  in_date                DATE         NULL                    COMMENT '入库日期',
  supplier               VARCHAR(64)  NULL                    COMMENT '供应商',
  remark                 VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id              BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by             BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by             BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted                TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_mb_no (batch_no),
  KEY idx_mb_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '物料批次';

-- ------------------------------------------------------------
-- 2. 报工-物料批次绑定（报工时录入关键件批次，SN 反查的关键链）
--    只增不改：同报工同物料换批次在 Service 层 409 拦截
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_report_material_batch (
  id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  report_id              BIGINT       NOT NULL                COMMENT '报工记录 ID',
  work_order_id          BIGINT       NOT NULL                COMMENT '工单 ID（冗余，反查免 join）',
  material_id            BIGINT       NOT NULL                COMMENT '物料 ID',
  material_code_snapshot VARCHAR(64)  NOT NULL                COMMENT '物料编码快照',
  material_name_snapshot VARCHAR(128) NOT NULL                COMMENT '物料名称快照',
  batch_id               BIGINT       NOT NULL                COMMENT '物料批次 ID',
  batch_no_snapshot      VARCHAR(64)  NOT NULL                COMMENT '批次号快照',
  qty_used               INT          NOT NULL DEFAULT 0      COMMENT '本次消耗数量（演示口径 1:1 取报工台数）',
  tenant_id              BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by             BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by             BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted                TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_rmb_report (report_id),
  KEY idx_rmb_batch (batch_id),
  KEY idx_rmb_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '报工物料批次绑定';

-- ------------------------------------------------------------
-- 3. 工序任务加计划时间两列（第 6 周排程结果落库）
-- ------------------------------------------------------------
ALTER TABLE mes_operation_task
  ADD COLUMN plan_start_time DATETIME NULL COMMENT '计划开始时间（排程结果）' AFTER end_time,
  ADD COLUMN plan_end_time   DATETIME NULL COMMENT '计划结束时间（排程结果）' AFTER plan_start_time;
