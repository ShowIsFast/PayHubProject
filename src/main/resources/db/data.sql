-- 演示商户：跑通接口时用这个 appId
-- 生产环境商户和密钥由运营后台创建，不会写在脚本里
-- 用 INSERT IGNORE 保证脚本可重复执行（唯一键 uk_app_id 冲突时跳过）
INSERT IGNORE INTO merchant_app (merchant_no, app_id, app_name, merchant_public_key, notify_url, status, create_time)
VALUES ('M100001', 'APP100001', '演示商户（本地）', NULL, 'http://localhost:8080/notify/mock', 1, NOW());
