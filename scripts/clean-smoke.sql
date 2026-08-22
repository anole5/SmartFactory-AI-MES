-- ============================================================
-- SmartFactory-MES 冒烟测试数据清理：让数据库回到干净种子状态
-- 执行方式（Git Bash，勿用 PowerShell）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < scripts/clean-smoke.sql
-- 配合 scripts/smoke.mjs 使用（冒烟数据约定：external_order_no 带 SMOKE 前缀、
-- 测试产品编码 SMK-001 / T-001）
-- ============================================================

USE smartfactory_mes;

-- 1. 生产执行链路（按 external_order_no 标记级联清理）
DELETE r FROM mes_work_report r
  JOIN mes_work_order o ON r.work_order_id = o.id
  WHERE o.external_order_no = 'SMOKE-20260823';

DELETE t FROM mes_trace_record t
  JOIN mes_work_order o ON t.work_order_id = o.id
  WHERE o.external_order_no = 'SMOKE-20260823';

DELETE k FROM mes_operation_task k
  JOIN mes_work_order o ON k.work_order_id = o.id
  WHERE o.external_order_no = 'SMOKE-20260823';

DELETE FROM mes_work_order WHERE external_order_no = 'SMOKE-20260823';

-- 2. 冒烟测试产品 SMK-001 及其 BOM/工艺路线（按产品编码级联清理）
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

DELETE FROM mes_product WHERE product_code = 'SMK-001';

-- 3. 测试产品 T-001（冒烟中途失败时的残留兜底）
DELETE FROM mes_product WHERE product_code = 'T-001';

-- 4. 单号序列复位：BOM/RT 预留 1（与种子单号对齐），其余清空
DELETE FROM mes_sequence WHERE seq_type IN ('WO', 'TASK', 'RPT', 'TRC');
UPDATE mes_sequence SET current_value = 1 WHERE seq_type IN ('BOM', 'RT');
