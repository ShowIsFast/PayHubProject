package com.payhub.common;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RSA2 签名/验签工具（支付宝用的就是这套）。
 *
 * <p><b>这是 W1 最值得逐行读的类</b>，因为它解释了一个面试高频问题：
 * 「支付回调怎么防伪造？」——答案就是验签，而验签的本质是：
 * <ol>
 *   <li>把参数按 key 字典序排序，拼成 {@code k1=v1&k2=v2}（不含 sign，不含空值）</li>
 *   <li>用私钥对这段字符串做 SHA256withRSA，得到签名</li>
 *   <li>对方用公钥解签名，把结果和自己算的摘要比对，一致即未篡改</li>
 * </ol>
 *
 * <p>关键细节：<b>拼签名串时 value 不做 URL 编码，发请求时才编码</b>。
 * 这两步顺序搞反，签名永远验不过——线上最常见的坑之一。
 */
public final class SignUtil {

    private static final String ALGORITHM = "SHA256withRSA";
    private static final String CHARSET = StandardCharsets.UTF_8.name();

    private SignUtil() {
    }

    /**
     * 构造待签名字符串：按 key 升序、过滤空值和 sign 字段、用 & 连接。
     */
    public static String buildSignContent(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && !e.getValue().isEmpty())
                .filter(e -> !"sign".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    public static String sign(Map<String, String> params, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(buildSignContent(params).getBytes(CHARSET));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("RSA2 签名失败", e);
        }
    }

    public static boolean verify(Map<String, String> params, PublicKey publicKey) {
        String sign = params.get("sign");
        if (sign == null || sign.isEmpty()) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(buildSignContent(params).getBytes(CHARSET));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            // 验签失败不抛异常，返回 false —— 上层决定是告警还是拒绝
            return false;
        }
    }

    /** 加载 PKCS8 格式私钥（去掉 PEM 头尾和换行后的 Base64 串） */
    public static PrivateKey loadPrivateKey(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(cleanKey(base64Key));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("加载私钥失败，请确认是 PKCS8 格式且已去掉头尾", e);
        }
    }

    /** 加载 X509 格式公钥 */
    public static PublicKey loadPublicKey(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(cleanKey(base64Key));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("加载公钥失败，请确认已去掉头尾", e);
        }
    }

    /** 生成一对 2048 位密钥，用于本地演示（生产密钥由支付宝密钥工具生成） */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("生成密钥对失败", e);
        }
    }

    public static void main(String[] args) {
        KeyPair keyPair = generateKeyPair();
        PrivateKey aPrivate = keyPair.getPrivate();
        PublicKey aPublic = keyPair.getPublic();
        System.out.println("私钥：" + Base64.getEncoder().encodeToString(aPrivate.getEncoded()));
        System.out.println("公钥：" + Base64.getEncoder().encodeToString(aPublic.getEncoded()));
        System.out.println("----------------------------------------");
    }

    public static String toBase64(java.security.Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static String cleanKey(String key) {
        return key.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }
}
