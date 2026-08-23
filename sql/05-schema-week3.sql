-- ============================================================
-- SmartFactory-MES 第 3 周建表脚本：质量 4 表 + 成品 SN + 设备主数据
-- 执行方式（Git Bash，勿用 PowerShell——GBK 管道会破坏 UTF-8）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/05-schema-week3.sql
--
-- 设计约定（与 01/03-schema 一致）：
--  1. 通用字段统一：id(BIGINT AUTO_INCREMENT) / tenant_id / created_by / created_at /
--     updated_by / updated_at / deleted(逻辑删除)
--  2. 业务单号（INP/INS/DEF/EXP/SN）由 mes_sequence 原子生成器保证唯一，
--     不建唯一索引——规避逻辑删除 + 唯一索引冲突
--  3. 数量字段用 INT；状态 VARCHAR(20) 由 Java 枚举管理（状态机见各表注释）
--  4. 快照字段（*_snapshot）一律服务端回填，主数据改名不影响历史单据
--  5. 质检任务/记录/不良/异常互相以 id 关联（非单号），删除时子先父后
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 质检任务（需要质检的工序任务完成后，由报工事务自动生成）
--    状态机：PENDING → INSPECTING → COMPLETED
--            PENDING / INSPECTING 可 → CANCELLED（工单取消时级联）
--    plan_qty = 触发任务报工完成时的累计完成数量
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_inspection_task (
  id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  inspection_task_no    VARCHAR(32)  NOT NULL                COMMENT '质检任务号（生成器生成：INP+日期+流水）',
  work_order_id         BIGINT       NOT NULL                COMMENT '工单 ID',
  operation_task_id     BIGINT       NOT NULL                COMMENT '工序任务 ID（触发来源）',
  process_code_snapshot VARCHAR(64)  NULL                    COMMENT '工序编码快照',
  process_name_snapshot VARCHAR(128) NULL                    COMMENT '工序名称快照',
  workstation_id        BIGINT       NULL                    COMMENT '工位 ID（来自工序任务）',
  plan_qty              INT          NOT NULL                COMMENT '送检数量（= 工序任务累计完成数）',
  inspected_qty         INT          NOT NULL DEFAULT 0      COMMENT '已检数量（合格+不良）',
  good_qty              INT          NOT NULL DEFAULT 0      COMMENT '检验合格数量',
  defect_qty            INT          NOT NULL DEFAULT 0      COMMENT '检验不良数量',
  status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/INSPECTING/COMPLETED/CANCELLED',
  inspector_id          BIGINT       NULL                    COMMENT '质检员 ID（开始检验时回填）',
  start_time            DATETIME     NULL                    COMMENT '开始检验时间',
  end_time              DATETIME     NULL                    COMMENT '检验完成时间（任务 COMPLETED 时回填）',
  remark                VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id             BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by            BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by            BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted               TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_inspection_wo (work_order_id),
  KEY idx_inspection_task (operation_task_id),
  KEY idx_inspection_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '质检任务';

-- ------------------------------------------------------------
-- 2. 质检记录（一次检验录入一条，允许同一质检任务分次录入，只增不改）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_inspection_record (
  id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  inspection_record_no VARCHAR(32)  NOT NULL                COMMENT '质检记录号（生成器生成：INS+日期+流水）',
  inspection_task_id   BIGINT       NOT NULL                COMMENT '质检任务 ID',
  work_order_id        BIGINT       NOT NULL                COMMENT '工单 ID',
  operation_task_id    BIGINT       NOT NULL                COMMENT '工序任务 ID',
  good_qty             INT          NOT NULL                COMMENT '本次检验合格数量',
  defect_qty           INT          NOT NULL                COMMENT '本次检验不良数量',
  inspect_time         DATETIME     NOT NULL                COMMENT '检验时间',
  inspector_id         BIGINT       NOT NULL                COMMENT '质检员 ID（当前登录用户）',
  remark               VARCHAR(255) NULL                    COMMENT '检验说明',
  tenant_id            BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by           BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by           BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted              TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_ins_record_task (inspection_task_id),
  KEY idx_ins_record_wo (work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '质检记录';

-- ------------------------------------------------------------
-- 3. 不良记录（质检录入不合格时生成，一次检验每种不良码一条）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_defect_record (
  id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  defect_no            VARCHAR(32)  NOT NULL                COMMENT '不良单号（生成器生成：DEF+日期+流水）',
  inspection_record_id BIGINT       NOT NULL                COMMENT '质检记录 ID（归属）',
  inspection_task_id   BIGINT       NOT NULL                COMMENT '质检任务 ID',
  work_order_id        BIGINT       NOT NULL                COMMENT '工单 ID',
  operation_task_id    BIGINT       NOT NULL                COMMENT '工序任务 ID',
  defect_code          VARCHAR(64)  NOT NULL                COMMENT '不良代码：BLACK_SCREEN/FLOWER_SCREEN/NO_SOUND/HDMI_ABNORMAL/BURN_FAIL/AGING_RESTART/ACCESSORY_MISSING',
  defect_qty           INT          NOT NULL                COMMENT '不良数量',
  remark               VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id            BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by           BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by           BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted              TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_defect_record (inspection_record_id),
  KEY idx_defect_task (inspection_task_id),
  KEY idx_defect_wo (work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不良记录';

-- ------------------------------------------------------------
-- 4. 异常单（不良可生成异常单，也可手工创建）
--    状态机：OPEN → PROCESSING → CLOSED
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_exception_order (
  id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  exception_no        VARCHAR(32)  NOT NULL                COMMENT '异常单号（生成器生成：EXP+日期+流水）',
  source_type         VARCHAR(20)  NOT NULL                COMMENT '来源：DEFECT 不良生成 / MANUAL 手工创建',
  defect_record_id    BIGINT       NULL                    COMMENT '不良记录 ID（source_type=DEFECT 时关联）',
  work_order_id       BIGINT       NULL                    COMMENT '工单 ID（可空）',
  operation_task_id   BIGINT       NULL                    COMMENT '工序任务 ID（可空）',
  inspection_task_id  BIGINT       NULL                    COMMENT '质检任务 ID（可空）',
  defect_code         VARCHAR(64)  NULL                    COMMENT '不良代码（不良生成时快照，手工创建可空）',
  description         VARCHAR(255) NOT NULL                COMMENT '异常描述',
  status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/PROCESSING/CLOSED',
  handler_id          BIGINT       NULL                    COMMENT '处理人 ID（开始处理时回填）',
  resolve_remark      VARCHAR(255) NULL                    COMMENT '处理结论（关闭时必填）',
  resolved_at         DATETIME     NULL                    COMMENT '关闭时间',
  tenant_id           BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by          BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by          BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted             TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_exception_status (tenant_id, status),
  KEY idx_exception_defect (defect_record_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '异常单';

-- ------------------------------------------------------------
-- 5. 成品 SN（最后一道工序报工完成时按合格数量批量生成，整机唯一标识）
--    SN 由 mes_sequence 批量取号（SN+日期+连续流水），生成即唯一，不建唯一索引
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_product_sn (
  id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  sn                    VARCHAR(32)  NOT NULL                COMMENT '整机序列号（SN+yyyyMMdd+4 位流水，如 SN202608230001）',
  work_order_id         BIGINT       NOT NULL                COMMENT '工单 ID（出生工单）',
  product_id            BIGINT       NOT NULL                COMMENT '产品 ID',
  product_code_snapshot VARCHAR(64)  NULL                    COMMENT '产品编码快照',
  product_name_snapshot VARCHAR(128) NULL                    COMMENT '产品名称快照',
  report_id             BIGINT       NOT NULL                COMMENT '出生报工记录 ID（最后一道工序报工）',
  remark                VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id             BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by            BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by            BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted               TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_sn (tenant_id, sn),
  KEY idx_sn_wo (work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '成品 SN';

-- ------------------------------------------------------------
-- 6. 设备主数据（第 3 周从工位拆分独立设备表）
--    状态用于看板与漂移模拟：RUNNING 运行 / IDLE 空闲 / STOPPED 停机 / MAINTENANCE 维护
--    （设备状态非严格状态机，允许任意切换，由 EquipmentSimulator 定时随机漂移）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_equipment (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  equipment_code VARCHAR(64)  NOT NULL                COMMENT '设备编码（唯一性 Service 层校验）',
  equipment_name VARCHAR(128) NOT NULL                COMMENT '设备名称',
  model          VARCHAR(64)  NULL                    COMMENT '设备型号',
  workstation_id BIGINT       NULL                    COMMENT '所属工位 ID（可空，看板按工位展示）',
  status         VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/IDLE/STOPPED/MAINTENANCE',
  remark         VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id      BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by     BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by     BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted        TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_equipment_code (tenant_id, equipment_code),
  KEY idx_equipment_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备主数据';
