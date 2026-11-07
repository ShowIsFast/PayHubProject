package com.payhub.common;

/**
 * 业务异常。
 *
 * <p>刻意和系统异常区分开：业务异常要原样返回给商户（含错误码），
 * 系统异常必须打日志 + 返回「系统繁忙」，绝不能把堆栈透给外部。
 * 这条区分在支付域尤其重要——错误码是商户对账的依据。
 */
public class BizException extends RuntimeException {

    private final String code;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
