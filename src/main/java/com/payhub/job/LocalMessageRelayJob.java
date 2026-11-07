package com.payhub.job;

import com.payhub.core.entity.LocalMessage;
import com.payhub.core.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * outbox 投递任务。
 *
 * <p>W2 阶段它只做一件事：把 NEW 的消息打日志 + 标记 SENT，用来证明
 * 「状态变更和消息写入是原子的」这条链路是通的。
 *
 * <p><b>W3 会把这个类改造成 MQ 生产者</b>：
 * <pre>
 *   // W2
 *   log.info("[OUTBOX] 投递 {}", payload);
 *   // W3
 *   rocketMQTemplate.send("PAYMENT_SUCCESS", payload);
 * </pre>
 * 注意改造范围只有「投递」这一行，业务代码一行不用动——这就是 outbox 的价值：
 * 把「可靠地发消息」这件事从业务逻辑里拔出来，交给可重试的任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payhub.job.enabled", havingValue = "true", matchIfMissing = true)
public class LocalMessageRelayJob {

    private final LocalMessageMapper localMessageMapper;

    @Value("${payhub.job.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${payhub.job.outbox-interval-ms:5000}",
            initialDelayString = "${payhub.job.outbox-interval-ms:5000}")
    public void relay() {
        LocalDateTime now = LocalDateTime.now();
        List<LocalMessage> pending = localMessageMapper.selectPending(now, batchSize);
        for (LocalMessage message : pending) {
            try {
                log.info("[OUTBOX] 投递事件 bizType={} bizNo={} payload={}",
                        message.getBizType(), message.getBizNo(), message.getPayload());
                localMessageMapper.updateStatus(message.getId(), "SENT", now);
            } catch (Exception e) {
                log.error("[OUTBOX] 投递失败 bizNo={}", message.getBizNo(), e);
            }
        }
    }
}
