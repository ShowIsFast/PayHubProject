package com.payhub.tools;

import com.payhub.common.SignUtil;

import java.security.KeyPair;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商户密钥小工具（本地练习用）。
 *
 * <p>解决的问题：一旦打开 {@code payhub.sign.verify-enabled: true}，
 * 下单请求就必须带签名，但演示商户的库里没有公钥——
 * 所有请求都会被「商户未配置公钥」拒绝。
 *
 * <p>用法（IDEA 里直接右键运行 main，或 mvn exec）：
 * <pre>
 *   1. 运行本类 → 得到三样东西：
 *        · 应用公钥  → 执行打印出来的 UPDATE 语句，写进 merchant_app
 *        · 应用私钥  → 自己留着，用来加签（不要提交到仓库）
 *        · 已签名的请求体 → 直接粘到 Postman 里调用统一下单
 *   2. 改一下 body 里的 amount，重新发 → 会收到「签名校验失败」
 * </pre>
 *
 * <p>这就是「篡改检测」的手工版体验：改一个字符，签名就对不上。
 */
public final class SignTool {

    private SignTool() {
    }

    public static void main(String[] args) {
        KeyPair keyPair = SignUtil.generateKeyPair();
        String publicKey = SignUtil.toBase64(keyPair.getPublic());
        String privateKey = SignUtil.toBase64(keyPair.getPrivate());

        // 待签名的业务参数（字段名必须和 UnifiedOrderRequest 一致）
        Map<String, String> params = new LinkedHashMap<>();
        params.put("merchantNo", "M100001");
        params.put("appId", "APP100001");
        params.put("outTradeNo", "SIGN-" + System.currentTimeMillis());
        params.put("amount", "8888");
        params.put("subject", "签名测试商品");
        params.put("channel", "MOCK");
        params.put("notifyUrl", "");

        String sign = SignUtil.sign(params, keyPair.getPrivate());

        System.out.println("""

                ==================== 1. 应用公钥（写进数据库）====================
                %s

                ---- 执行这条 SQL ----
                UPDATE merchant_app SET merchant_public_key = '%s' WHERE app_id = 'APP100001';

                ==================== 2. 应用私钥（自己保存，别提交）====================
                %s

                ==================== 3. 已签名的请求体（可直接 POST）====================
                {
                  "merchantNo": "%s",
                  "appId": "%s",
                  "outTradeNo": "%s",
                  "amount": %s,
                  "subject": "%s",
                  "channel": "%s",
                  "sign": "%s"
                }

                提示：把 amount 改成 1 再发一次，应当返回「签名校验失败」。
                """.formatted(
                publicKey,
                publicKey,
                privateKey,
                params.get("merchantNo"),
                params.get("appId"),
                params.get("outTradeNo"),
                params.get("amount"),
                params.get("subject"),
                params.get("channel"),
                sign));
    }
}
