-- ============================================================
-- SmartFactory-MES 第 4 周建表脚本：AI 知识库文档 + 问答记录 + AI 日报
-- 执行方式（Git Bash，勿用 PowerShell——GBK 管道会破坏 UTF-8）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/07-schema-week4.sql
--
-- 设计约定（与 01/03/05-schema 一致）：
--  1. 通用字段统一：id(BIGINT AUTO_INCREMENT) / tenant_id / created_by / created_at /
--     updated_by / updated_at / deleted(逻辑删除)
--  2. 文档内容/回答/日报等长文本用 MEDIUMTEXT（Markdown，utf8mb4）
--  3. 知识库文档启用状态 ENABLED/DISABLED（逻辑删除之外再加一层业务启停用，
--     与产品/物料同款约定）；唯一性（同日同人日报）走 Service 校验
--  4. 问答记录只增不改（反馈 useful 允许回填覆盖）
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 知识库文档（SOP/质量标准/设备手册/故障手册，Markdown 内容）
--    检索策略：keywords 关键词召回 + 按 ## 段落切分，命中段落作为 LLM 上下文
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_knowledge_doc (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  doc_name    VARCHAR(128) NOT NULL                COMMENT '文档名称',
  doc_type    VARCHAR(30)  NOT NULL                COMMENT '文档类型：SOP/QUALITY_STANDARD/EQUIPMENT_MANUAL/FAULT_GUIDE',
  keywords    VARCHAR(500) NULL                    COMMENT '检索关键词（逗号分隔，中文/代码/枚举值混排）',
  content     MEDIUMTEXT   NOT NULL                COMMENT '文档内容（Markdown，## 段落为检索粒度）',
  status      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED（停用不参与检索）',
  remark      VARCHAR(255) NULL                    COMMENT '备注',
  tenant_id   BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by  BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by  BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_knowledge_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 知识库文档';

-- ------------------------------------------------------------
-- 2. AI 问答记录（每次提问一条，只增不改；反馈 useful 覆盖回填）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_ai_qa_record (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  question     VARCHAR(500) NOT NULL                COMMENT '用户问题',
  answer       MEDIUMTEXT   NOT NULL                COMMENT 'AI 回答',
  intent       VARCHAR(30)  NULL                    COMMENT '意图：OVERVIEW/KNOWLEDGE/EXCEPTION/REPORT',
  ref_doc_ids  VARCHAR(255) NULL                    COMMENT '引用文档 ID（逗号分隔，知识问答场景）',
  useful       TINYINT      NULL                    COMMENT '反馈：1 有用 / 0 无用 / NULL 未反馈',
  tenant_id    BIGINT       NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by   BIGINT       NOT NULL DEFAULT 0      COMMENT '创建人（提问人）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by   BIGINT       NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_ai_qa_created (tenant_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 问答记录';

-- ------------------------------------------------------------
-- 3. AI 生产日报（按日期生成草稿，同日同人 Service 层幂等覆盖）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_ai_report (
  id          BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
  report_date DATE       NOT NULL                COMMENT '日报日期',
  content     MEDIUMTEXT NOT NULL                COMMENT '日报内容（Markdown）',
  remark      VARCHAR(255) NULL                  COMMENT '备注',
  tenant_id   BIGINT     NOT NULL DEFAULT 1      COMMENT '租户 ID',
  created_by  BIGINT     NOT NULL DEFAULT 0      COMMENT '创建人',
  created_at  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by  BIGINT     NOT NULL DEFAULT 0      COMMENT '更新人',
  updated_at  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT    NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_ai_report_date (tenant_id, report_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 生产日报';

-- ------------------------------------------------------------
-- 4. 异常单扩展：AI 处理建议（AI 助手生成后保存回写）
-- ------------------------------------------------------------
ALTER TABLE mes_exception_order
  ADD COLUMN ai_suggestion MEDIUMTEXT NULL COMMENT 'AI 处理建议（异常建议助手生成保存）' AFTER resolve_remark;
