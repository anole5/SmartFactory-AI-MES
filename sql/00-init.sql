-- ============================================================
-- SmartFactory-MES 数据库初始化（用 root 执行一次）
-- 执行方式（Git Bash，勿用 PowerShell）：
--   docker exec -i mysql mysql -uroot -pAtguigu.123 --default-character-set=utf8mb4 < sql/00-init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS smartfactory_mes
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

-- 应用专用账号（与尚硅谷课程账号 atguigu 隔离，互不影响）
CREATE USER IF NOT EXISTS 'smartfactory'@'%' IDENTIFIED BY 'Smartfactory@123';
GRANT ALL PRIVILEGES ON smartfactory_mes.* TO 'smartfactory'@'%';
FLUSH PRIVILEGES;
