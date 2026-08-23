-- ============================================================
-- SmartFactory-MES 第 5 周种子数据：系统集成菜单/角色授权 + WMS 初始库存
-- 执行方式（Git Bash）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/10-seed-week5.sql
--
-- 菜单 id 规划延续：目录 5 系统集成；C 级 501-502；按钮 4 位
-- 补 tv-demo 菜单行（id=500，C 级 parent_id=0）：前端动态路由化后
--   侧边栏完全由菜单表驱动，大屏入口必须有菜单行才不丢失
-- 权限设计：
--   系统集成（ERP 下单/转工单、WMS 入库/领料）—— admin + 计划员
--   operator / qa 不给集成菜单（动态菜单"角色差异"演示素材）
--   tv-demo —— 全部角色（perm 复用 production:dashboard:query，四角色都已有 205）
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 菜单（第 5 周新增 + tv-demo 补行）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, perm, icon, order_num, status) VALUES
  -- 系统集成目录
  (5, 0, '系统集成', 'M', NULL, NULL, 'Link', 50, 'ENABLED'),
  -- ERP 订单
  (501, 5, 'ERP 订单', 'C', '/erp-orders', 'erp:order:query', 'ShoppingCart', 501, 'ENABLED'),
  (5011, 501, '模拟下单', 'F', NULL, 'erp:order:create', NULL, 1, 'ENABLED'),
  (5012, 501, '转工单', 'F', NULL, 'erp:order:to-work-order', NULL, 2, 'ENABLED'),
  -- WMS 库存
  (502, 5, 'WMS 库存', 'C', '/inventory', 'wms:inventory:query', 'OfficeBuilding', 502, 'ENABLED'),
  (5021, 502, '采购入库', 'F', NULL, 'wms:inventory:in', NULL, 1, 'ENABLED'),
  (5022, 502, '工单领料', 'F', NULL, 'wms:pick', NULL, 2, 'ENABLED'),
  -- 电视 Demo 大屏（补行：动态路由化后侧边栏由菜单表驱动）
  (500, 0, '电视 Demo 大屏', 'C', '/tv-demo', 'production:dashboard:query', 'VideoPlay', 60, 'ENABLED');

-- ------------------------------------------------------------
-- 2. 角色-菜单关系（admin 由 INSERT ... SELECT 自动覆盖新增菜单）
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);

-- 计划员：系统集成全功能（外部订单一键转工单 + 领料入库是计划场景）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (3, 5), (3, 501), (3, 5011), (3, 5012), (3, 502), (3, 5021), (3, 5022);

-- 操作工 / 质检员：仅 tv-demo 大屏（不给集成菜单，演示动态菜单角色差异）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (2, 500), (3, 500), (4, 500);

-- ------------------------------------------------------------
-- 3. WMS 初始库存（关键物料 trace_required=1：面板/背光/T-CON/主板/电源板/SN 标签）
--    每行配一条 PURCHASE_IN 采购入库流水（初始库存入账）
-- ------------------------------------------------------------
INSERT INTO mes_inventory (id, item_type, item_ref_id, qty, remark) VALUES
  (1, 'MATERIAL', 1,  100, '55 英寸 4K LCD 面板初始库存'),
  (2, 'MATERIAL', 2,  100, 'LED 背光模组初始库存'),
  (3, 'MATERIAL', 3,  100, 'T-CON 逻辑板初始库存'),
  (4, 'MATERIAL', 4,  100, '4K 智能电视主板初始库存'),
  (5, 'MATERIAL', 5,  100, '电视电源板初始库存'),
  (6, 'MATERIAL', 20, 500, 'SN/能效/箱贴标签初始库存');

ALTER TABLE mes_inventory AUTO_INCREMENT = 100;

INSERT INTO mes_stock_transaction (id, tx_no, tx_type, item_type, item_ref_id, qty, biz_type, work_order_id, remark) VALUES
  (1, 'STK202608230001', 'IN', 'MATERIAL', 1,  100, 'PURCHASE_IN', NULL, '初始库存入账：LCD 面板'),
  (2, 'STK202608230002', 'IN', 'MATERIAL', 2,  100, 'PURCHASE_IN', NULL, '初始库存入账：背光模组'),
  (3, 'STK202608230003', 'IN', 'MATERIAL', 3,  100, 'PURCHASE_IN', NULL, '初始库存入账：T-CON 板'),
  (4, 'STK202608230004', 'IN', 'MATERIAL', 4,  100, 'PURCHASE_IN', NULL, '初始库存入账：主板'),
  (5, 'STK202608230005', 'IN', 'MATERIAL', 5,  100, 'PURCHASE_IN', NULL, '初始库存入账：电源板'),
  (6, 'STK202608230006', 'IN', 'MATERIAL', 20, 500, 'PURCHASE_IN', NULL, '初始库存入账：SN 标签');

ALTER TABLE mes_stock_transaction AUTO_INCREMENT = 100;

-- ------------------------------------------------------------
-- 4. 单号序列预置（ERP 外部订单 / STK 库存流水）
-- ------------------------------------------------------------
INSERT INTO mes_sequence (seq_type, seq_date, current_value) VALUES
  ('ERP', '20260823', 1),
  ('STK', '20260823', 1);
