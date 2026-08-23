-- ============================================================
-- SmartFactory-MES 第 3 周种子数据：质检角色/质量菜单/设备主数据/序列预置
-- 执行方式（Git Bash）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/06-seed-week3.sql
--
-- 新增演示账号：
--   qa / qa123  质检员（质量模块操作 + 生产查询 + 追溯/看板）
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 质检用户与角色（固定 id=4，延续 04-seed 编号）
-- ------------------------------------------------------------
INSERT INTO sys_user (id, username, password, real_name, status, remark) VALUES
  (4, 'qa', '$2a$10$NG/QnSrOz19dDSpGD5J.7OCoYILltDsk.VDdIkOiqNcXo/kn1CLc6', '王质检', 'ENABLED', '演示质检员账号');

INSERT INTO sys_role (id, role_code, role_name, status, remark) VALUES
  (4, 'INSPECTOR', '质检员', 'ENABLED', '质检任务操作与异常处理');

-- ------------------------------------------------------------
-- 2. 菜单（第 3 周新增；id 规划延续：目录 3 质量管理；基础资料 C 级 107；
--    生产管理 C 级 204/205；质量管理 C 级 301-303（按钮 4 位））
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, perm, icon, order_num, status) VALUES
  -- 质量管理目录
  (3, 0, '质量管理', 'M', NULL, NULL, 'Stamp', 30, 'ENABLED'),
  -- 基础资料：设备管理
  (107, 1, '设备管理', 'C', '/equipment', 'master:equipment:list', 'Cpu', 107, 'ENABLED'),
  (1071, 107, '设备新增', 'F', NULL, 'master:equipment:create', NULL, 1, 'ENABLED'),
  (1072, 107, '设备编辑', 'F', NULL, 'master:equipment:update', NULL, 2, 'ENABLED'),
  (1073, 107, '设备状态切换', 'F', NULL, 'master:equipment:status', NULL, 3, 'ENABLED'),
  -- 生产管理：追溯查询 / 生产看板
  (204, 2, '追溯查询', 'C', '/traces', 'production:trace:query', 'Search', 204, 'ENABLED'),
  (205, 2, '生产看板', 'C', '/dashboard', 'production:dashboard:query', 'DataAnalysis', 205, 'ENABLED'),
  -- 质量管理：质检任务 / 不良记录 / 异常管理
  (301, 3, '质检任务', 'C', '/inspection-tasks', 'quality:inspection-task:query', 'CircleCheck', 301, 'ENABLED'),
  (3011, 301, '开始检验', 'F', NULL, 'quality:inspection-task:start', NULL, 1, 'ENABLED'),
  (3012, 301, '检验录入', 'F', NULL, 'quality:inspection-record:create', NULL, 2, 'ENABLED'),
  (302, 3, '不良记录', 'C', '/defects', 'quality:defect:query', 'Warning', 302, 'ENABLED'),
  (3021, 302, '生成异常单', 'F', NULL, 'quality:defect:to-exception', NULL, 1, 'ENABLED'),
  (303, 3, '异常管理', 'C', '/exceptions', 'quality:exception:query', 'AlarmClock', 303, 'ENABLED'),
  (3031, 303, '异常新建', 'F', NULL, 'quality:exception:create', NULL, 1, 'ENABLED'),
  (3032, 303, '开始处理', 'F', NULL, 'quality:exception:process', NULL, 2, 'ENABLED'),
  (3033, 303, '关闭异常', 'F', NULL, 'quality:exception:close', NULL, 3, 'ENABLED');

-- ------------------------------------------------------------
-- 3. 用户-角色关系
-- ------------------------------------------------------------
INSERT INTO sys_user_role (user_id, role_id) VALUES
  (4, 4);   -- qa -> INSPECTOR

-- ------------------------------------------------------------
-- 4. 角色-菜单关系（新菜单对既有角色的增量授权 + 质检员全量授权）
-- ------------------------------------------------------------
-- 超级管理员：全部菜单（INSERT ... SELECT 自动覆盖新增菜单）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);

-- 操作工 / 计划员：追溯查询 + 生产看板
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (2, 204), (2, 205),
  (3, 204), (3, 205);

-- 质检员：生产模块只读 + 追溯/看板 + 质量模块全操作
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (4, 201), (4, 202), (4, 203), (4, 204), (4, 205),
  (4, 301), (4, 3011), (4, 3012),
  (4, 302), (4, 3021),
  (4, 303), (4, 3031), (4, 3032), (4, 3033);

-- ------------------------------------------------------------
-- 5. 设备主数据（id 固定 1..10，对齐 02-seed 工位绑定的 EQ-* 设备，全 RUNNING）
-- ------------------------------------------------------------
INSERT INTO mes_equipment (id, equipment_code, equipment_name, model, workstation_id, status, remark) VALUES
( 1, 'EQ-IQC-LIGHT-01',   '面板点亮检测台', 'LP-3200',    1,  'RUNNING', 'IQC 来料点亮检测'),
( 2, 'EQ-BLU-ASSY-01',    '背光装配台',     'BA-201',     2,  'RUNNING', '背光模组装配'),
( 3, 'EQ-SCREW-01',       '电批',           'DL-880',     3,  'RUNNING', '整机装配锁螺丝'),
( 4, 'EQ-BURN-01',        '软件烧录治具',   'BT-USB4',    4,  'RUNNING', '系统软件烧录与 SN 写入'),
( 5, 'EQ-FUNC-TEST-01',   '功能测试治具',   'FT-55U',     5,  'RUNNING', '遥控/Wi-Fi/HDMI/USB 功能测试'),
( 6, 'EQ-AV-TEST-01',     '画质声音测试台', 'AV-4K',      6,  'RUNNING', '灰阶色彩与声道测试'),
( 7, 'EQ-HIPOT-01',       '耐压测试仪',     'HT-5050',    7,  'RUNNING', '耐压/绝缘/泄漏电流'),
( 8, 'EQ-AGING-RACK-01',  '老化架',         'AR-120',     8,  'RUNNING', '120 分钟通电老化'),
( 9, 'EQ-OQC-01',         '外观检验台',     'VI-LED',     9,  'RUNNING', '出货前外观与性能抽检'),
(10, 'EQ-PACK-01',        '包装线',         'PK-AUTO',    10, 'RUNNING', '附件放置与装箱封箱');
ALTER TABLE mes_equipment AUTO_INCREMENT = 100;

-- ------------------------------------------------------------
-- 6. 单号序列起点对齐种子数据
--    第 3 周新增 5 种单号前缀（质检任务 INP / 质检记录 INS / 不良 DEF /
--    异常单 EXP / 整机 SN），预置当日起点 1（04-seed 同款约定）
-- ------------------------------------------------------------
INSERT INTO mes_sequence (seq_type, seq_date, current_value) VALUES
  ('INP', '20260823', 1),
  ('INS', '20260823', 1),
  ('DEF', '20260823', 1),
  ('EXP', '20260823', 1),
  ('SN',  '20260823', 1);
