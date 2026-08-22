-- ============================================================
-- SmartFactory-MES 第 1 周建表脚本：基础资料 8 张表
-- 执行方式（Git Bash）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 < sql/01-schema.sql
--
-- 设计约定：
--  1. 通用字段统一：id(BIGINT AUTO_INCREMENT) / tenant_id / created_by / created_at /
--     updated_by / updated_at / deleted(逻辑删除)
--  2. 编码字段只建普通索引、不建唯一索引：逻辑删除的行物理保留，
--     唯一索引会导致删除后编码无法复用；编码唯一性由 Service 层校验
--  3. status 一律 VARCHAR(20)，枚举值由 Java 枚举管理（ENABLED/DISABLED 等）
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 产品
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_product (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  product_code VARCHAR(64)  NOT NULL                COMMENT '产品编码',
  product_name VARCHAR(128) NOT NULL                COMMENT '产品名称',
  product_type VARCHAR(32)  NULL                    COMMENT '产品类型（如：智能电视）',
  specification VARCHAR(255) NULL                   COMMENT '规格型号',
  unit         VARCHAR(32)  NULL                    COMMENT '单位',
  status       VARCHAR(20)  NOT NULL DEFAULT 'DISABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
  tenant_id    BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID（第一版固定默认租户）',
  created_by   BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by   BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  KEY idx_product_code (tenant_id, product_code),
  KEY idx_product_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '产品';

-- ------------------------------------------------------------
-- 2. 物料
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_material (
  id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  material_code  VARCHAR(64)   NOT NULL                COMMENT '物料编码',
  material_name  VARCHAR(128)  NOT NULL                COMMENT '物料名称',
  material_type  VARCHAR(32)   NULL                    COMMENT '物料类型（核心件/板卡/结构件/线材/包材等）',
  unit           VARCHAR(32)   NULL                    COMMENT '单位',
  trace_required TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否批次追溯：0 否 / 1 是',
  status         VARCHAR(20)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
  remark         VARCHAR(255)  NULL                    COMMENT '备注',
  tenant_id      BIGINT        NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by     BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by     BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted        TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_material_code (tenant_id, material_code),
  KEY idx_material_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '物料';

-- ------------------------------------------------------------
-- 3. 工序（工艺字典，无启停用状态）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_process (
  id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  process_code     VARCHAR(64)   NOT NULL                COMMENT '工序编码',
  process_name     VARCHAR(128)  NOT NULL                COMMENT '工序名称',
  need_inspection  TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否需要质检：0 否 / 1 是',
  standard_minutes DECIMAL(10,2) NOT NULL DEFAULT 0      COMMENT '标准工时（分钟）',
  description      VARCHAR(255)  NULL                    COMMENT '工序说明',
  tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by       BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by       BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_process_code (tenant_id, process_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工序';

-- ------------------------------------------------------------
-- 4. 工位（含默认设备信息，第 2 周再拆独立设备表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_workstation (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  workstation_code VARCHAR(64)  NOT NULL                COMMENT '工位编码',
  workstation_name VARCHAR(128) NOT NULL                COMMENT '工位名称',
  equipment_code   VARCHAR(64)  NULL                    COMMENT '绑定设备编码',
  equipment_name   VARCHAR(128) NULL                    COMMENT '绑定设备名称',
  status           VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
  tenant_id        BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by       BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by       BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted          TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_workstation_code (tenant_id, workstation_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工位';

-- ------------------------------------------------------------
-- 5. BOM 头
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_bom (
  id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  bom_no         VARCHAR(64) NOT NULL                COMMENT 'BOM 编号（后端生成）',
  product_id     BIGINT      NOT NULL                COMMENT '产品 ID',
  version        VARCHAR(16) NOT NULL DEFAULT 'V1'   COMMENT '版本号',
  status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / ACTIVE 生效 / OBSOLETE 作废',
  effective_date DATE        NULL                    COMMENT '生效日期',
  remark         VARCHAR(255) NULL                   COMMENT '备注',
  tenant_id      BIGINT      NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by     BIGINT      NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by     BIGINT      NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted        TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_bom_no (tenant_id, bom_no),
  KEY idx_bom_product (tenant_id, product_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'BOM 头';

-- ------------------------------------------------------------
-- 6. BOM 明细
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_bom_item (
  id                     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  bom_id                 BIGINT        NOT NULL                COMMENT 'BOM 头 ID',
  line_no                INT           NOT NULL                COMMENT '明细行号（按数组顺序 1..n）',
  material_id            BIGINT        NOT NULL                COMMENT '物料 ID',
  material_code_snapshot VARCHAR(64)   NULL                    COMMENT '物料编码快照（防物料改名影响历史）',
  material_name_snapshot VARCHAR(128)  NULL                    COMMENT '物料名称快照',
  unit_snapshot          VARCHAR(32)   NULL                    COMMENT '单位快照',
  required_qty           DECIMAL(18,4) NOT NULL DEFAULT 1      COMMENT '单位用量',
  loss_rate              DECIMAL(5,2)  NOT NULL DEFAULT 0      COMMENT '损耗率（%）',
  remark                 VARCHAR(255)  NULL                    COMMENT '备注',
  tenant_id              BIGINT        NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by             BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by             BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted                TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_bom_item_bom (bom_id, line_no),
  KEY idx_bom_item_material (tenant_id, material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'BOM 明细';

-- ------------------------------------------------------------
-- 7. 工艺路线头
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_route (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  route_no   VARCHAR(64) NOT NULL                COMMENT '工艺路线编号（后端生成）',
  product_id BIGINT      NOT NULL                COMMENT '产品 ID',
  version    VARCHAR(16) NOT NULL DEFAULT 'V1'   COMMENT '版本号',
  status     VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / ACTIVE 生效 / OBSOLETE 作废',
  remark     VARCHAR(255) NULL                   COMMENT '备注',
  tenant_id  BIGINT      NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by BIGINT      NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT      NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted    TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_route_no (tenant_id, route_no),
  KEY idx_route_product (tenant_id, product_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工艺路线头';

-- ------------------------------------------------------------
-- 8. 工艺路线步骤
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_route_step (
  id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  route_id         BIGINT        NOT NULL                COMMENT '工艺路线 ID',
  sequence_no      INT           NOT NULL                COMMENT '工序顺序号（1..n，工艺顺序即数组顺序）',
  process_id       BIGINT        NOT NULL                COMMENT '工序 ID',
  process_code_snapshot VARCHAR(64)  NULL                COMMENT '工序编码快照',
  process_name_snapshot VARCHAR(128) NULL                COMMENT '工序名称快照',
  workstation_id   BIGINT        NULL                    COMMENT '默认工位 ID（可空）',
  need_inspection  TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '本步是否质检：0 否 / 1 是',
  standard_minutes DECIMAL(10,2) NOT NULL DEFAULT 0      COMMENT '标准工时快照（分钟）',
  remark           VARCHAR(255)  NULL                    COMMENT '备注',
  tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by       BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by       BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_route_step_route (route_id, sequence_no),
  KEY idx_route_step_process (tenant_id, process_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工艺路线步骤';
