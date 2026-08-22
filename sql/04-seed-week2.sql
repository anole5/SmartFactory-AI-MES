-- ============================================================
-- SmartFactory-MES 第 2 周种子数据：RBAC 用户/角色/菜单/关系
-- 执行方式（Git Bash）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/04-seed-week2.sql
--
-- 演示账号（密码为 BCrypt 哈希）：
--   admin / admin123       系统管理员（全部权限）
--   operator / operator123 操作工（工单/任务查询 + 派工/开工/暂停/继续 + 报工）
--   planning / planning123 计划员（工单全操作 + 基础资料查询）
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 用户（固定 id，便于关系表与演示脚本引用）
-- ------------------------------------------------------------
INSERT INTO sys_user (id, username, password, real_name, status, remark) VALUES
  (1, 'admin',    '$2a$10$WN.BVVYKCcf0Mf9UK.1nBeolIisjgMVMkdfkeTw8qn.XJxK5A5846', '系统管理员', 'ENABLED', '演示管理员账号'),
  (2, 'operator', '$2a$10$CfbJkVsXDhYFZ4A8mQ84JevrH5LAJa9R5CBGbWx74jh7sGYaHm0Ri', '张操作',     'ENABLED', '演示操作工账号'),
  (3, 'planning', '$2a$10$iAHQ7b4DzGRq814mpe5.muhjXGEuimzzH.pO7L7r1Wukk5bkZsCIe', '李计划',     'ENABLED', '演示计划员账号');

-- ------------------------------------------------------------
-- 2. 角色
-- ------------------------------------------------------------
INSERT INTO sys_role (id, role_code, role_name, status, remark) VALUES
  (1, 'SUPER_ADMIN', '超级管理员', 'ENABLED', '全部菜单与按钮权限'),
  (2, 'OPERATOR',    '操作工',     'ENABLED', '工序任务操作与报工'),
  (3, 'PLANNING',    '计划员',     'ENABLED', '工单创建下发与进度跟踪');

-- ------------------------------------------------------------
-- 3. 菜单（M 目录 / C 菜单 / F 按钮；perm 为权限标识，第 3/4 周接前端动态路由）
--    id 规划：目录 1/2；基础资料 C 级 101-106（按钮 4 位）；生产管理 C 级 201-203
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, perm, icon, order_num, status) VALUES
  -- 目录
  (1, 0, '基础资料', 'M', NULL, NULL, 'Box', 10, 'ENABLED'),
  (2, 0, '生产管理', 'M', NULL, NULL, 'Operation', 20, 'ENABLED'),
  -- 基础资料菜单（C）与按钮（F）
  (101, 1, '产品管理',   'C', '/products',     'master:product:list',     'Goods',       101, 'ENABLED'),
  (1011, 101, '产品新增', 'F', NULL, 'master:product:create', NULL, 1, 'ENABLED'),
  (1012, 101, '产品编辑', 'F', NULL, 'master:product:update', NULL, 2, 'ENABLED'),
  (1013, 101, '产品删除', 'F', NULL, 'master:product:delete', NULL, 3, 'ENABLED'),
  (1014, 101, '产品启停用', 'F', NULL, 'master:product:status', NULL, 4, 'ENABLED'),
  (102, 1, '物料管理',   'C', '/materials',    'master:material:list',    'Files',       102, 'ENABLED'),
  (1021, 102, '物料新增', 'F', NULL, 'master:material:create', NULL, 1, 'ENABLED'),
  (1022, 102, '物料编辑', 'F', NULL, 'master:material:update', NULL, 2, 'ENABLED'),
  (1023, 102, '物料删除', 'F', NULL, 'master:material:delete', NULL, 3, 'ENABLED'),
  (1024, 102, '物料启停用', 'F', NULL, 'master:material:status', NULL, 4, 'ENABLED'),
  (103, 1, '工序管理',   'C', '/processes',    'master:process:list',     'SetUp',       103, 'ENABLED'),
  (1031, 103, '工序新增', 'F', NULL, 'master:process:create', NULL, 1, 'ENABLED'),
  (1032, 103, '工序编辑', 'F', NULL, 'master:process:update', NULL, 2, 'ENABLED'),
  (1033, 103, '工序删除', 'F', NULL, 'master:process:delete', NULL, 3, 'ENABLED'),
  (104, 1, '工位管理',   'C', '/workstations', 'master:workstation:list', 'Monitor',     104, 'ENABLED'),
  (1041, 104, '工位新增', 'F', NULL, 'master:workstation:create', NULL, 1, 'ENABLED'),
  (1042, 104, '工位编辑', 'F', NULL, 'master:workstation:update', NULL, 2, 'ENABLED'),
  (1043, 104, '工位删除', 'F', NULL, 'master:workstation:delete', NULL, 3, 'ENABLED'),
  (1044, 104, '工位启停用', 'F', NULL, 'master:workstation:status', NULL, 4, 'ENABLED'),
  (105, 1, 'BOM 管理',   'C', '/boms',         'master:bom:list',         'Tickets',     105, 'ENABLED'),
  (1051, 105, 'BOM 新增', 'F', NULL, 'master:bom:create', NULL, 1, 'ENABLED'),
  (1052, 105, 'BOM 编辑', 'F', NULL, 'master:bom:update', NULL, 2, 'ENABLED'),
  (1053, 105, 'BOM 删除', 'F', NULL, 'master:bom:delete', NULL, 3, 'ENABLED'),
  (1054, 105, 'BOM 状态流转', 'F', NULL, 'master:bom:status', NULL, 4, 'ENABLED'),
  (106, 1, '工艺路线',   'C', '/routes',       'master:route:list',       'Connection',  106, 'ENABLED'),
  (1061, 106, '路线新增', 'F', NULL, 'master:route:create', NULL, 1, 'ENABLED'),
  (1062, 106, '路线编辑', 'F', NULL, 'master:route:update', NULL, 2, 'ENABLED'),
  (1063, 106, '路线删除', 'F', NULL, 'master:route:delete', NULL, 3, 'ENABLED'),
  (1064, 106, '路线状态流转', 'F', NULL, 'master:route:status', NULL, 4, 'ENABLED'),
  -- 生产管理菜单（C）与按钮（F）
  (201, 2, '工单管理',   'C', '/work-orders',  'production:work-order:query',  'Document', 201, 'ENABLED'),
  (2011, 201, '工单新增', 'F', NULL, 'production:work-order:create', NULL, 1, 'ENABLED'),
  (2012, 201, '工单编辑', 'F', NULL, 'production:work-order:update', NULL, 2, 'ENABLED'),
  (2013, 201, '工单下发', 'F', NULL, 'production:work-order:release', NULL, 3, 'ENABLED'),
  (2014, 201, '工单取消', 'F', NULL, 'production:work-order:cancel', NULL, 4, 'ENABLED'),
  (202, 2, '工序任务',   'C', '/tasks',        'production:task:query',        'List',     202, 'ENABLED'),
  (2021, 202, '派工',     'F', NULL, 'production:task:assign', NULL, 1, 'ENABLED'),
  (2022, 202, '开工',     'F', NULL, 'production:task:start',  NULL, 2, 'ENABLED'),
  (2023, 202, '暂停',     'F', NULL, 'production:task:pause',  NULL, 3, 'ENABLED'),
  (2024, 202, '继续',     'F', NULL, 'production:task:resume', NULL, 4, 'ENABLED'),
  (2025, 202, '任务取消', 'F', NULL, 'production:task:cancel', NULL, 5, 'ENABLED'),
  (203, 2, '报工记录',   'C', '/reports',      'production:report:query',      'DataLine', 203, 'ENABLED'),
  (2031, 203, '报工',     'F', NULL, 'production:report:create', NULL, 1, 'ENABLED');

-- ------------------------------------------------------------
-- 4. 用户-角色关系
-- ------------------------------------------------------------
INSERT INTO sys_user_role (user_id, role_id) VALUES
  (1, 1),   -- admin -> SUPER_ADMIN
  (2, 2),   -- operator -> OPERATOR
  (3, 3);   -- planning -> PLANNING

-- ------------------------------------------------------------
-- 5. 角色-菜单关系（INSERT ... SELECT 批量挂，一行给管理员全量）
-- ------------------------------------------------------------
-- 超级管理员：全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 操作工：工单查询 + 任务查询与全部任务操作 + 报工查询与报工
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (2, 201), (2, 202), (2, 2021), (2, 2022), (2, 2023), (2, 2024), (2, 203), (2, 2031);

-- 计划员：工单全操作 + 任务/报工查询 + 基础资料菜单（只读）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (3, 101), (3, 102), (3, 103), (3, 104), (3, 105), (3, 106),
  (3, 201), (3, 2011), (3, 2012), (3, 2013), (3, 2014),
  (3, 202), (3, 203);

-- ------------------------------------------------------------
-- 6. 单号序列起点对齐种子数据
--    种子 BOM/RT 单号（BOM202608230001 / RT202608230001）已占用当日 001，
--    序列表预留到 1，避免单号生成器对种子产品发出重复号
-- ------------------------------------------------------------
INSERT INTO mes_sequence (seq_type, seq_date, current_value) VALUES
  ('BOM', '20260823', 1),
  ('RT',  '20260823', 1);
