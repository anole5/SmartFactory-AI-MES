-- ============================================================
-- SmartFactory-MES 第 7 周建表脚本：AI 进阶（AI 周报类型迁移）
-- 执行方式（Git Bash，勿用 PowerShell——GBK 管道会破坏 UTF-8）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/13-schema-week7.sql
--
-- 设计约定：
--  1. mes_ai_report 加 report_type（DAY 日报 / WEEK 周报）区分粒度，
--     默认 'DAY' 保证既有日报链路零改动（INSERT 不带该列仍成功）
--  2. 加唯一键前先去重（幂等：重复执行第二次无行可删）——
--     既有 save() 是 Service 层幂等 upsert，正常不会产生重复行，去重是保险
--  3. 唯一键含 tenant_id（与全库索引口径一致）：同一天日报/周报各只一条，
--     save 幂等从 Service 层提升到 DB 层兜底；系统无报表删除入口，
--     不会出现"逻辑删除行挡住唯一键"的场景
--  4. MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，依赖干净重放（DROP → 00→14）
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 去重：同 (tenant_id, report_date) 保留最小 id 行（deleted=0 条件防逻辑删除行干扰）
-- ------------------------------------------------------------
DELETE t1 FROM mes_ai_report t1
  JOIN mes_ai_report t2
    ON t1.tenant_id = t2.tenant_id AND t1.report_date = t2.report_date AND t1.id > t2.id
 WHERE t1.deleted = 0 AND t2.deleted = 0;

-- ------------------------------------------------------------
-- 2. 报表类型列：历史行统一归为 DAY 日报
-- ------------------------------------------------------------
ALTER TABLE mes_ai_report
  ADD COLUMN report_type VARCHAR(20) NOT NULL DEFAULT 'DAY' COMMENT '报表类型：DAY 日报 / WEEK 周报' AFTER report_date;

-- ------------------------------------------------------------
-- 3. (report_date, report_type) 唯一：日报/周报同日期各一条
-- ------------------------------------------------------------
ALTER TABLE mes_ai_report
  ADD UNIQUE KEY uk_report_date_type (tenant_id, report_date, report_type);
