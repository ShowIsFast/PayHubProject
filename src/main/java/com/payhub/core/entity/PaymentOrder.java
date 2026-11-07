package com.payhub.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 支付单：一次支付尝试的唯一记录。
 *
 * <p><b>为什么支付单不直接复用业务订单号？</b>
 * 因为一个订单可能被支付多次：扫码超时改用 H5、换渠道重试、拆单支付。
 * 如果拿订单号当渠道侧的 out_trade_no，第二次发起就会撞唯一键。
 *
 * <p>四层单据关系（后面几周会逐步补齐）：
 * <pre>
 *   业务订单 ──▶ 支付单 ──▶ 资金流水 ──▶ 会计凭证
 *   (W2)        (W1)       (W4)        (W4)
 * </pre>
 *
 * <p><b>三个关键注解：</b>
 * <ul>
 *   <li>{@code @Version}：开乐观锁。updateById 会自动变成
 *       {@code UPDATE ... SET ver = ver + 1 WHERE id = ? AND ver = ?}，
 *       并发下只有一个线程能改成功，返回 0 行即代表「别人先改了」。</li>
 *   <li>{@code fill = INSERT} / {@code INSERT_UPDATE}：时间字段自动填充，
 *       业务代码不再出现 {@code setCreateTime(LocalDateTime.now())}。</li>
 *   <li>{@code updateStrategy = NOT_NULL}：null 字段不参与 UPDATE，
 *       避免把 tradeNo 之类的列误覆盖成 NULL（支付系统里这是事故）。</li>
 * </ul>
 */
@Data
@TableName("payment_order")
public class PaymentOrder {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 平台支付单号：P + 雪花 ID */
    @TableField(value = "payment_no", jdbcType = JdbcType.VARCHAR)
    private String paymentNo;

    @TableField(value = "merchant_no", jdbcType = JdbcType.VARCHAR)
    private String merchantNo;

    @TableField(value = "app_id", jdbcType = JdbcType.VARCHAR)
    private String appId;

    /** 商户订单号：渠道侧唯一性的根 */
    @TableField(value = "out_trade_no", jdbcType = JdbcType.VARCHAR)
    private String outTradeNo;

    /** 渠道：MOCK / ALIPAY */
    @TableField(value = "channel", jdbcType = JdbcType.VARCHAR)
    private String channel;

    /** 金额，单位：分 */
    @TableField(value = "amount", jdbcType = JdbcType.BIGINT)
    private Long amount;

    @TableField(value = "currency", jdbcType = JdbcType.VARCHAR)
    private String currency;

    @TableField(value = "subject", jdbcType = JdbcType.VARCHAR)
    private String subject;

    /** 见 PaymentStatus */
    @TableField(value = "status", jdbcType = JdbcType.VARCHAR)
    private String status;

    /** 渠道交易号（支付宝 trade_no） */
    @TableField(value = "trade_no", jdbcType = JdbcType.VARCHAR)
    private String tradeNo;

    /** 收银台地址 / 二维码内容 */
    @TableField(value = "pay_url", jdbcType = JdbcType.VARCHAR)
    private String payUrl;

    @TableField(value = "pay_time", jdbcType = JdbcType.TIMESTAMP)
    private LocalDateTime payTime;

    @TableField(value = "expire_time", jdbcType = JdbcType.TIMESTAMP)
    private LocalDateTime expireTime;

    /**
     * 乐观锁版本号。
     *
     * <p>注意：加了 @Version 之后，{@code updateById} 必须带上当前 ver 才能更新成功。
     * 所以更新前必须先查（或由前端/上层带回来），不能凭空 new 一个对象直接 update。
     */
    @Version
    @TableField(value = "ver", jdbcType = JdbcType.INTEGER)
    private Integer ver;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE,
            updateStrategy = FieldStrategy.NOT_NULL)
    private LocalDateTime updateTime;
}
