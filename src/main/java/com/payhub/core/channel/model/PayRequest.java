package com.payhub.core.channel.model;

/**
 * 调用渠道「预下单」的入参（渠道无关的抽象）。
 *
 * <p>这是「防腐层」的入口：外部渠道的参数千差万别（支付宝要 total_amount 字符串、
 * 微信要分、银联要报文），但进入我们核心域的，只有这几个字段。
 * 核心域永远不认识 alipay 的任何字段名。
 */
public record PayRequest(
        String paymentNo,
        String outTradeNo,
        long amount,
        String subject,
        String notifyUrl,
        int expireMinutes
) {
}
