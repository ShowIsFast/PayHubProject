package com.payhub.core.command;

/**
 * 创建支付单的命令对象。
 *
 * <p>为什么不直接用 openapi 那边的 {@code UnifiedOrderRequest}？
 * 因为<b>依赖方向只能向内</b>：openapi 可以依赖 core，core 绝不能依赖 openapi。
 * 否则哪天要把 core 拆成独立服务，就会发现核心域里全是 HTTP 层的 DTO。
 *
 * <p>这个类看着像「多此一举的复制」，但它是分层能立住的关键。
 * 面试时被问「你怎么做分层」，答这一条比背概念有用。
 */
public record CreatePaymentCommand(
        String merchantNo,
        String appId,
        String outTradeNo,
        long amount,
        String subject,
        String channel,
        String notifyUrl
) {
}
