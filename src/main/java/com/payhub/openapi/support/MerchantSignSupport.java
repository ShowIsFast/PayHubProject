package com.payhub.openapi.support;

import com.payhub.common.BizException;
import com.payhub.common.SignUtil;
import com.payhub.merchant.entity.MerchantApp;
import com.payhub.merchant.service.MerchantAppService;
import com.payhub.openapi.dto.UnifiedOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商户请求验签。
 *
 * <p>这里实现的是<b>平台自己的</b>签名体系（商户 ↔ 平台），
 * 和渠道回调的验签（支付宝 ↔ 平台）是两套独立的密钥，不要混为一谈。
 * 面试里能分清这两套，说明你真的做过支付接入。
 *
 * <p>W1 默认关闭（{@code payhub.sign.verify-enabled: false}），
 * 因为先生成一对密钥、配进数据库、再算签名，会挡住「先跑通」这一步。
 * 打开方式见 docs/W1-骨架导读.md 第 6 节。
 */
@Slf4j
@Component
public class MerchantSignSupport {

    private final MerchantAppService merchantAppService;
    private final boolean verifyEnabled;

    //没写 @Autowired 也能注入
    //Spring 4.3 之后，类只有一个构造器时，@Autowired 可以省略。这是官方行为，不是漏写。
    public MerchantSignSupport(MerchantAppService merchantAppService,
                               @Value("${payhub.sign.verify-enabled:true}") boolean verifyEnabled) {
        this.merchantAppService = merchantAppService;
        this.verifyEnabled = verifyEnabled;
    }

    public void verifyIfEnabled(UnifiedOrderRequest request) {
        if (!verifyEnabled) {
            return;
        }
        MerchantApp app = merchantAppService.mustGet(request.merchantNo(), request.appId());
        if (app.getMerchantPublicKey() == null || app.getMerchantPublicKey().isBlank()) {
            throw new BizException("2002", "商户未配置公钥，无法验签");
        }
        Map<String, String> params = toSignMap(request);
        boolean ok = SignUtil.verify(params, SignUtil.loadPublicKey(app.getMerchantPublicKey()));
        if (!ok) {
            log.warn("验签失败 merchantNo={} outTradeNo={}", request.merchantNo(), request.outTradeNo());
            throw new BizException("2002", "签名校验失败");
        }
    }

    private Map<String, String> toSignMap(UnifiedOrderRequest r) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("merchantNo", r.merchantNo());
        map.put("appId", r.appId());
        map.put("outTradeNo", r.outTradeNo());
        map.put("amount", String.valueOf(r.amount()));
        map.put("subject", r.subject());
        map.put("channel", r.channel());
        map.put("notifyUrl", r.notifyUrl());
        map.put("sign", r.sign());
        return map;
    }
}
