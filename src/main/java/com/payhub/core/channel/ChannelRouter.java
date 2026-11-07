package com.payhub.core.channel;

import com.payhub.common.BizException;
import com.payhub.core.enums.Channel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道路由（策略模式的选择器）。
 *
 * <p>Spring 会把所有 {@link ChannelClient} 实现注入到 List 里，
 * 构造时转成 Map，运行时按渠道号 O(1) 命中——比一堆 if-else 好在：
 * <ul>
 *   <li>新增渠道零改动</li>
 *   <li>可以在这里加「渠道降级」：主渠道失败自动切备用</li>
 * </ul>
 *
 * <p>W2~W3 的演进方向：路由不只是「按 channel 选实现」，
 * 而是「按费率、成功率、限额、可用性」动态选渠道（智能路由）。
 */
@Component
public class ChannelRouter {

    private final Map<Channel, ChannelClient> clients = new EnumMap<>(Channel.class);

    public ChannelRouter(List<ChannelClient> channelClients) {
        channelClients.forEach(c -> clients.put(c.channel(), c));
    }

    public ChannelClient route(Channel channel) {
        ChannelClient client = clients.get(channel);
        if (client == null) {
            throw new BizException("2002",
                    "渠道[" + channel.getDesc() + "]未启用，当前可用: " + clients.keySet());
        }
        return client;
    }

    public boolean isAvailable(Channel channel) {
        return clients.containsKey(channel);
    }
}
