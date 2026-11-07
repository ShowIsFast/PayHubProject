package com.payhub.core.channel.model;

/**
 * 主动查询渠道的结果。
 *
 * <p>注意 found 和 paid 是两个维度：
 * found=false 表示渠道查不到这笔单（可能还没落地），
 * 这跟「查到了但没付」是两件完全不同的事——W2 的补偿任务要靠这个区分。
 */
public record ChannelQueryResult(
        boolean found,
        boolean paid,
        String channelTradeNo,
        String rawStatus
) {

    public static ChannelQueryResult notFound() {
        return new ChannelQueryResult(false, false, null, null);
    }

    public static ChannelQueryResult paid(String channelTradeNo, String rawStatus) {
        return new ChannelQueryResult(true, true, channelTradeNo, rawStatus);
    }

    public static ChannelQueryResult unpaid(String rawStatus) {
        return new ChannelQueryResult(true, false, null, rawStatus);
    }
}
