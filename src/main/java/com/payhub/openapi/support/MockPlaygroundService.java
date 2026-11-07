package com.payhub.openapi.support;

import com.payhub.common.BizException;
import com.payhub.core.channel.mock.MockChannelClient;
import com.payhub.core.entity.PaymentOrder;
import com.payhub.core.mapper.PaymentOrderMapper;
import com.payhub.core.service.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地「折腾台」：把 W2 想演示的场景做成接口，方便你手动触发。
 *
 * <p>它只服务本地开发，代码写得不讲究（直接操作 mapper），
 * 但每个方法都对应一个真实场景：
 * <ul>
 *   <li>{@code expire}：把支付单改成已过期 → 观察关单任务</li>
 *   <li>{@code dropNotification}：模拟用户付了钱但回调丢了 → 观察补偿任务</li>
 *   <li>{@code buildNotifyParams}：构造一条符合渠道协议的通知报文</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MockPlaygroundService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOrderService paymentOrderService;
    private final MockChannelClient mockChannelClient;

    public PaymentOrder mustGet(String paymentNo) {
        PaymentOrder order = paymentOrderService.getByPaymentNo(paymentNo);
        if (order == null) {
            throw new BizException("2004", "支付单不存在: " + paymentNo);
        }
        return order;
    }

    /** 把支付单改成 1 分钟前就过期（不用等 30 分钟） */
    public PaymentOrder expire(String paymentNo) {
        PaymentOrder order = mustGet(paymentNo);
        order.setExpireTime(LocalDateTime.now().minusMinutes(1));
        paymentOrderMapper.updateById(order);
        return order;
    }

    /** 模拟「用户已付款，但渠道回调丢了」：只在渠道侧标记已付，不发通知 */
    public String dropNotification(String paymentNo) {
        PaymentOrder order = mustGet(paymentNo);
        return mockChannelClient.markPaid(order.getOutTradeNo());
    }

    /** 直接置为 CLOSED（用于验证「已关闭的单不能被回调改成成功」） */
    public void forceClose(String paymentNo) {
        paymentOrderMapper.forceStatus(paymentNo, "CLOSED", LocalDateTime.now());
    }

    /** 构造一条标准通知报文（对应支付宝的异步通知字段） */
    public Map<String, String> buildNotifyParams(PaymentOrder order, String tradeNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", order.getAppId());
        params.put("out_trade_no", order.getOutTradeNo());
        params.put("trade_no", tradeNo);
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", BigDecimal.valueOf(order.getAmount(), 2).toPlainString());
        params.put("gmt_payment", LocalDateTime.now().format(TS));
        params.put("notify_id", "MOCK" + System.currentTimeMillis());
        return params;
    }
}
