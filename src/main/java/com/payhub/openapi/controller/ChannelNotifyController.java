package com.payhub.openapi.controller;

import com.payhub.core.enums.Channel;
import com.payhub.core.enums.NotifyResult;
import com.payhub.core.notify.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 渠道异步回调入口（W2 重写版）。
 *
 * <p>W1 那版「谁都能把订单改成已支付」的代码已经删掉了。现在这里只做一件事：
 * 把请求交给 {@link NotifyService}，按处理结果返回渠道要求的应答。
 *
 * <p>两个必须知道的协议细节：
 * <ul>
 *   <li>支付宝要求返回<b>纯字符串</b> {@code success}，不能是 JSON，也不能带引号</li>
 *   <li>返回 failure 会触发重试：4m / 10m / 10m / 1h / 2h / 6h / 15h，共 8 次</li>
 * </ul>
 *
 * <p>所以「返回 failure」是一个业务决策：验签失败/金额不符时返回 failure，
 * 意味着允许渠道重试（如果是我们配置错了，修好后还能自动恢复）。
 * 但如果是永久性错误（比如订单已被关闭），返回 failure 只会让渠道白重试 8 次，
 * 生产上通常也返回 success + 告警——这里的取舍要有意识，别机械照抄。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ChannelNotifyController {

    private final NotifyService notifyService;

    /** 支付宝异步通知（form 表单） */
    @PostMapping("/notify/alipay")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        NotifyResult result = notifyService.handle(Channel.ALIPAY, params);
        log.info("[ALIPAY] 回调应答={} 结果={}", result.getChannelResponse(), result.getDesc());
        return result.getChannelResponse();
    }

    /**
     * 模拟渠道回调（本地开发用）。
     *
     * <p>虽然是 mock，但走的是<b>真实的验签链路</b>：
     * 参数必须带 sign，且签名要用 MockChannelSigner 的私钥签。
     * 直接裸调（不带 sign）会被拒绝——你可以自己试一下。
     */
    @PostMapping("/notify/mock")
    public String mockNotify(@RequestParam Map<String, String> params) {
        NotifyResult result = notifyService.handle(Channel.MOCK, params);
        log.info("[MOCK] 回调应答={} 结果={}", result.getChannelResponse(), result.getDesc());
        return result.getChannelResponse();
    }
}
