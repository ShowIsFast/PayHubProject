package com.payhub.openapi.controller;

import com.payhub.common.ApiResult;
import com.payhub.core.channel.mock.MockChannelClient;
import com.payhub.core.entity.PaymentOrder;
import com.payhub.core.enums.Channel;
import com.payhub.core.enums.NotifyResult;
import com.payhub.core.notify.NotifyService;
import com.payhub.core.notify.mock.MockChannelSigner;
import com.payhub.core.service.PaymentOrderService;
import com.payhub.openapi.support.MockPlaygroundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地模拟收银台 + W2 折腾台。
 *
 * <p>它扮演「支付宝收银台 + 支付宝服务器」两个角色：
 * 用户点付款 → 我们构造通知报文 → <b>用渠道私钥签名</b> → POST 到自己的 notify 接口。
 * 走的是和真实支付宝完全一样的链路（含验签），只是密钥是本地生成的。
 *
 * <p>这就是为什么在 Mock 模式下你依然能学到真东西：
 * 把 amount 改掉再发，回调和真实环境一样会被拒。
 */
@Controller
@RequiredArgsConstructor
public class MockCashierController {

    private static final String TAMPER_NONE = "none";
    private static final String TAMPER_SIGN = "sign";
    private static final String TAMPER_AMOUNT = "amount";

    private final PaymentOrderService paymentOrderService;
    private final MockChannelClient mockChannelClient;
    private final MockChannelSigner mockChannelSigner;
    private final NotifyService notifyService;
    private final MockPlaygroundService playground;

    // ==================== 收银台 ====================

    @GetMapping(value = "/mock/cashier", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String cashier(@RequestParam String paymentNo) {
        PaymentOrder order = paymentOrderService.getByPaymentNo(paymentNo);
        if (order == null) {
            return page("支付单不存在", "<p>paymentNo=" + paymentNo + "</p>");
        }
        String yuan = BigDecimal.valueOf(order.getAmount(), 2).toPlainString();
        String body = """
                <h2>模拟支付宝收银台</h2>
                <p>商品：%s</p>
                <p>金额：<b style="color:#d33;font-size:24px">￥%s</b></p>
                <p>支付单号：%s</p>
                <p>状态：%s</p>
                <form method="post" action="/mock/cashier/pay">
                  <input type="hidden" name="paymentNo" value="%s"/>
                  <button type="submit" style="padding:10px 30px;font-size:18px;background:#1677ff;color:#fff;border:0;border-radius:6px;cursor:pointer">
                    确认支付
                  </button>
                </form>
                """.formatted(order.getSubject(), yuan, order.getPaymentNo(), order.getStatus(), order.getPaymentNo());
        return page("模拟收银台", body);
    }

    @PostMapping(value = "/mock/cashier/pay", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String pay(@RequestParam String paymentNo) {
        PaymentOrder order = playground.mustGet(paymentNo);
        String tradeNo = mockChannelClient.markPaid(order.getOutTradeNo());
        Map<String, String> signed = mockChannelSigner.sign(
                playground.buildNotifyParams(order, tradeNo));
        NotifyResult result = notifyService.handle(Channel.MOCK, signed);
        String current = paymentOrderService.getByPaymentNo(paymentNo).getStatus();
        String body = """
                <h2>%s</h2>
                <p>渠道交易号：%s</p>
                <p>回调处理结果：<b>%s</b>（%s）</p>
                <p>支付单当前状态：<b>%s</b></p>
                <p><a href="/openapi/pay/query/%s">查询支付单</a></p>
                """.formatted(result == NotifyResult.SUCCESS ? "支付成功" : "支付未成功",
                tradeNo, result.name(), result.getDesc(), current, paymentNo);
        return page("支付结果", body);
    }

    // ==================== W2 折腾台 ====================

    /**
     * 手动发一条回调通知。
     *
     * @param tamper none  正常通知
     *             sign  签名后再篡改金额 → 应验签失败
     *             amount 用篡改后的金额签名 → 应金额不一致
     */
    @PostMapping("/mock/notify/send")
    @ResponseBody
    public ApiResult<Map<String, Object>> sendNotify(@RequestParam String paymentNo,
                                                     @RequestParam(defaultValue = TAMPER_NONE) String tamper) {
        PaymentOrder order = playground.mustGet(paymentNo);
        String tradeNo = mockChannelClient.markPaid(order.getOutTradeNo());
        Map<String, String> params = new HashMap<>(playground.buildNotifyParams(order, tradeNo));

        if (TAMPER_AMOUNT.equals(tamper)) {
            params.put("total_amount", "0.01");
        }
        Map<String, String> signed = mockChannelSigner.sign(params);
        if (TAMPER_SIGN.equals(tamper)) {
            signed.put("total_amount", "0.01");
        }

        NotifyResult result = notifyService.handle(Channel.MOCK, signed);
        Map<String, Object> data = new HashMap<>();
        data.put("notifyKey", signed.get("out_trade_no") + ":" + signed.get("trade_status"));
        data.put("result", result.name());
        data.put("desc", result.getDesc());
        data.put("channelResponse", result.getChannelResponse());
        data.put("orderStatus", paymentOrderService.getByPaymentNo(paymentNo).getStatus());
        return ApiResult.ok(data);
    }

    /** 把支付单改成已过期，等关单任务处理（或直接调 /mock/order/close） */
    @PostMapping("/mock/order/expire/{paymentNo}")
    @ResponseBody
    public ApiResult<Void> expire(@PathVariable String paymentNo) {
        playground.expire(paymentNo);
        return ApiResult.ok(null);
    }

    /** 模拟掉单：渠道已付款但回调丢失 */
    @PostMapping("/mock/order/drop/{paymentNo}")
    @ResponseBody
    public ApiResult<String> drop(@PathVariable String paymentNo) {
        String tradeNo = playground.dropNotification(paymentNo);
        return ApiResult.ok(tradeNo);
    }

    /** 手动触发一次补偿（等价于定时任务跑一轮） */
    @PostMapping("/mock/order/compensate/{paymentNo}")
    @ResponseBody
    public ApiResult<Map<String, Object>> compensate(@PathVariable String paymentNo) {
        var outcome = paymentOrderService.compensate(paymentNo);
        Map<String, Object> data = new HashMap<>();
        data.put("outcome", outcome.name());
        data.put("desc", outcome.getDesc());
        data.put("orderStatus", paymentOrderService.getByPaymentNo(paymentNo).getStatus());
        return ApiResult.ok(data);
    }

    /** 手动关单（跳过等待过期） */
    @PostMapping("/mock/order/close/{paymentNo}")
    @ResponseBody
    public ApiResult<Boolean> close(@PathVariable String paymentNo) {
        return ApiResult.ok(paymentOrderService.closePayment(paymentNo, "MANUAL"));
    }

    private String page(String title, String body) {
        return """
                <!doctype html>
                <html lang="zh-CN"><head><meta charset="utf-8"><title>%s</title></head>
                <body style="font-family:-apple-system,'PingFang SC',sans-serif;max-width:620px;margin:60px auto;line-height:1.9">
                %s
                </body></html>
                """.formatted(title, body);
    }
}
