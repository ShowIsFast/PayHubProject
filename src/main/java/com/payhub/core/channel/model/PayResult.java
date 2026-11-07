package com.payhub.core.channel.model;

/**
 * 渠道预下单结果。
 *
 * <p>统一包装的好处：上层不需要 try-catch 每个渠道的 SDK 异常，
 * 只需要判断 success，失败原因进 errorCode/errorMsg（可用于渠道成功率监控）。
 */
public record PayResult(
        boolean success,
        String channelTradeNo,
        String payUrl,
        String errorCode,
        String errorMsg
) {

    public static PayResult ok(String channelTradeNo, String payUrl) {
        return new PayResult(true, channelTradeNo, payUrl, null, null);
    }

    public static PayResult fail(String errorCode, String errorMsg) {
        return new PayResult(false, null, null, errorCode, errorMsg);
    }
}
