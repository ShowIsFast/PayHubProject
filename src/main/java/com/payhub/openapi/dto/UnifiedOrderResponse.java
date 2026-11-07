package com.payhub.openapi.dto;

import com.payhub.core.entity.PaymentOrder;

import java.time.LocalDateTime;

/**
 * 统一下单响应。
 *
 * <p>返回给商户的字段要克制：只给「商户需要知道的」，不给内部主键、渠道报文原文。
 * 渠道返回的原始报文只进日志和数据库，避免把支付宝的错误细节透给商户。
 */
public record UnifiedOrderResponse(
        String paymentNo,
        String outTradeNo,
        String channel,
        long amount,
        String status,
        String payUrl,
        LocalDateTime expireTime
) {

    public static UnifiedOrderResponse of(PaymentOrder o) {
        return new UnifiedOrderResponse(
                o.getPaymentNo(), o.getOutTradeNo(), o.getChannel(),
                o.getAmount(), o.getStatus(), o.getPayUrl(), o.getExpireTime());
    }
}
