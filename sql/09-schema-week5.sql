-- ============================================================
-- SmartFactory-MES 第 5 周建表脚本：系统集成（ERP 外部订单 + WMS 库存流水）
-- 执行方式（Git Bash，勿用 PowerShell——GBK 管道会破坏 UTF-8）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/09-schema-week5.sql
--
-- 设计约定（与 01/03/05/07-schema 一致）：
--  1. 通用字段统一：id(BIGINT AUTO_INCREMENT) / tenant_id / created_by / created_at /
--     updated_by / updated_at / deleted(逻辑删除)
--  2. 外部订单单号由 mes_sequence 生成（ERP 前缀），永不重复，故 UNIQUE 索引安全
--     （与"物料编码 Service 校验"场景不同：编码可复用，序列号不复用）
--  3. mes_inventory 用单列 item_ref_id 指代物料或成品（item_type 区分）——
--     不用 material_id/product_id 双可空列：MySQL 唯一索引允许多个 NULL 共存，
--     会插出重复库存行，CAS 扣减失效
--  4. 库存行只改数量不删除（qty 归零保留），唯一键 uk_inventory_item 天然安全
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. ERP 外部订单（模拟外部 ERP 系统下发的生产订单）
--    状态机：PENDING(已接收) → SYNCED(已转工单) → DONE(工单完工回传)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_external_order (
  id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  external_order_no     VARCHAR(64)  NOT NULL                COMMENT '外部订单号（ERP+日期+流水）',
  product_id            BIGINT       NOT NULL                COMMENT '产品 ID',
  product_code_snapshot VARCHAR(64)  NOT NULL                COMMENT '产品编码快照',
  product_name_snapshot VARCHAR(128) NOT NULL                COMMENT '产品名称快照',
  plan_qty              INT          NOT NULL                COMMENT '计划数量',
  priority              VARCHAR(16)  NULL                    COMMENT '优先级：HIGH/NORMAL/LOW（透传工单）',
  plan_start_time       DATE         NULL                    COMMENT '计划开始日期（透传工单）',
  plan_end_time         DATE         NULL                    COMMENT '计划完成日期（透传工单）',
  status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SYNCED/DONE',
  work_order_id         BIGINT       NULL                    COMMENT '转工单后回填的工单 ID',
  remark                VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id             BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by            BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by            BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted               TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_external_order_no (external_order_no),
  KEY idx_external_order_wo (work_order_id),
  KEY idx_external_order_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP 外部订单';

-- ------------------------------------------------------------
-- 2. WMS 库存（item_ref_id = 物料 ID 或成品产品 ID，由 item_type 区分）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_inventory (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  item_type   VARCHAR(20) NOT NULL                COMMENT '库存对象类型：MATERIAL 物料 / FINISHED 成品',
  item_ref_id BIGINT      NOT NULL                COMMENT '物料 ID（MATERIAL）或产品 ID（FINISHED）',
  qty         INT         NOT NULL DEFAULT 0      COMMENT '库存数量',
  remark      VARCHAR(255) NULL                   COMMENT '备注',
  tenant_id   BIGINT      NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by  BIGINT      NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by  BIGINT      NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inventory_item (item_type, item_ref_id),
  KEY idx_inventory_type (tenant_id, item_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'WMS 库存';

-- ------------------------------------------------------------
-- 3. WMS 库存流水（每次出入库一条，只增不改）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_stock_transaction (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tx_no         VARCHAR(64)  NOT NULL                COMMENT '流水号（STK+日期+流水）',
  tx_type       VARCHAR(10)  NOT NULL                COMMENT '方向：IN 入库 / OUT 出库',
  item_type     VARCHAR(20)  NOT NULL                COMMENT '库存对象类型：MATERIAL/FINISHED',
  item_ref_id   BIGINT       NOT NULL                COMMENT '物料 ID 或产品 ID',
  qty           INT          NOT NULL                COMMENT '数量（正数）',
  biz_type      VARCHAR(30)  NOT NULL                COMMENT '业务类型：PURCHASE_IN 采购入库 / PICK_OUT 工单领料 / FINISHED_IN 成品入库',
  work_order_id BIGINT       NULL                    COMMENT '关联工单 ID（领料/成品入库场景）',
  remark        VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id     BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by    BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by    BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_stock_tx_no (tx_no),
  KEY idx_stock_tx_wo (work_order_id, biz_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'WMS 库存流水';
