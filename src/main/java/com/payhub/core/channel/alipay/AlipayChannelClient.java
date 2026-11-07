package com.payhub.core.channel.alipay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payhub.common.BizException;
import com.payhub.common.SignUtil;
import com.payhub.core.channel.ChannelClient;
import com.payhub.core.channel.model.ChannelQueryResult;
import com.payhub.core.channel.model.PayRequest;
import com.payhub.core.channel.model.PayResult;
import com.payhub.core.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 支付宝渠道适配器。
 *
 * <p><b>为什么不用官方 SDK，而是手写？</b>
 * 因为 SDK 把「签名」这件事藏起来了，而签名恰恰是面试最爱问的点
 * （「支付回调怎么防伪造」）。手写一遍你能看清：
 * <pre>
 *   组装参数 → 排序拼串 → SHA256withRSA 签名 → form 提交 → 解析 JSON
 * </pre>
 * 生产环境建议直接用 alipay-sdk-java，避免踩字符集、加签顺序的坑。
 *
 * <p>开启方式（application.yml）：
 * <pre>
 *   payhub.channel.mode: alipay
 *   payhub.channel.alipay.app-id: 沙箱 appId
 *   payhub.channel.alipay.merchant-private-key: 应用私钥（PKCS8，去头尾）
 *   payhub.channel.alipay.alipay-public-key: 支付宝公钥（不是应用公钥！）
 * </pre>
 *
 * <p>接口文档：https://opendocs.alipay.com/open/02ekfg （当面付 precreate）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payhub.channel.mode", havingValue = "alipay")
public class AlipayChannelClient implements ChannelClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CHARSET = StandardCharsets.UTF_8.name();

    private final String gateway;
    private final String appId;
    private final String merchantPrivateKeyText;
    private final String alipayPublicKeyText;
    private final String notifyUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 私钥只在第一次调用时解析（解析有开销，且失败要尽早暴露） */
    private volatile PrivateKey privateKey;
    private volatile PublicKey alipayPublicKey;

    public AlipayChannelClient(
            @Value("${payhub.channel.alipay.gateway}") String gateway,
            @Value("${payhub.channel.alipay.app-id}") String appId,
            @Value("${payhub.channel.alipay.merchant-private-key}") String merchantPrivateKeyText,
            @Value("${payhub.channel.alipay.alipay-public-key}") String alipayPublicKeyText,
            @Value("${payhub.channel.alipay.notify-url}") String notifyUrl) {
        this.gateway = gateway;
        this.appId = appId;
        this.merchantPrivateKeyText = merchantPrivateKeyText;
        this.alipayPublicKeyText = alipayPublicKeyText;
        this.notifyUrl = notifyUrl;
        if (isBlank(appId)) {
            log.warn("支付宝渠道已启用但未配置 app-id，调用时将直接失败");
        }
    }

    @Override
    public Channel channel() {
        return Channel.ALIPAY;
    }

    /**
     * 当面付预下单：alipay.trade.precreate。
     * 扫码支付用这个；PC 网站支付换成 alipay.trade.page.pay，H5 换成 wap.pay。
     */
    @Override
    public PayResult prepay(PayRequest request) {
        try {
            Map<String, Object> biz = new LinkedHashMap<>();
            biz.put("out_trade_no", request.outTradeNo());
            // 支付宝金额是「元」的字符串，最多两位小数；我们内部是「分」，这里只做一次转换
            biz.put("total_amount", toYuan(request.amount()));
            biz.put("subject", request.subject());
            biz.put("timeout_express", request.expireMinutes() + "m");

            JsonNode root = execute("alipay.trade.precreate", MAPPER.writeValueAsString(biz));
            JsonNode node = root.path("alipay_trade_precreate_response");
            String code = node.path("code").asText();
            if (!"10000".equals(code)) {
                String msg = node.path("sub_msg").asText(node.path("msg").asText("渠道返回失败"));
                log.warn("[ALIPAY] 预下单失败 outTradeNo={} code={} msg={}",
                        request.outTradeNo(), code, msg);
                return PayResult.fail(code, msg);
            }
            // qr_code 就是二维码内容，前端用它生成二维码
            return PayResult.ok(null, node.path("qr_code").asText());
        } catch (Exception e) {
            log.error("[ALIPAY] 预下单异常 outTradeNo={}", request.outTradeNo(), e);
            return PayResult.fail("ALIPAY_ERROR", e.getMessage());
        }
    }

    @Override
    public ChannelQueryResult query(String outTradeNo) {
        try {
            Map<String, Object> biz = Map.of("out_trade_no", outTradeNo);
            JsonNode root = execute("alipay.trade.query", MAPPER.writeValueAsString(biz));
            JsonNode node = root.path("alipay_trade_query_response");
            String code = node.path("code").asText();
            // 40004 = 交易不存在，这是「查不到」，和「查到了但未支付」不是一回事
            if (!"10000".equals(code)) {
                return ChannelQueryResult.notFound();
            }
            String status = node.path("trade_status").asText();
            String tradeNo = node.path("trade_no").asText();
            return switch (status) {
                case "TRADE_SUCCESS", "TRADE_FINISHED" ->
                        ChannelQueryResult.paid(tradeNo, status);
                default -> ChannelQueryResult.unpaid(status);
            };
        } catch (Exception e) {
            log.error("[ALIPAY] 查询异常 outTradeNo={}", outTradeNo, e);
            return ChannelQueryResult.notFound();
        }
    }

    /**
     * 关闭渠道侧交易：alipay.trade.close。
     *
     * <p>返回 40004（交易不存在）也算成功——说明这笔单在支付宝侧本来就没落地，
     * 不存在「用户还能继续支付」的风险，正是我们想要的结果。
     */
    @Override
    public boolean close(String outTradeNo) {
        try {
            Map<String, Object> biz = Map.of("out_trade_no", outTradeNo);
            JsonNode root = execute("alipay.trade.close", MAPPER.writeValueAsString(biz));
            String code = root.path("alipay_trade_close_response").path("code").asText();
            return "10000".equals(code) || "40004".equals(code);
        } catch (Exception e) {
            log.warn("[ALIPAY] 关闭交易异常 outTradeNo={}", outTradeNo, e);
            return false;
        }
    }

    /** 组装公共参数 + 签名 + 发请求 + 解析 JSON */
    private JsonNode execute(String method, String bizContent) throws Exception {
        ensureConfigured();

        Map<String, String> params = new TreeMap<>();
        params.put("app_id", appId);
        params.put("method", method);
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(TS));
        params.put("version", "1.0");
        params.put("notify_url", notifyUrl);
        params.put("biz_content", bizContent);
        // 签名只对「未编码」的原值做，编码发生在提交时，顺序不能反
        params.put("sign", SignUtil.sign(params, privateKey()));

        String form = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(gateway))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.debug("[ALIPAY] method={} status={} body={}", method, response.statusCode(), response.body());

        // TODO(生产必做)：校验响应签名。官方 SDK 会做，手写版这里省略了，
        //  因为要按「原始 JSON 子串」验签，正确做法是截取 alipay_trade_xxx_response 的原文再 verify。
        return MAPPER.readTree(response.body());
    }

    private PrivateKey privateKey() {
        if (privateKey == null) {
            synchronized (this) {
                if (privateKey == null) {
                    privateKey = SignUtil.loadPrivateKey(merchantPrivateKeyText);
                }
            }
        }
        return privateKey;
    }

    public PublicKey alipayPublicKey() {
        if (alipayPublicKey == null) {
            synchronized (this) {
                if (alipayPublicKey == null) {
                    alipayPublicKey = SignUtil.loadPublicKey(alipayPublicKeyText);
                }
            }
        }
        return alipayPublicKey;
    }

    private void ensureConfigured() {
        if (isBlank(appId) || isBlank(merchantPrivateKeyText)) {
            throw new BizException("2003", "支付宝渠道未配置 app-id 或应用私钥，请检查 application.yml");
        }
    }

    private static String toYuan(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
