-- ============================================================
-- SmartFactory-MES 冒烟测试数据清理：让数据库回到干净种子状态
-- 执行方式（Git Bash，勿用 PowerShell）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < scripts/clean-smoke.sql
-- 配合 scripts/smoke.mjs 使用。
-- 第 4 周版：种子（00→08）不含任何工单/任务/报工/追溯/质检/异常/SN/AI 记录
-- 数据，事务类数据整表清空即可；基础资料、种子用户/角色/菜单、种子设备
-- （id 1-10）、种子知识库文档（id 1-4）保留。验证脚本残留（V6-BATCH-1、
-- EQ-T7-TEST、无 external_order_no 的临时工单、【验证】知识库文档）全部覆盖。
-- ============================================================

USE smartfactory_mes;

-- 1. 质量/异常/SN（第 3 周 5 张表，子先父后）
DELETE FROM mes_defect_record;
DELETE FROM mes_exception_order;
DELETE FROM mes_inspection_record;
DELETE FROM mes_inspection_task;
DELETE FROM mes_product_sn;

-- 2. 生产执行链路（第 2 周 4 张表，子先父后）
DELETE FROM mes_work_report;
DELETE FROM mes_trace_record;
DELETE FROM mes_operation_task;
DELETE FROM mes_work_order;

-- 3. 设备：清除非种子行（种子 id 1-10，验证残留 id >= 100），
--    并把种子设备状态复位 RUNNING（漂移模拟会改状态）
DELETE FROM mes_equipment WHERE id > 10;
UPDATE mes_equipment SET status = 'RUNNING', updated_by = 0 WHERE id <= 10 AND deleted = 0;

-- 4. 测试产品 SMK-001 / T-001 及其 BOM/工艺路线（按产品编码级联清理）
DELETE s FROM mes_route_step s
  JOIN mes_route r ON s.route_id = r.id
  JOIN mes_product p ON r.product_id = p.id
  WHERE p.product_code = 'SMK-001';

DELETE r FROM mes_route r
  JOIN mes_product p ON r.product_id = p.id
  WHERE p.product_code = 'SMK-001';

DELETE i FROM mes_bom_item i
  JOIN mes_bom b ON i.bom_id = b.id
  JOIN mes_product p ON b.product_id = p.id
  WHERE p.product_code = 'SMK-001';

DELETE b FROM mes_bom b
  JOIN mes_product p ON b.product_id = p.id
  WHERE p.product_code = 'SMK-001';

DELETE FROM mes_product WHERE product_code IN ('SMK-001', 'T-001');

-- 5. 单号序列复位：BOM/RT 预留 1（与种子单号对齐），事务类前缀全部删除，
--    第 3 周 5 种前缀重置为当日起点 1（与 06-seed 对齐）
DELETE FROM mes_sequence WHERE seq_type IN ('WO', 'TASK', 'RPT', 'TRC', 'INP', 'INS', 'DEF', 'EXP', 'SN', 'ERP', 'STK');
UPDATE mes_sequence SET current_value = 1 WHERE seq_type IN ('BOM', 'RT');
INSERT INTO mes_sequence (seq_type, seq_date, current_value) VALUES
  ('INP', '20260823', 1),
  ('INS', '20260823', 1),
  ('DEF', '20260823', 1),
  ('EXP', '20260823', 1),
  ('SN',  '20260823', 1),
  ('ERP', '20260823', 1),
  ('STK', '20260823', 1);

-- 6. AI（第 4 周 3 张表：问答记录/日报为验证产生，整表清空；
--    知识库文档保留种子 4 篇，仅清验证创建的【验证】文档）
DELETE FROM mes_ai_qa_record;
DELETE FROM mes_ai_report;
DELETE FROM mes_knowledge_doc WHERE id > 4;

-- 7. 系统集成（第 5 周）：外部订单为验证产生整表清空；
--    库存复位种子 6 行初始数量（验证领料/入库会改数量），清验证新增行；
--    流水保留种子 6 条初始入库，清验证新增
DELETE FROM mes_external_order;
DELETE FROM mes_stock_transaction WHERE id > 6;
UPDATE mes_inventory SET qty = CASE item_ref_id
  WHEN 1 THEN 100 WHEN 2 THEN 100 WHEN 3 THEN 100
  WHEN 4 THEN 100 WHEN 5 THEN 100 WHEN 20 THEN 500 END
  WHERE item_type = 'MATERIAL' AND item_ref_id IN (1, 2, 3, 4, 5, 20);
DELETE FROM mes_inventory WHERE NOT (item_type = 'MATERIAL' AND item_ref_id IN (1, 2, 3, 4, 5, 20));
