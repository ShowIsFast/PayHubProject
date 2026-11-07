package com.payhub.openapi.dto;

import com.payhub.core.entity.PaymentOrder;

import java.time.LocalDateTime;

/** 支付单查询响应 */
public record PayQueryResponse(
        String paymentNo,
        String outTradeNo,
        String channel,
        long amount,
        String status,
        String tradeNo,
        LocalDateTime payTime,
        LocalDateTime createTime
) {

    public static PayQueryResponse of(PaymentOrder o) {
        return new PayQueryResponse(
                o.getPaymentNo(), o.getOutTradeNo(), o.getChannel(), o.getAmount(),
                o.getStatus(), o.getTradeNo(), o.getPayTime(), o.getCreateTime());
    }
}
