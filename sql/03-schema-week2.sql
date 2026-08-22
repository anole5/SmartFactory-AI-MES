-- ============================================================
-- SmartFactory-MES 第 2 周建表脚本：生产执行 5 表 + RBAC 5 表
-- 执行方式（Git Bash，勿用 PowerShell——GBK 管道会破坏 UTF-8）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/03-schema-week2.sql
--
-- 设计约定（与第 1 周 01-schema.sql 一致）：
--  1. 通用字段统一：id(BIGINT AUTO_INCREMENT) / tenant_id / created_by / created_at /
--     updated_by / updated_at / deleted(逻辑删除)
--  2. 业务单号（work_order_no/task_no/report_no/trace_no）由 mes_sequence 原子生成器
--     保证唯一（生成即唯一），不建唯一索引——规避逻辑删除 + 唯一索引冲突
--  3. 唯一索引只出现在：mes_sequence(seq_type,seq_date) 与两张 RBAC 关系表（无逻辑删除）
--  4. 数量字段用 INT（电视机台数为整数；物料用量才是 DECIMAL）
--  5. 快照字段（*_snapshot）一律服务端回填，主数据改名不影响历史单据
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 单号序列表（无逻辑删除，允许唯一索引）
--    面试点：UPDATE ... SET current_value = LAST_INSERT_ID(current_value + 1)
--    借助行锁串行并发请求，LAST_INSERT_ID() 为连接级取值，绝不串号
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_sequence (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  seq_type      VARCHAR(32) NOT NULL                COMMENT '单号类型：WO 工单 / TASK 任务 / RPT 报工 / TRC 追溯',
  seq_date      VARCHAR(8)  NOT NULL                COMMENT '业务日期 yyyyMMdd（跨天自动重新计数）',
  current_value BIGINT      NOT NULL DEFAULT 0      COMMENT '当日当前流水值',
  tenant_id     BIGINT      NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_seq_type_date (seq_type, seq_date, tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '单号序列表';

-- ------------------------------------------------------------
-- 2. 生产工单
--    状态机：DRAFT → RELEASED → IN_PROGRESS → COMPLETED → CLOSED
--            DRAFT / RELEASED / IN_PROGRESS 可 → CANCELLED
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_work_order (
  id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  work_order_no         VARCHAR(32)  NOT NULL                COMMENT '工单号（生成器生成：WO+日期+流水）',
  external_order_no     VARCHAR(64)  NULL                    COMMENT '外部订单号（手填，第 2 周不做 ERP 集成）',
  product_id            BIGINT       NOT NULL                COMMENT '产品 ID',
  product_code_snapshot VARCHAR(64)  NULL                    COMMENT '产品编码快照',
  product_name_snapshot VARCHAR(128) NULL                    COMMENT '产品名称快照',
  bom_id                BIGINT       NOT NULL                COMMENT 'BOM 头 ID（创建时自动解析生效 BOM）',
  route_id              BIGINT       NOT NULL                COMMENT '工艺路线 ID（创建时自动解析生效路线）',
  plan_qty              INT          NOT NULL                COMMENT '计划数量（台）',
  completed_qty         INT          NOT NULL DEFAULT 0      COMMENT '已完成数量（= 最后一道工序累计合格+不良）',
  good_qty              INT          NOT NULL DEFAULT 0      COMMENT '合格数量（= 最后一道工序累计合格）',
  defect_qty            INT          NOT NULL DEFAULT 0      COMMENT '不良数量',
  status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/RELEASED/IN_PROGRESS/COMPLETED/CLOSED/CANCELLED',
  priority              VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：HIGH/NORMAL/LOW',
  plan_start_time       DATETIME     NULL                    COMMENT '计划开始时间',
  plan_end_time         DATETIME     NULL                    COMMENT '计划结束时间',
  actual_start_time     DATETIME     NULL                    COMMENT '实际开工时间（首个任务开工时回填）',
  actual_end_time       DATETIME     NULL                    COMMENT '实际完工时间（工单 COMPLETED 时回填）',
  remark                VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id             BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by            BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by            BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted               TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  KEY idx_wo_no (tenant_id, work_order_no),
  KEY idx_wo_status (tenant_id, status),
  KEY idx_wo_product (tenant_id, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '生产工单';

-- ------------------------------------------------------------
-- 3. 工序任务（工单下发时按工艺路线步骤生成）
--    状态机：PENDING → ASSIGNED → RUNNING ↔ PAUSED → COMPLETED
--            PENDING / ASSIGNED 可 → CANCELLED（工单取消时级联）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_operation_task (
  id                     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_no                VARCHAR(32)   NOT NULL                COMMENT '任务号（生成器生成：TASK+日期+流水）',
  work_order_id          BIGINT        NOT NULL                COMMENT '工单 ID',
  process_id             BIGINT        NOT NULL                COMMENT '工序 ID',
  process_code_snapshot  VARCHAR(64)   NULL                    COMMENT '工序编码快照',
  process_name_snapshot  VARCHAR(128)  NULL                    COMMENT '工序名称快照',
  sequence_no            INT           NOT NULL                COMMENT '工序顺序号（1..n，照工艺路线）',
  workstation_id         BIGINT        NULL                    COMMENT '工位 ID（默认取路线步骤工位，派工可覆盖）',
  operator_id            BIGINT        NULL                    COMMENT '操作员 ID（sys_user.id，派工时分配）',
  equipment_code_snapshot VARCHAR(64)  NULL                    COMMENT '设备编码快照（来自工位绑定设备）',
  equipment_name_snapshot VARCHAR(128) NULL                    COMMENT '设备名称快照',
  plan_qty               INT           NOT NULL                COMMENT '计划数量（= 工单计划数量）',
  completed_qty          INT           NOT NULL DEFAULT 0      COMMENT '已报工数量（合格+不良）',
  good_qty               INT           NOT NULL DEFAULT 0      COMMENT '累计合格数量',
  defect_qty             INT           NOT NULL DEFAULT 0      COMMENT '累计不良数量',
  status                 VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/ASSIGNED/RUNNING/PAUSED/COMPLETED/CANCELLED',
  need_inspection        TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '本工序是否需质检（快照自路线步骤，第 3 周质检任务用）',
  standard_minutes       DECIMAL(10,2) NULL                    COMMENT '标准工时快照（分钟）',
  start_time             DATETIME      NULL                    COMMENT '实际开工时间',
  end_time               DATETIME      NULL                    COMMENT '实际完工时间（任务 COMPLETED 时回填）',
  tenant_id              BIGINT        NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by             BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by             BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted                TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_task_wo (work_order_id, sequence_no),
  KEY idx_task_status (tenant_id, status),
  KEY idx_task_ws (tenant_id, workstation_id),
  KEY idx_task_operator (tenant_id, operator_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工序任务';

-- ------------------------------------------------------------
-- 4. 报工记录（一次报工一条记录，只增不改）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_work_report (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  report_no         VARCHAR(32)  NOT NULL                COMMENT '报工单号（生成器生成：RPT+日期+流水）',
  work_order_id     BIGINT       NOT NULL                COMMENT '工单 ID',
  task_id           BIGINT       NOT NULL                COMMENT '工序任务 ID',
  operator_id       BIGINT       NOT NULL                COMMENT '报工人 ID（当前登录用户）',
  product_batch_no  VARCHAR(64)  NULL                    COMMENT '生产批次号（第 2 周不做 SN 绑定）',
  report_qty        INT          NOT NULL                COMMENT '报工数量（= 合格 + 不良）',
  good_qty          INT          NOT NULL                COMMENT '合格数量',
  defect_qty        INT          NOT NULL                COMMENT '不良数量',
  start_time        DATETIME     NULL                    COMMENT '本批次开始时间（默认当前）',
  end_time          DATETIME     NULL                    COMMENT '本批次结束时间（默认当前）',
  remark            VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id         BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by        BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by        BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted           TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_report_wo (work_order_id),
  KEY idx_report_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '报工记录';

-- ------------------------------------------------------------
-- 5. 生产追溯记录（第 2 周写入：下发/派工/开工/暂停/继续/报工/取消，第 3 周做查询）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_trace_record (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  trace_no      VARCHAR(32)   NOT NULL                COMMENT '追溯单号（生成器生成：TRC+日期+流水）',
  work_order_id BIGINT        NOT NULL                COMMENT '工单 ID',
  task_id       BIGINT        NULL                    COMMENT '工序任务 ID（工单级动作可为空）',
  action_type   VARCHAR(20)   NOT NULL                COMMENT '动作：CREATE/RELEASE/ASSIGN/START/PAUSE/RESUME/REPORT/CANCEL',
  action_time   DATETIME      NOT NULL                COMMENT '动作时间',
  operator_id   BIGINT        NOT NULL                COMMENT '操作人 ID（当前登录用户）',
  action_detail VARCHAR(1000) NULL                    COMMENT '动作明细 JSON，如 {"taskCount":13}',
  tenant_id     BIGINT        NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by    BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by    BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_trace_wo (work_order_id, action_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '生产追溯记录';

-- ------------------------------------------------------------
-- 6. 系统用户（RBAC，RuoYi 风格）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username   VARCHAR(64)  NOT NULL                COMMENT '登录账号',
  password   VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 哈希）',
  real_name  VARCHAR(64)  NULL                    COMMENT '真实姓名/昵称',
  status     VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
  remark     VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id  BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_username (tenant_id, username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户';

-- ------------------------------------------------------------
-- 7. 角色
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_code  VARCHAR(64)  NOT NULL                COMMENT '角色编码（如 SUPER_ADMIN/OPERATOR）',
  role_name  VARCHAR(64)  NOT NULL                COMMENT '角色名称',
  status     VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
  remark     VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id  BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_role_code (tenant_id, role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色';

-- ------------------------------------------------------------
-- 8. 菜单（目录/菜单/按钮三级，perm 为权限标识，第 3/4 周接前端动态路由）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  parent_id  BIGINT       NOT NULL DEFAULT 0      COMMENT '父菜单 ID（0 = 根）',
  menu_name  VARCHAR(64)  NOT NULL                COMMENT '菜单名称',
  menu_type  VARCHAR(10)  NOT NULL DEFAULT 'C'    COMMENT '类型：M 目录 / C 菜单 / F 按钮',
  path       VARCHAR(128) NULL                    COMMENT '前端路由路径（C 级）',
  perm       VARCHAR(128) NULL                    COMMENT '权限标识，如 production:work-order:release',
  icon       VARCHAR(64)  NULL                    COMMENT '图标',
  order_num  INT          NOT NULL DEFAULT 0      COMMENT '排序号',
  status     VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
  tenant_id  BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_menu_parent (parent_id, order_num)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单权限';

-- ------------------------------------------------------------
-- 9. 用户-角色关系（纯关系表：无逻辑删除，唯一键防重）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
  id        BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id   BIGINT NOT NULL                COMMENT '用户 ID',
  role_id   BIGINT NOT NULL                COMMENT '角色 ID',
  tenant_id BIGINT NOT NULL DEFAULT 1      COMMENT '租户 ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户-角色关系';

-- ------------------------------------------------------------
-- 10. 角色-菜单关系（纯关系表：无逻辑删除，唯一键防重）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role_menu (
  id        BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id   BIGINT NOT NULL                COMMENT '角色 ID',
  menu_id   BIGINT NOT NULL                COMMENT '菜单 ID',
  tenant_id BIGINT NOT NULL DEFAULT 1      COMMENT '租户 ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-菜单关系';
