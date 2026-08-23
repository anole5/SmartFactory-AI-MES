-- ============================================================
-- SmartFactory-MES 第 4 周种子数据：AI 菜单/角色授权 + 知识库预置文档
-- 执行方式（Git Bash）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 smartfactory_mes < sql/08-seed-week4.sql
--
-- 预置 4 篇电视场景知识文档（演示脚本推荐 Demo 数据同名）：
--   电视软件烧录作业指导书 / 功能测试规范 / 老化测试规范 / 黑屏故障排查手册
-- 菜单：目录 4 AI 应用（AI 助手/工厂知识库/异常建议助手/生产日报助手）
-- ============================================================

USE smartfactory_mes;

-- ------------------------------------------------------------
-- 1. 菜单（第 4 周新增；id 规划延续：目录 4 AI 应用；C 级 401-404；按钮 4 位）
--    权限设计：
--      AI 四页查询/问答/生成 —— 全部角色（工人查 SOP 是核心场景）
--      知识库文档写（4021/4022）—— 仅 admin
--      建议保存回写异常单（4032）—— admin + 质检员（质量处置）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, perm, icon, order_num, status) VALUES
  -- AI 应用目录
  (4, 0, 'AI 应用', 'M', NULL, NULL, 'MagicStick', 40, 'ENABLED'),
  -- AI 助手（统一对话入口）
  (401, 4, 'AI 助手', 'C', '/ai-chat', 'ai:chat:query', 'ChatDotRound', 401, 'ENABLED'),
  -- 工厂知识库
  (402, 4, '工厂知识库', 'C', '/knowledge', 'ai:knowledge:query', 'Collection', 402, 'ENABLED'),
  (4021, 402, '文档新增', 'F', NULL, 'ai:knowledge:create', NULL, 1, 'ENABLED'),
  (4022, 402, '文档编辑', 'F', NULL, 'ai:knowledge:update', NULL, 2, 'ENABLED'),
  -- 异常建议助手
  (403, 4, '异常建议助手', 'C', '/ai-assistant', 'ai:assistant:query', 'Opportunity', 403, 'ENABLED'),
  (4031, 403, '生成建议', 'F', NULL, 'ai:assistant:generate', NULL, 1, 'ENABLED'),
  (4032, 403, '保存建议', 'F', NULL, 'ai:assistant:save', NULL, 2, 'ENABLED'),
  -- 生产日报助手
  (404, 4, '生产日报助手', 'C', '/ai-daily', 'ai:daily:query', 'Document', 404, 'ENABLED'),
  (4041, 404, '生成日报', 'F', NULL, 'ai:daily:generate', NULL, 1, 'ENABLED'),
  (4042, 404, '保存日报', 'F', NULL, 'ai:daily:save', NULL, 2, 'ENABLED');

-- ------------------------------------------------------------
-- 2. 角色-菜单关系（admin 由 INSERT ... SELECT 自动覆盖新增菜单）
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);

-- 操作工 / 计划员：AI 四页可用（问答/生成建议/日报生成保存；无知识库写、无建议保存）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (2, 401), (2, 402), (2, 403), (2, 4031), (2, 404), (2, 4041), (2, 4042),
  (3, 401), (3, 402), (3, 403), (3, 4031), (3, 404), (3, 4041), (3, 4042);

-- 质检员：AI 四页 + 建议保存（回写异常单属质量处置）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (4, 401), (4, 402), (4, 403), (4, 4031), (4, 4032), (4, 404), (4, 4041), (4, 4042);

-- ------------------------------------------------------------
-- 3. 知识库预置文档（id 固定 1..4，AUTO_INCREMENT=100 留测试空间）
--    content 按 ## 段落组织，段落即检索粒度；keywords 覆盖中文叫法/工序代码/不良枚举
-- ------------------------------------------------------------
INSERT INTO mes_knowledge_doc (id, doc_name, doc_type, keywords, content, status, remark) VALUES
(1, '电视软件烧录作业指导书', 'SOP', '烧录,软件烧录,固件,写码,刷机,SW_BURN,BURN_FAIL', '## 适用工序
本 SOP 适用于 55 英寸智能电视软件烧录工序（SW_BURN），设备为软件烧录治具 BT-USB4。

## 操作步骤
1. 扫描工单与整机 SN 条码，确认与系统工单一致。
2. 连接 USB 烧录线与电源，长按遥控器「菜单+音量-」进入刷机模式。
3. 烧录治具自动写入系统固件并校验，进度条达 100% 后绿色指示灯亮。
4. 断开烧录线，执行开机自检：LOGO 显示、SN 写入正确、出厂设置恢复。

## 注意事项
- 烧录过程中严禁断电或拔插 USB 线，否则需重新烧录。
- 烧录失败（BURN_FAIL）时先重试一次，仍失败标记不良并隔离整机。

## 质量判定
烧录成功且开机自检通过方可流转下一工序；SN 与工单不一致视为烧录失败。', 'ENABLED', '种子文档：演示脚本推荐 SOP'),

(2, '功能测试规范', 'QUALITY_STANDARD', '功能测试,FUNC_TEST,HDMI,声音,遥控,按键,Wi-Fi,HDMI_ABNORMAL,NO_SOUND', '## 测试范围
本规范适用于功能测试工序（FUNC_TEST），设备为功能测试治具 FT-55U。

## 测试项目与判定标准
- 遥控接收：所有按键响应正常，无串键。
- Wi-Fi 连接：2.4G/5G 均可连接并取得 IP。
- HDMI 输入：三个 HDMI 口均能显示 4K@60Hz 信号（HDMI_ABNORMAL 判不良）。
- USB 播放：U 盘视频播放流畅，无卡顿花屏。
- 声音输出：左右声道正常，无杂音无声（NO_SOUND 判不良）。

## 不良处理
任何一项测试不通过即整机判不良，记录不良代码并隔离，禁止流转下一工序。', 'ENABLED', '种子文档：演示脚本推荐规范'),

(3, '老化测试规范', 'SOP', '老化,AGING,老化重启,温度,时长,电流,AGING_RESTART', '## 老化条件
本规范适用于老化测试工序（AGING），设备为老化架 AR-120，单机老化时长 120 分钟。

## 测试内容
- 通电老化期间循环播放 4K 测试视频，每 30 分钟自动切换信号源。
- 观察整机在高温老化房环境下是否出现自动重启（AGING_RESTART 判不良）。
- 老化结束自动记录电流曲线，波动超 10% 判不良。

## 通过标准
120 分钟无重启、无黑屏花屏、电流曲线稳定即为通过。', 'ENABLED', '种子文档：演示脚本推荐规范'),

(4, '黑屏故障排查手册', 'FAULT_GUIDE', '黑屏,花屏,BLACK_SCREEN,FLOWER_SCREEN,背光,电源,排线,T-CON', '## 故障现象
整机开机后屏幕全黑无画面（BLACK_SCREEN），指示灯状态可能是常亮或闪烁。

## 可能原因
1. 电源板无输出或电压不足。
2. 背光驱动损坏或背光条老化。
3. 主板开机信号异常，固件启动失败。
4. 屏幕排线松动或断裂。

## 排查步骤
1. 测量电源板 12V/5V 输出是否正常。
2. 用手电筒斜照屏幕，若能看到隐约画面则为背光故障。
3. 重新插拔屏幕排线并检查接口氧化。
4. 重新烧录固件，排除固件损坏。
5. 仍无法定位时更换主板交叉验证。

## 花屏处理（FLOWER_SCREEN）
花屏多与排线接触不良或 T-CON 板故障有关：先重插排线，再更换 T-CON 板验证。', 'ENABLED', '种子文档：演示脚本推荐手册');

ALTER TABLE mes_knowledge_doc AUTO_INCREMENT = 100;
