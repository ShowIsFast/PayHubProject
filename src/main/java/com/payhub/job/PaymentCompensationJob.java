package com.payhub.job;

import com.payhub.core.entity.PaymentOrder;
import com.payhub.core.enums.AdvanceOutcome;
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
 * 掉单补偿任务。
 *
 * <p>「掉单」= 用户付了钱，但回调没到（网络抖动、我方重启、支付宝重试耗尽）。
 * 它是支付系统里<b>必然会发生</b>的事，不是异常，所以必须有兜底。
 *
 * <p>三类兜底手段，按实时性排序：
 * <pre>
 *   1. 回调（秒级，主力）
 *   2. 主动查询补偿（分钟级，本任务）
 *   3. T+1 对账（天级，最终兜底）—— W5 实现
 * </pre>
 *
 * <p>注意查单窗口：只捞「超过 delay 秒没更新」的单，避免为刚下单的支付浪费 RPC。
 * 生产上这个延迟一般 30s~2min，还要考虑分片（多实例时用 XXL-Job 分片广播，
 * 否则两个实例同时查同一批单，虽然幂等但浪费渠道配额）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payhub.job.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentCompensationJob {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOrderService paymentOrderService;

    @Value("${payhub.job.compensate-delay-seconds:120}")
    private long delaySeconds;

    @Value("${payhub.job.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${payhub.job.compensate-interval-ms:10000}",
            initialDelayString = "${payhub.job.compensate-interval-ms:10000}")
    public void compensate() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentOrder> candidates = paymentOrderMapper.selectPayingForCompensation(
                now.minusSeconds(delaySeconds), now, batchSize);
        if (candidates.isEmpty()) {
            return;
        }
        log.info("[补偿] 本轮候选 {} 笔", candidates.size());
        for (PaymentOrder order : candidates) {
            try {
                AdvanceOutcome outcome = paymentOrderService.compensate(order.getPaymentNo());
                if (outcome == AdvanceOutcome.ADVANCED) {
                    log.info("[补偿] 补单成功 paymentNo={}", order.getPaymentNo());
                } else {
                    log.debug("[补偿] paymentNo={} 结果={}", order.getPaymentNo(), outcome);
                }
            } catch (Exception e) {
                // 单笔失败不能影响整批
                log.error("[补偿] 处理异常 paymentNo={}", order.getPaymentNo(), e);
            }
        }
    }
}
