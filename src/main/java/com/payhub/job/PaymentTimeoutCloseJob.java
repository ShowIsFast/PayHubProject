package com.payhub.job;

import com.payhub.core.channel.ChannelClient;
import com.payhub.core.channel.ChannelRouter;
import com.payhub.core.entity.PaymentOrder;
import com.payhub.core.enums.Channel;
import com.payhub.core.mapper.PaymentOrderMapper;
import com.payhub.core.service.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时关单任务。
 *
 * <p>为什么必须关单？
 * <ul>
 *   <li>支付单永远挂在 PAYING，用户看到的订单状态是错的</li>
 *   <li>商户侧的库存/优惠券一直被占用，释放不出去</li>
 *   <li>对账时这笔单会变成「长期挂账」，越积越多</li>
 * </ul>
 *
 * <p>两步走：<b>先改本地（CAS 到 CLOSED），再通知渠道关闭。</b>
 * 顺序不能反：如果先关渠道成功了、本地 CAS 失败（比如用户刚好支付成功），
 * 就会出现「渠道关了单但我们以为还开着」的错位。
 * 反过来，本地关了渠道没关，最多是渠道侧有一笔待支付的单，不会丢钱。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payhub.job.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentTimeoutCloseJob {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOrderService paymentOrderService;
    private final ChannelRouter channelRouter;

    @Value("${payhub.job.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${payhub.job.close-interval-ms:15000}",
            initialDelayString = "${payhub.job.close-interval-ms:15000}")
    public void closeExpired() {
        List<PaymentOrder> expired = paymentOrderMapper.selectExpiredPaying(LocalDateTime.now(), batchSize);
        if (expired.isEmpty()) {
            return;
        }
        log.info("[关单] 本轮候选 {} 笔", expired.size());
        for (PaymentOrder order : expired) {
            try {
                boolean closed = paymentOrderService.closePayment(order.getPaymentNo(), "TIMEOUT");
                if (!closed) {
                    // CAS 失败：说明这期间用户刚好支付成功，是最幸运的一种「失败」
                    log.info("[关单] 未关闭（状态已变化）paymentNo={}", order.getPaymentNo());
                    continue;
                }
                closeChannelQuietly(order);
            } catch (Exception e) {
                log.error("[关单] 处理异常 paymentNo={}", order.getPaymentNo(), e);
            }
        }
    }

    /** 渠道关闭失败不影响本地状态，进日志等人工处理 */
    private void closeChannelQuietly(PaymentOrder order) {
        try {
            ChannelClient client = channelRouter.route(Channel.valueOf(order.getChannel()));
            client.close(order.getOutTradeNo());
        } catch (Exception e) {
            log.warn("[关单] 渠道关闭失败 paymentNo={} 需人工核对", order.getPaymentNo(), e);
        }
    }
}
