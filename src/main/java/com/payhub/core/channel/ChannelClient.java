package com.payhub.core.channel;

import com.payhub.core.channel.model.ChannelQueryResult;
import com.payhub.core.channel.model.PayRequest;
import com.payhub.core.channel.model.PayResult;
import com.payhub.core.enums.Channel;

/**
 * 渠道适配器（策略模式的策略接口）。
 *
 * <p>新增一个渠道 = 新增一个实现类，核心域一行不改。
 * 这就是 JD 里「对设计模式非常熟悉」最实在的落点：
 * 不是为了用模式而用模式，是因为「加渠道不改调用方」这个需求天然长得像策略。
 *
 * <p>实现类需要保证：
 * <ol>
 *   <li>幂等：同一 outTradeNo 重复调用不能产生两笔渠道交易</li>
 *   <li>超时：必须设置连接/读取超时，不能无限等</li>
 *   <li>异常不外抛：统一转换成 PayResult.fail，让上层逻辑简单</li>
 * </ol>
 */
public interface ChannelClient {

    Channel channel();

    /** 预下单，返回收银台地址或二维码内容 */
    PayResult prepay(PayRequest request);

    /** 主动查询（W2 掉单补偿用） */
    ChannelQueryResult query(String outTradeNo);

    /**
     * 关闭渠道侧交易（超时关单用）。
     *
     * <p>默认返回 true，是因为有些渠道没有关闭接口（或者关闭是幂等的空操作）。
     * 只有真正需要调用的渠道才覆写它——这就是接口默认方法的正确用法：
     * 给「大多数实现都一样」的行为一个默认值，而不是让每个实现都写一遍 return true。
     */
    default boolean close(String outTradeNo) {
        return true;
    }
}
