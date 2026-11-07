package com.payhub.openapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 统一下单请求（商户 → 平台）。
 *
 * <p>字段设计的几个讲究：
 * <ul>
 *   <li>amount 用 long 分，不用 BigDecimal：跨语言、跨库都不会有精度歧义</li>
 *   <li>outTradeNo 由商户生成，我们只保证「同一商户下唯一」</li>
 *   <li>channel 让商户指定：真实场景通常还会允许「不传，由平台智能路由」</li>
 *   <li>sign 在 W1 默认不校验（payhub.sign.verify-enabled=false）</li>
 * </ul>
 */
public record UnifiedOrderRequest(
        @NotBlank(message = "商户号不能为空")
        String merchantNo,

        @NotBlank(message = "appId 不能为空")
        String appId,

        @NotBlank(message = "商户订单号不能为空")
        String outTradeNo,

        @NotNull(message = "金额不能为空")
        @Min(value = 1, message = "金额必须大于 0 分")
        @Max(value = 100_000_000L, message = "金额超出上限")
        Long amount,

        @NotBlank(message = "商品标题不能为空")
        String subject,

        /** MOCK / ALIPAY */
        @NotBlank(message = "渠道不能为空")
        String channel,

        String notifyUrl,

        String sign
) {
}
