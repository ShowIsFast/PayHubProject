-- ============================================================
-- W1 建表脚本（MySQL 8，InnoDB / utf8mb4）
-- 说明：W1 只建两张表。账务、退款、对账表在 W3~W5 再加。
-- ============================================================

-- 商户应用：谁来调我们的接口、用什么公钥验签
CREATE TABLE IF NOT EXISTS merchant_app
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    merchant_no         VARCHAR(32)  NOT NULL COMMENT '商户号',
    app_id              VARCHAR(32)  NOT NULL COMMENT '应用ID，对外唯一标识',
    app_name            VARCHAR(64)           DEFAULT NULL COMMENT '应用名称',
    merchant_public_key VARCHAR(1024)         DEFAULT NULL COMMENT '商户公钥，用于请求验签',
    notify_url          VARCHAR(255)          DEFAULT NULL COMMENT '默认回调地址',
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    create_time         DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_id (app_id),
    KEY idx_merchant_no (merchant_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='商户应用';

-- 支付单：一次支付尝试的唯一记录
-- 关键设计：
--   1. payment_no        平台支付单号（对外主键）
--   2. out_trade_no      商户订单号，渠道侧唯一性的根
--   3. uk_merchant_out   防重复下单的「硬约束」，不靠代码 if 判断
--   4. amount            单位是「分」，绝对不用浮点
--   5. ver               乐观锁版本号，@Version 注解会用到
CREATE TABLE IF NOT EXISTS payment_order
(
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    payment_no   VARCHAR(40) NOT NULL COMMENT '平台支付单号',
    merchant_no  VARCHAR(32) NOT NULL COMMENT '商户号',
    app_id       VARCHAR(32) NOT NULL COMMENT '应用ID',
    out_trade_no VARCHAR(64) NOT NULL COMMENT '商户订单号',
    channel      VARCHAR(16) NOT NULL COMMENT '渠道：MOCK/ALIPAY',
    amount       BIGINT      NOT NULL COMMENT '金额，单位：分',
    currency     VARCHAR(8)  NOT NULL DEFAULT 'CNY' COMMENT '币种',
    subject      VARCHAR(128)         DEFAULT NULL COMMENT '商品标题',
    status       VARCHAR(16) NOT NULL COMMENT '状态：CREATED/PAYING/SUCCESS/FAILED/CLOSED',
    trade_no     VARCHAR(64)          DEFAULT NULL COMMENT '渠道交易号',
    pay_url      VARCHAR(512)         DEFAULT NULL COMMENT '收银台地址/二维码内容',
    pay_time     DATETIME             DEFAULT NULL COMMENT '支付成功时间',
    expire_time  DATETIME             DEFAULT NULL COMMENT '支付超时时间',
    ver          INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time  DATETIME    NOT NULL COMMENT '创建时间',
    update_time  DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_merchant_out (merchant_no, out_trade_no),
    KEY idx_status (status),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='支付单';

-- ============================================================
-- W2：回调证据链 + 本地消息表（outbox）
-- 设计要点：
--   1. payment_callback_log 是「证据」：先落库再处理，验签失败也要留痕
--   2. uk_notify_key 保证同一条通知只处理一次；重复通知只累加 retry_count
--   3. local_message 是 outbox：与业务状态变更同事务写入，由定时任务投递
-- ============================================================

CREATE TABLE IF NOT EXISTS payment_callback_log
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    channel        VARCHAR(16)  NOT NULL COMMENT '渠道：MOCK/ALIPAY',
    notify_key     VARCHAR(160) NOT NULL COMMENT '通知幂等键：channel:outTradeNo:tradeStatus',
    out_trade_no   VARCHAR(64)           DEFAULT NULL COMMENT '商户订单号',
    payment_no     VARCHAR(40)           DEFAULT NULL COMMENT '平台支付单号',
    raw_body       TEXT COMMENT '原始报文，先落证据再处理',
    sign_verified  TINYINT      NOT NULL DEFAULT 0 COMMENT '1验签通过 0未通过',
    amount_check   TINYINT      NOT NULL DEFAULT 0 COMMENT '1金额一致 0不一致或未校验',
    process_status VARCHAR(16)  NOT NULL COMMENT 'PENDING/SUCCESS/REJECTED/FAILED',
    fail_reason    VARCHAR(255)          DEFAULT NULL COMMENT '失败原因',
    retry_count    INT          NOT NULL DEFAULT 0 COMMENT '重复通知次数',
    create_time    DATETIME     NOT NULL COMMENT '创建时间',
    update_time    DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notify_key (notify_key),
    KEY idx_payment_no (payment_no),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='渠道回调日志（证据链）';

CREATE TABLE IF NOT EXISTS local_message
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    biz_type        VARCHAR(32) NOT NULL COMMENT '业务类型：PAYMENT_SUCCESS / PAYMENT_CLOSED',
    biz_no          VARCHAR(64) NOT NULL COMMENT '业务单号（支付单号）',
    payload         TEXT COMMENT '消息体',
    status          VARCHAR(16) NOT NULL COMMENT 'NEW/SENT/FAILED',
    retry           INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME             DEFAULT NULL COMMENT '下次重试时间',
    create_time     DATETIME    NOT NULL COMMENT '创建时间',
    update_time     DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz (biz_type, biz_no),
    KEY idx_status_next (status, next_retry_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='本地消息表（outbox）';
