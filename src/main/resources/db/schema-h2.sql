-- ============================================================
-- H2 版本建表脚本（备用 profile 使用，MySQL 风格的 H2 兼容写法）
-- 与 db/schema.sql 结构保持一致，只是去掉了 H2 不认的 ENGINE/COMMENT/KEY 语法
-- ============================================================

CREATE TABLE IF NOT EXISTS merchant_app (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_no         VARCHAR(32)  NOT NULL,
    app_id              VARCHAR(32)  NOT NULL,
    app_name            VARCHAR(64),
    merchant_public_key VARCHAR(1024),
    notify_url          VARCHAR(255),
    status              TINYINT      NOT NULL DEFAULT 1,
    create_time         DATETIME     NOT NULL,
    CONSTRAINT uk_app_id UNIQUE (app_id)
);

CREATE TABLE IF NOT EXISTS payment_order (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_no    VARCHAR(40)  NOT NULL,
    merchant_no   VARCHAR(32)  NOT NULL,
    app_id        VARCHAR(32)  NOT NULL,
    out_trade_no  VARCHAR(64)  NOT NULL,
    channel       VARCHAR(16)  NOT NULL,
    amount        BIGINT       NOT NULL,
    currency      VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    subject       VARCHAR(128),
    status        VARCHAR(16)  NOT NULL,
    trade_no      VARCHAR(64),
    pay_url       VARCHAR(512),
    pay_time      DATETIME,
    expire_time   DATETIME,
    ver           INT          NOT NULL DEFAULT 0,
    create_time   DATETIME     NOT NULL,
    update_time   DATETIME     NOT NULL,
    CONSTRAINT uk_payment_no UNIQUE (payment_no),
    CONSTRAINT uk_merchant_out UNIQUE (merchant_no, out_trade_no)
);

CREATE TABLE IF NOT EXISTS payment_callback_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel        VARCHAR(16)  NOT NULL,
    notify_key     VARCHAR(160) NOT NULL,
    out_trade_no   VARCHAR(64),
    payment_no     VARCHAR(40),
    raw_body       TEXT,
    sign_verified  TINYINT      NOT NULL DEFAULT 0,
    amount_check   TINYINT      NOT NULL DEFAULT 0,
    process_status VARCHAR(16)  NOT NULL,
    fail_reason    VARCHAR(255),
    retry_count    INT          NOT NULL DEFAULT 0,
    create_time    DATETIME     NOT NULL,
    update_time    DATETIME     NOT NULL,
    CONSTRAINT uk_notify_key UNIQUE (notify_key)
);
CREATE INDEX IF NOT EXISTS idx_callback_payment_no ON payment_callback_log (payment_no);

CREATE TABLE IF NOT EXISTS local_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type        VARCHAR(32) NOT NULL,
    biz_no          VARCHAR(64) NOT NULL,
    payload         TEXT,
    status          VARCHAR(16) NOT NULL,
    retry           INT         NOT NULL DEFAULT 0,
    next_retry_time DATETIME,
    create_time     DATETIME    NOT NULL,
    update_time     DATETIME    NOT NULL,
    CONSTRAINT uk_biz UNIQUE (biz_type, biz_no)
);
