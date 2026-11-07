package com.payhub.core.channel.mock;

import com.payhub.core.channel.ChannelClient;
import com.payhub.core.channel.model.ChannelQueryResult;
import com.payhub.core.channel.model.PayRequest;
import com.payhub.core.channel.model.PayResult;
import com.payhub.core.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地模拟渠道。
 *
 * <p>存在的意义：<b>让你在没有支付宝账号、没有网络的情况下，把整条链路跑通</b>。
 * 支付系统的调试成本很高（真实渠道有沙箱、限流、白名单），
 * 工程上标准的做法就是在渠道适配层下面挂一个 mock 实现，本地全链路可控。
 *
 * <p>它模拟了渠道最关键的两个行为：
 * <ol>
 *   <li>prepay 返回一个「收银台地址」</li>
 *   <li>用户在收银台付款后，渠道回调我们（由 /mock/cashier/pay 触发）</li>
 * </ol>
 */
@Slf4j
@Component
public class MockChannelClient implements ChannelClient {

    private final String baseUrl;

    /**
     * outTradeNo -> 渠道交易号；有值即视为「用户已付款」。
     *
     * <p><b>注意 key 是 outTradeNo 而不是 paymentNo。</b>
     * 渠道只认识「商户订单号」，它根本不知道我们的 paymentNo 是什么。
     * 这是支付系统最经典的一个坑：两套 ID 的映射关系一旦搞混，
     * 表现就是「用户明明付了钱，补偿任务查回来却是未支付」。
     */
    private final Map<String, String> paidTradeNos = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1000);

    public MockChannelClient(@Value("${payhub.mock.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Channel channel() {
        return Channel.MOCK;
    }

    @Override
    public PayResult prepay(PayRequest request) {
        String tradeNo = "MOCK" + System.currentTimeMillis() + seq.incrementAndGet();
        String payUrl = baseUrl + "/mock/cashier?paymentNo=" + request.paymentNo();
        log.info("[MOCK] 预下单成功 paymentNo={} payUrl={}", request.paymentNo(), payUrl);
        return PayResult.ok(tradeNo, payUrl);
    }

    @Override
    public ChannelQueryResult query(String outTradeNo) {
        String tradeNo = paidTradeNos.get(outTradeNo);
        return tradeNo == null ? ChannelQueryResult.unpaid("WAIT_BUYER_PAY")
                : ChannelQueryResult.paid(tradeNo, "TRADE_SUCCESS");
    }

    /** 模拟「用户点了付款」——由本地收银台调用。注意入参是商户订单号 */
    public String markPaid(String outTradeNo) {
        return paidTradeNos.computeIfAbsent(outTradeNo,
                k -> "MOCK" + System.currentTimeMillis() + seq.incrementAndGet());
    }

    public boolean isPaid(String outTradeNo) {
        return paidTradeNos.containsKey(outTradeNo);
    }
}
