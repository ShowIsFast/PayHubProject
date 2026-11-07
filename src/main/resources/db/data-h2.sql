-- H2 版本种子数据（用 WHERE NOT EXISTS 保证可重复执行）
INSERT INTO merchant_app (merchant_no, app_id, app_name, merchant_public_key, notify_url, status, create_time)
SELECT 'M100001', 'APP100001', '演示商户（本地）', NULL, 'http://localhost:8080/notify/mock', 1, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM merchant_app WHERE app_id = 'APP100001');
