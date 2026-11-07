package com.payhub.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 单号生成器（雪花算法简化版）。
 *
 * <p>为什么不用 UUID / 数据库自增？
 * <ul>
 *   <li>自增：暴露业务量，且分库分表后会冲突</li>
 *   <li>UUID：无序，MySQL 聚簇索引插入会随机分裂页，性能差</li>
 *   <li>雪花：趋势递增 + 全局唯一 + 自带时间信息（排查时能反解出生成时间）</li>
 * </ul>
 *
 * <p>面试延伸：生产上要么用 Redis INCR（简单但依赖 Redis），
 * 要么用美团 Leaf / 百度 UidGenerator（解决时钟回拨和高并发）。
 */
@Component
public class IdGenerator {

    /** 起始时间戳：2026-01-01，决定了 41 位时间戳能用到哪一年 */
    private static final long EPOCH = 1767225600000L;
    private static final long WORKER_BITS = 10L;
    private static final long SEQ_BITS = 12L;
    private static final long MAX_SEQ = ~(-1L << SEQ_BITS);   // 4095
    private static final long MAX_WORKER = ~(-1L << WORKER_BITS);

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public IdGenerator(@Value("${payhub.worker-id:1}") long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER) {
            throw new IllegalArgumentException("workerId 超出范围 0~" + MAX_WORKER);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now < lastTimestamp) {
            // 时钟回拨：小回拨等待，大回拨直接报错（绝不能生成重复 ID）
            long offset = lastTimestamp - now;
            if (offset > 5) {
                throw new IllegalStateException("时钟回拨 " + offset + "ms，拒绝生成单号");
            }
            try {
                Thread.sleep(offset << 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            now = System.currentTimeMillis();
        }
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQ;
            if (sequence == 0) {
                now = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = now;
        return ((now - EPOCH) << (WORKER_BITS + SEQ_BITS))
                | (workerId << SEQ_BITS)
                | sequence;
    }

    /** 支付单号：P + 雪花 ID */
    public String nextPaymentNo() {
        return "P" + nextId();
    }

    private long waitNextMillis(long last) {
        long now = System.currentTimeMillis();
        while (now <= last) {
            now = System.currentTimeMillis();
        }
        return now;
    }
}
