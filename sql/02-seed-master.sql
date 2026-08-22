-- ============================================================
-- SmartFactory-MES 第 1 周种子数据：AOC 55 英寸 4K 智能电视 Demo
-- 执行方式（Git Bash）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/02-seed-master.sql
-- 数据来源：Obsidian 冠捷科技资料收集/06-MES系统落地映射/02-电视制造Demo数据草稿.md
-- 说明：主产品 TV-AOC-55U4K-001 状态 ENABLED、BOM/路线 ACTIVE，
--       保证"产品启用才能维护 BOM/工艺路线"的校验规则与种子数据自洽。
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 产品（id 固定 1..3）
-- ------------------------------------------------------------
INSERT INTO mes_product (id, product_code, product_name, product_type, specification, unit, status) VALUES
(1, 'TV-AOC-55U4K-001',      'AOC 55 英寸 4K 智能电视 Demo',     '智能电视', '55 英寸 4K UHD', '台', 'ENABLED'),
(2, 'TV-PHILIPS-65MLED-001', 'Philips 65 英寸 MiniLED 智能电视 Demo', '智能电视', '65 英寸 MiniLED', '台', 'DISABLED'),
(3, 'MON-AOC-27G-001',       'AOC 27 英寸电竞显示器 Demo',        '显示器',   '27 英寸 165Hz',  '台', 'DISABLED');
ALTER TABLE mes_product AUTO_INCREMENT = 100;

-- ------------------------------------------------------------
-- 2. 物料（id 固定 1..19）
-- ------------------------------------------------------------
INSERT INTO mes_material (id, material_code, material_name, material_type, unit, trace_required, status) VALUES
( 1, 'PNL-LCD-55-4K',        '55 英寸 4K LCD 面板',        '核心件', '片', 1, 'ENABLED'),
( 2, 'BLU-55-LED',           '55 英寸 LED 背光模组',       '核心件', '套', 1, 'ENABLED'),
( 3, 'TCON-55-4K',           '55 英寸 4K T-CON 逻辑板',    '板卡',   '块', 1, 'ENABLED'),
( 4, 'PCBA-MAIN-4K',         '4K 智能电视主板',             '板卡',   '块', 1, 'ENABLED'),
( 5, 'PCBA-POWER-55',        '55 英寸电视电源板',           '板卡',   '块', 1, 'ENABLED'),
( 6, 'IR-KEY-BOARD',         '遥控接收 / 按键板',           '板卡',   '块', 0, 'ENABLED'),
( 7, 'SPK-10W-L',            '左喇叭',                      '音频件', '个', 0, 'ENABLED'),
( 8, 'SPK-10W-R',            '右喇叭',                      '音频件', '个', 0, 'ENABLED'),
( 9, 'FRAME-FRONT-55',       '55 英寸前框',                 '结构件', '件', 0, 'ENABLED'),
(10, 'COVER-BACK-55',        '55 英寸后盖',                 '结构件', '件', 0, 'ENABLED'),
(11, 'STAND-55',             '55 英寸底座组件',              '结构件', '套', 0, 'ENABLED'),
(12, 'LVDS-CABLE-55',        '55 英寸 LVDS / eDP 屏线',     '线材',   '条', 0, 'ENABLED'),
(13, 'POWER-HARNESS-55',     '电源连接线束',                '线材',   '条', 0, 'ENABLED'),
(14, 'BACKLIGHT-HARNESS-55', '背光连接线束',                '线材',   '条', 0, 'ENABLED'),
(15, 'REMOTE-AOC-TV',        'AOC 电视遥控器',              '附件',   '个', 0, 'ENABLED'),
(16, 'BATTERY-AAA',          'AAA 电池',                    '附件',   '节', 0, 'ENABLED'),
(17, 'MANUAL-TV',            '说明书 / 保修卡',             '附件',   '套', 0, 'ENABLED'),
(18, 'CARTON-55',            '55 英寸电视外箱',             '包材',   '个', 0, 'ENABLED'),
(19, 'FOAM-55',              '55 英寸泡沫缓冲材料',         '包材',   '套', 0, 'ENABLED'),
(20, 'LABEL-SN',             'SN / 能效 / 箱贴标签',        '包材',   '套', 1, 'ENABLED');
ALTER TABLE mes_material AUTO_INCREMENT = 200;

-- ------------------------------------------------------------
-- 3. 工序（id 固定 1..13，按电视工艺主线顺序）
-- ------------------------------------------------------------
INSERT INTO mes_process (id, process_code, process_name, need_inspection, standard_minutes, description) VALUES
( 1, 'IQC',         '来料检验',         1,  10, '关键物料来料检验：面板、主板、电源板'),
( 2, 'BLU_ASSY',    '背光模组装配',     1,   8, '背光模组组装与点亮检查'),
( 3, 'PANEL_ASSY',  '面板合装',         1,   6, 'LCD 面板与背光模组合装'),
( 4, 'MAIN_ASSY',   '主板 / 电源板装配', 0,  8, '主板、电源板、T-CON 固定与接线'),
( 5, 'WIRE_ASSY',   '线束插接与整理',   0,   5, 'LVDS/电源/背光线束插接与走线整理'),
( 6, 'COVER_ASSY',  '后盖 / 底座装配',  0,   6, '后盖、底座、喇叭安装'),
( 7, 'SW_BURN',     '软件烧录 / SN 绑定', 1, 5, '电视系统软件烧录、SN/MAC 写入'),
( 8, 'FUNC_TEST',   '功能测试',         1,   8, '遥控、Wi-Fi、HDMI、USB 等功能测试'),
( 9, 'AV_TEST',     '画质与声音测试',   1,   6, '灰阶、色彩、亮度与左右声道测试'),
(10, 'SAFETY_TEST', '安规测试',         1,   4, '耐压、绝缘、泄漏电流测试'),
(11, 'AGING',       '老化测试',         1, 120, '长时间通电老化，模拟使用环境'),
(12, 'OQC',         '最终检验',         1,   6, '出货前外观与性能抽检'),
(13, 'PACKING',     '包装',             0,   5, '附件放置、装箱、封箱与贴标');
ALTER TABLE mes_process AUTO_INCREMENT = 100;

-- ------------------------------------------------------------
-- 4. 工位（id 固定 1..10）
-- ------------------------------------------------------------
INSERT INTO mes_workstation (id, workstation_code, workstation_name, equipment_code, equipment_name, status) VALUES
( 1, 'WS-IQC-01',   'IQC 检验工位',       'EQ-IQC-LIGHT-01',   '面板点亮检测台', 'ENABLED'),
( 2, 'WS-MOD-01',   '显示模组装配工位',   'EQ-BLU-ASSY-01',    '背光装配台',     'ENABLED'),
( 3, 'WS-ASSY-01',  '整机装配工位',       'EQ-SCREW-01',       '电批',           'ENABLED'),
( 4, 'WS-SW-01',    '软件烧录工位',       'EQ-BURN-01',        '软件烧录治具',   'ENABLED'),
( 5, 'WS-TEST-01',  '功能测试工位',       'EQ-FUNC-TEST-01',   '功能测试治具',   'ENABLED'),
( 6, 'WS-AV-01',    '音视频测试工位',     'EQ-AV-TEST-01',     '画质声音测试台', 'ENABLED'),
( 7, 'WS-SAFE-01',  '安规测试工位',       'EQ-HIPOT-01',       '耐压测试仪',     'ENABLED'),
( 8, 'WS-AGING-01', '老化测试区',         'EQ-AGING-RACK-01',  '老化架',         'ENABLED'),
( 9, 'WS-OQC-01',   'OQC 检验工位',       'EQ-OQC-01',         '外观检验台',     'ENABLED'),
(10, 'WS-PACK-01',  '包装工位',           'EQ-PACK-01',        '包装线',         'ENABLED');
ALTER TABLE mes_workstation AUTO_INCREMENT = 100;

-- ------------------------------------------------------------
-- 5. BOM：AOC 55 电视（id=1，ACTIVE，19 行明细）
-- ------------------------------------------------------------
INSERT INTO mes_bom (id, bom_no, product_id, version, status, effective_date, remark) VALUES
(1, 'BOM202608230001', 1, 'V1', 'ACTIVE', '2026-08-23', 'AOC 55 英寸 4K 智能电视标准 BOM');
ALTER TABLE mes_bom AUTO_INCREMENT = 100;

INSERT INTO mes_bom_item (id, bom_id, line_no, material_id, material_code_snapshot, material_name_snapshot, unit_snapshot, required_qty, loss_rate, remark) VALUES
( 1, 1,  1,  1, 'PNL-LCD-55-4K',        '55 英寸 4K LCD 面板',        '片', 1, 0, '核心显示部件'),
( 2, 1,  2,  2, 'BLU-55-LED',           '55 英寸 LED 背光模组',       '套', 1, 0, '背光'),
( 3, 1,  3,  3, 'TCON-55-4K',           '55 英寸 4K T-CON 逻辑板',    '块', 1, 0, '逻辑板'),
( 4, 1,  4,  4, 'PCBA-MAIN-4K',         '4K 智能电视主板',             '块', 1, 0, '主板'),
( 5, 1,  5,  5, 'PCBA-POWER-55',        '55 英寸电视电源板',           '块', 1, 0, '电源'),
( 6, 1,  6,  6, 'IR-KEY-BOARD',         '遥控接收 / 按键板',           '块', 1, 0, '按键遥控'),
( 7, 1,  7,  7, 'SPK-10W-L',            '左喇叭',                      '个', 1, 0, '左喇叭'),
( 8, 1,  8,  8, 'SPK-10W-R',            '右喇叭',                      '个', 1, 0, '右喇叭'),
( 9, 1,  9,  9, 'FRAME-FRONT-55',       '55 英寸前框',                 '件', 1, 0, '前框'),
(10, 1, 10, 10, 'COVER-BACK-55',        '55 英寸后盖',                 '件', 1, 0, '后盖'),
(11, 1, 11, 11, 'STAND-55',             '55 英寸底座组件',              '套', 1, 0, '底座'),
(12, 1, 12, 12, 'LVDS-CABLE-55',        '55 英寸 LVDS / eDP 屏线',     '条', 1, 0, '屏线'),
(13, 1, 13, 13, 'POWER-HARNESS-55',     '电源连接线束',                '条', 1, 0, '电源线束'),
(14, 1, 14, 14, 'BACKLIGHT-HARNESS-55', '背光连接线束',                '条', 1, 0, '背光线束'),
(15, 1, 15, 15, 'REMOTE-AOC-TV',        'AOC 电视遥控器',              '个', 1, 0, '遥控器'),
(16, 1, 16, 16, 'BATTERY-AAA',          'AAA 电池',                    '节', 2, 0, '遥控器电池'),
(17, 1, 17, 17, 'MANUAL-TV',            '说明书 / 保修卡',             '套', 1, 0, '说明书'),
(18, 1, 18, 18, 'CARTON-55',            '55 英寸电视外箱',             '个', 1, 0, '外箱'),
(19, 1, 19, 19, 'FOAM-55',              '55 英寸泡沫缓冲材料',         '套', 1, 0, '泡沫'),
(20, 1, 20, 20, 'LABEL-SN',             'SN / 能效 / 箱贴标签',        '套', 1, 0, '标签');
ALTER TABLE mes_bom_item AUTO_INCREMENT = 100;

-- ------------------------------------------------------------
-- 6. 工艺路线：AOC 55 电视（id=1，ACTIVE，13 步）
--    工序顺序 = 演示脚本的电视工艺主线
-- ------------------------------------------------------------
INSERT INTO mes_route (id, route_no, product_id, version, status, remark) VALUES
(1, 'RT202608230001', 1, 'V1', 'ACTIVE', 'AOC 55 英寸 4K 智能电视标准工艺路线');
ALTER TABLE mes_route AUTO_INCREMENT = 100;

INSERT INTO mes_route_step (id, route_id, sequence_no, process_id, process_code_snapshot, process_name_snapshot, workstation_id, need_inspection, standard_minutes, remark) VALUES
( 1, 1,  1,  1, 'IQC',         '来料检验',             1, 1,  10, NULL),
( 2, 1,  2,  2, 'BLU_ASSY',    '背光模组装配',         2, 1,   8, NULL),
( 3, 1,  3,  3, 'PANEL_ASSY',  '面板合装',             2, 1,   6, NULL),
( 4, 1,  4,  4, 'MAIN_ASSY',   '主板 / 电源板装配',    3, 0,   8, NULL),
( 5, 1,  5,  5, 'WIRE_ASSY',   '线束插接与整理',       3, 0,   5, NULL),
( 6, 1,  6,  6, 'COVER_ASSY',  '后盖 / 底座装配',      3, 0,   6, NULL),
( 7, 1,  7,  7, 'SW_BURN',     '软件烧录 / SN 绑定',   4, 1,   5, NULL),
( 8, 1,  8,  8, 'FUNC_TEST',   '功能测试',             5, 1,   8, NULL),
( 9, 1,  9,  9, 'AV_TEST',     '画质与声音测试',       6, 1,   6, NULL),
(10, 1, 10, 10, 'SAFETY_TEST', '安规测试',             7, 1,   4, NULL),
(11, 1, 11, 11, 'AGING',       '老化测试',             8, 1, 120, NULL),
(12, 1, 12, 12, 'OQC',         '最终检验',             9, 1,   6, NULL),
(13, 1, 13, 13, 'PACKING',     '包装',                10, 0,   5, NULL);
ALTER TABLE mes_route_step AUTO_INCREMENT = 100;
