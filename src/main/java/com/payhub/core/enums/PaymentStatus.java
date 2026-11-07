package com.payhub.core.enums;

/**
 * 支付单状态。
 *
 * <p><b>状态机是支付系统最便宜、最有效的防重复手段。</b>
 * 回调重复、乱序、补偿任务并发——最终都靠「状态只能前进」这一条挡住。
 *
 * <pre>
 *   CREATED ──调渠道成功──▶ PAYING ──回调/查询成功──▶ SUCCESS
 *      │                     │
 *      └──参数/渠道失败──▶ FAILED
 *                            └──超时/主动关闭──▶ CLOSED
 * </pre>
 *
 * <p>注意：SUCCESS/FAILED/CLOSED 是终态，任何流转到终态之后的请求都必须幂等返回。
 */
public enum PaymentStatus {

    /** 已落库，尚未调渠道 */
    CREATED,
    /** 已调渠道成功，等用户支付 */
    PAYING,
    /** 支付成功（终态） */
    SUCCESS,
    /** 下单失败（终态） */
    FAILED,
    /** 已关闭/超时（终态） */
    CLOSED;

    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == CLOSED;
    }

    /**
     * 允许的状态流转表。
     *
     * <p>把它写成数据而不是 if-else 的好处：新增状态时只改一张表，
     * 而且这段代码在 review 时一眼能看出有没有非法流转。
     */
    public boolean canTransitTo(PaymentStatus target) {
        return switch (this) {
            case CREATED -> target == PAYING || target == FAILED;
            // PAYING 只允许前进到 SUCCESS 或关闭；绝不回到 CREATED
            case PAYING -> target == SUCCESS || target == CLOSED;
            // 终态不可变：重复回调走到这里直接幂等返回
            case SUCCESS, FAILED, CLOSED -> false;
        };
    }
}
