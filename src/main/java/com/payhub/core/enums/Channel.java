package com.payhub.core.enums;

import com.payhub.common.BizException;

/**
 * 支付渠道。
 *
 * <p>枚举的第一价值是「把字符串收敛成有限集合」：
 * 渠道号一旦散落在各处字符串里，新增渠道就要全局搜索替换。
 */
public enum Channel {

    /** 本地模拟渠道：不需要任何账号即可跑通全链路 */
    MOCK("mock", "本地模拟"),

    /** 支付宝：真实沙箱/生产 */
    ALIPAY("alipay", "支付宝");

    private final String code;
    private final String desc;

    Channel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static Channel of(String code) {
        for (Channel c : values()) {
            if (c.code.equalsIgnoreCase(code)) {
                return c;
            }
        }
        throw new BizException("2001", "不支持的支付渠道: " + code);
    }
}
