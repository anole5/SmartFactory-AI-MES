-- ============================================================
-- SmartFactory-MES 第 6 周种子数据：物料批次台账 + 排程/报表菜单授权
-- 执行方式（Git Bash）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/12-seed-week6.sql
--
-- 菜单 id 规划延续：生产管理目录（2）下 C 206 生产排程 / C 207 报表中心；
-- 按钮 4 位：2041 物料批次新增（204 追溯菜单下）/ 2061 排程执行 / 2071 报表导出
-- 权限设计：
--   排程 / 报表 / 批次主数据 —— admin + 计划员（排程与报表是计划场景）
--   operator / qa 不新增菜单（批次列表复用 production:trace:query，四角色都有 204）
-- 批次种子：6 种关键物料（trace_required=1）各 2 批 = 12 行，id 固定 1-12，
--   供报工绑定 + 批次正反向追溯演示
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 物料批次台账种子（关键件各 2 批）
-- ------------------------------------------------------------
INSERT INTO mes_material_batch (id, batch_no, material_id, material_code_snapshot, material_name_snapshot, batch_qty, used_qty, in_date, supplier, remark) VALUES
( 1, 'MB202608230001', 1,  'PNL-LCD-55-4K',  '55 英寸 4K LCD 面板',     100, 0, '2026-08-20', '京东方',   '面板来料批次 A'),
( 2, 'MB202608230002', 1,  'PNL-LCD-55-4K',  '55 英寸 4K LCD 面板',     100, 0, '2026-08-22', '华星光电', '面板来料批次 B'),
( 3, 'MB202608230003', 2,  'BLU-55-LED',     '55 英寸 LED 背光模组',    100, 0, '2026-08-20', '兆驰股份', '背光模组批次 A'),
( 4, 'MB202608230004', 2,  'BLU-55-LED',     '55 英寸 LED 背光模组',    100, 0, '2026-08-22', '瑞仪光电', '背光模组批次 B'),
( 5, 'MB202608230005', 3,  'TCON-55-4K',     '55 英寸 4K T-CON 逻辑板', 100, 0, '2026-08-20', '联咏科技', 'T-CON 批次 A'),
( 6, 'MB202608230006', 3,  'TCON-55-4K',     '55 英寸 4K T-CON 逻辑板', 100, 0, '2026-08-22', '瑞鼎科技', 'T-CON 批次 B'),
( 7, 'MB202608230007', 4,  'PCBA-MAIN-4K',   '4K 智能电视主板',          100, 0, '2026-08-20', '联发科',   '主板批次 A'),
( 8, 'MB202608230008', 4,  'PCBA-MAIN-4K',   '4K 智能电视主板',          100, 0, '2026-08-22', '晨星半导体', '主板批次 B'),
( 9, 'MB202608230009', 5,  'PCBA-POWER-55',  '55 英寸电视电源板',        100, 0, '2026-08-20', '台达电子', '电源板批次 A'),
(10, 'MB202608230010', 5,  'PCBA-POWER-55',  '55 英寸电视电源板',        100, 0, '2026-08-22', '群光电子', '电源板批次 B'),
(11, 'MB202608230011', 20, 'LABEL-SN',       'SN / 能效 / 箱贴标签',     100, 0, '2026-08-20', '冠捷自产', 'SN 标签批次 A'),
(12, 'MB202608230012', 20, 'LABEL-SN',       'SN / 能效 / 箱贴标签',     100, 0, '2026-08-22', '裕同科技', 'SN 标签批次 B');

ALTER TABLE mes_material_batch AUTO_INCREMENT = 100;

-- ------------------------------------------------------------
-- 2. 菜单（第 6 周：生产排程 / 报表中心 + 追溯下批次新增按钮）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, perm, icon, order_num, status) VALUES
  (2041, 204, '物料批次新增', 'F', NULL, 'production:material-batch:create', NULL, 1, 'ENABLED'),
  (206, 2, '生产排程', 'C', '/scheduling', 'production:schedule:query', 'Histogram', 206, 'ENABLED'),
  (2061, 206, '排程执行', 'F', NULL, 'production:schedule:run', NULL, 1, 'ENABLED'),
  (207, 2, '报表中心', 'C', '/reports-center', 'production:report:center:query', 'PieChart', 207, 'ENABLED'),
  (2071, 207, '报表导出', 'F', NULL, 'production:report:export', NULL, 1, 'ENABLED');

-- ------------------------------------------------------------
-- 3. 角色-菜单关系（admin 由 INSERT ... SELECT 自动覆盖新增菜单）
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);

-- 计划员：排程 / 报表 / 批次主数据（排程与报表是计划场景）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (3, 2041), (3, 206), (3, 2061), (3, 207), (3, 2071);

-- ------------------------------------------------------------
-- 4. 单号序列预置（MB 物料批次，种子已占当日 001-012）
-- ------------------------------------------------------------
INSERT INTO mes_sequence (seq_type, seq_date, current_value) VALUES
  ('MB', '20260823', 12);
