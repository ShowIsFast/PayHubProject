package com.payhub.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签名/验签往返测试。
 *
 * <p>为什么这个测试重要：签名是支付域唯一能「证明数据没被篡改」的手段，
 * 而它最容易出的问题是「加签和验签的参数拼法不一致」。
 * 这个测试的价值不在于覆盖率，而在于给你一个可运行的签名样例。
 */
class SignUtilTest {

    @Test
    @DisplayName("私钥-签名")
    void signByPrivate() {
        String privateKeyStr = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD42QApCJvfxAjLyOsJpAy6PV8Rqe7Q91/nz/xL+B1UX3UgLonmSN5yBNcjE9LXn6Q23/9WjMwVUNmu16ziEU+Geegkzh1XrMgumVAzu1AmSAeomirlWekbPgvc1pjjS6enMkGdzplW93qS/Iu7a5RvuI9AhrQ1y/N93asJZGx70CRyUBtSYYPkF6q7jIqxTIcM49KNGpLKELagK0T450Hel9hYPVl6vCk1hzkAJ4BjUyhcwQcBekrPAbYBuoNRJvhb2PiHXWmtwSKJvo/E7Itk0xyIGfSPEsKy+8OgK+FNm4Rx643RKqb+b5rmfJJmf7WJoJz9VZvOyXMA5O+6IWUFAgMBAAECggEAAx6qaVlJP4ahFKsZ4R7DTFpOSR5fg+ApztAHWfPONLpbHDFpLE/ToMeVOOaGjopa3Ozby68FNKcTAOnrw5TeLVXsyqREnhtXmr44t3zIdihE9Ngj0u9Hqsa77awnNjU6To5G+bgS3ImgFIs1AN0n+xWnoINv0dC1M0equ08/CMyWEo6sWI+lrr163JdZoaKwMJV9O0ryzOu+59EeHSccX7Z5gAWK0Yo6DkjYWneHUlg4AnU1U4ViNZY+j5AWWxx4LdWvM+ZxLSo951dA6q7a/o5ddhyZbgw+uP4qgXAZhp90kwyO7Kjdt8qaHzu35VZp5uAw8ulwPiwjb0sZBlJ5ywKBgQD52l9JTVlTpELRx7Zv0sYPIWcEUcLHn1utNntk6cpcnZB0F+XQ+9uPwh8Y/QOw1dOVyHNNfdefkPWarPWW4gq3cWh/oMfasNclqUntVeegIuTQoMiu8zhMjn4sVV77Thwux0mxdj3lT0sttYYr+KUlJyKCHEKQ63nclWwHds8CTwKBgQD++EvkgY9gnrpaSSGiS1zNlJM+l1jC+LoDg9TO2TvjjcPT7mJ3onSWl1dkXjKtKcrSWwKGoqT3EWnATr2odZVNpcJKWzaeY01WgFvLYkjUF/QgvKourDAMwZmyC3n1TLjHiQYiObFzPjC4GoGU7gWsc1SF3M9MsGfLcJ0QqFgyawKBgQDB117Hwv/DeZPxpyzuMqtAf9ypqDkioIsCplszXoKGOiQhPY0qBsxACw4PV/SuFCa0dTCndTN9c3xoxTlAFxzTV2QIaJfFHh+880sMm/I49flptuQbf001b/OJt64Z8G7DCBAvYxoANvWPf96ZbK3LVkJaiUxHnLkNnsuSOuW91wKBgHY1R3o5Vu/Ep0EUhrNnnS7yoxFxuYUrK5JVnGQJ8uoc351x99TfTI5EvA+cXIJLzeyYpSMmZLTnLdAqsWNYoE/PebRfdYMZJtHj0KsKdhlewaAr1/qredtwJoGVMIW5fDGGJYHYnZyASLYSE+GoOJkrHxSwuqsjXfvDyg3e/oThAoGBAIOPCa7cT1NM94ZOBpsHjNbtIdNzSRKc7CD462Wysw6+3s/YotqyktSzEZcMojRPjTBkxFsfu+QgUeKtS/fAs7/X8zR/QmReqcNarmHkFp1RA3JC4BF4S+8NA0waMiMKlOdOmkaVFF0H7W57hOuZmD57/bkGTx9RnangWcF8RjlL";
        PrivateKey privateKey = SignUtil.loadPrivateKey(privateKeyStr);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("merchantNo", "M100001");
        params.put("appId", "APP100001");
        params.put("outTradeNo", "T20261106006");
        params.put("amount", "8833");
        params.put("channel", "MOCK");
        params.put("subject","蓝牙耳机");
        String sign = SignUtil.sign(params, privateKey);
        params.put("sign", sign);
        PublicKey publicKey = SignUtil.loadPublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA+NkAKQib38QIy8jrCaQMuj1fEanu0Pdf58/8S/gdVF91IC6J5kjecgTXIxPS15+kNt//VozMFVDZrtes4hFPhnnoJM4dV6zILplQM7tQJkgHqJoq5VnpGz4L3NaY40unpzJBnc6ZVvd6kvyLu2uUb7iPQIa0Ncvzfd2rCWRse9AkclAbUmGD5Bequ4yKsUyHDOPSjRqSyhC2oCtE+OdB3pfYWD1ZerwpNYc5ACeAY1MoXMEHAXpKzwG2AbqDUSb4W9j4h11prcEiib6PxOyLZNMciBn0jxLCsvvDoCvhTZuEceuN0Sqm/m+a5nySZn+1iaCc/VWbzslzAOTvuiFlBQIDAQAB");

        assertTrue(SignUtil.verify(params, publicKey), "验签应通过");
        System.out.println("验签通过，签名：" + sign);
    }

    @Test
    @DisplayName("签名后验签通过")
    void signAndVerify() {
        KeyPair keyPair = SignUtil.generateKeyPair();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("merchantNo", "M100001");
        params.put("appId", "APP100001");
        params.put("outTradeNo", "T20261106001");
        params.put("amount", "100");
        params.put("channel", "MOCK");

        String sign = SignUtil.sign(params, keyPair.getPrivate());
        params.put("sign", sign);

        assertTrue(SignUtil.verify(params, keyPair.getPublic()), "验签应通过");
    }

    @Test
    @DisplayName("金额被篡改后验签失败")
    void tamperAmountThenVerifyFail() {
        KeyPair keyPair = SignUtil.generateKeyPair();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("merchantNo", "M100001");
        params.put("outTradeNo", "T20261106001");
        params.put("amount", "100");

        params.put("sign", SignUtil.sign(params, keyPair.getPrivate()));
        // 攻击者把 100 分改成 1 分
        params.put("amount", "1");

        assertFalse(SignUtil.verify(params, keyPair.getPublic()), "篡改后验签必须失败");
    }

    @Test
    @DisplayName("待签串按 key 字典序拼接，且排除 sign 与空值")
    void signContentSorted() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("b", "2");
        params.put("a", "1");
        params.put("empty", "");
        params.put("sign", "xxx");

        String content = SignUtil.buildSignContent(params);
        assertTrue(content.equals("a=1&b=2"), "实际为: " + content);
    }
}
