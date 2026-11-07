package com.payhub.core.service;

import com.payhub.common.BizException;
import com.payhub.common.IdGenerator;
import com.payhub.core.channel.ChannelClient;
import com.payhub.core.channel.ChannelRouter;
import com.payhub.core.channel.model.ChannelQueryResult;
import com.payhub.core.channel.model.PayRequest;
import com.payhub.core.channel.model.PayResult;
import com.payhub.core.command.CreatePaymentCommand;
import com.payhub.core.entity.PaymentOrder;
import com.payhub.core.enums.AdvanceOutcome;
import com.payhub.core.enums.Channel;
import com.payhub.core.enums.PaymentStatus;
import com.payhub.core.mapper.PaymentOrderMapper;
import com.payhub.merchant.entity.MerchantApp;
import com.payhub.merchant.service.MerchantAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付单核心服务。
 *
 * <p>W1 解决「怎么下单」，W2 解决「怎么可信地改状态」。核心变化只有一个：
 * <b>所有状态推进收敛到一个方法 {@link #advanceToSuccess}</b>，
 * 回调、主动查询、补偿任务三条入口全部走它。
 *
 * <p>为什么必须收敛？
 * 因为「推进成功」这件事包含三个动作：CAS 更新 → 记 outbox → 幂等判断。
 * 如果回调一套、补偿一套，两边迟早会漂移：一边记了 outbox，另一边忘了；
 * 或者一边判断了状态机，另一边直接 update。这类 bug 在对账时才会暴露，非常贵。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final MerchantAppService merchantAppService;
    private final ChannelRouter channelRouter;
    private final LocalMessageService localMessageService;
    private final IdGenerator idGenerator;

    @Value("${payhub.order.expire-minutes:30}")
    private int expireMinutes;

    /**
     * 统一下单。
     *
     * <p>幂等三层：先查 → 唯一索引 → 捕获冲突回查。
     * 注意「不把外部调用放进事务」：这里刻意没有 @Transactional，
     * 每一步都是独立提交，因为中间夹着一次渠道 RPC。
     */
    public PaymentOrder create(CreatePaymentCommand command) {
        MerchantApp app = merchantAppService.mustGet(command.merchantNo(), command.appId());

        PaymentOrder exist = paymentOrderMapper.selectByMerchantOutTradeNo(
                command.merchantNo(), command.outTradeNo());
        if (exist != null) {
            log.info("下单幂等命中 outTradeNo={} paymentNo={}",
                    command.outTradeNo(), exist.getPaymentNo());
            return exist;
        }

        Channel channel = Channel.of(command.channel());
        ChannelClient channelClient = channelRouter.route(channel);

        PaymentOrder order = buildOrder(command, channel);
        try {
            paymentOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.warn("并发下单冲突，回落查已有单 outTradeNo={}", command.outTradeNo());
            PaymentOrder concurrent = paymentOrderMapper.selectByMerchantOutTradeNo(
                    command.merchantNo(), command.outTradeNo());
            if (concurrent == null) {
                throw new BizException("2005", "下单冲突，请重试");
            }
            return concurrent;
        }

        String notifyUrl = command.notifyUrl() != null && !command.notifyUrl().isBlank()
                ? command.notifyUrl() : app.getNotifyUrl();
        PayResult payResult = channelClient.prepay(new PayRequest(
                order.getPaymentNo(), order.getOutTradeNo(), order.getAmount(),
                order.getSubject(), notifyUrl, expireMinutes));

        if (!payResult.success()) {
            order.setStatus(PaymentStatus.FAILED.name());
            paymentOrderMapper.updateById(order);
            log.warn("预下单失败 paymentNo={} code={} msg={}",
                    order.getPaymentNo(), payResult.errorCode(), payResult.errorMsg());
            throw new BizException("2003", "渠道下单失败：" + payResult.errorMsg());
        }

        order.setStatus(PaymentStatus.PAYING.name());
        order.setPayUrl(payResult.payUrl());
        order.setTradeNo(payResult.channelTradeNo());
        paymentOrderMapper.updateById(order);
        log.info("预下单成功 paymentNo={} channel={} payUrl={}",
                order.getPaymentNo(), channel.getCode(), order.getPayUrl());
        return order;
    }

    /**
     * <b>唯一的状态推进入口。</b>回调、主动查询、补偿任务都必须走这里。
     *
     * <p>事务边界很关键：CAS 更新 + 写 outbox 必须在同一个事务里，
     * 否则会出现「状态改了但消息没发」——账务永远收不到这笔钱。
     *
     * @return 推进结果，调用方据此决定给渠道什么应答、要不要告警
     */
    @Transactional(rollbackFor = Exception.class)
    public AdvanceOutcome advanceToSuccess(String paymentNo, String tradeNo,
                                           LocalDateTime payTime, String source) {
        PaymentOrder order = paymentOrderMapper.selectByPaymentNo(paymentNo);
        if (order == null) {
            return AdvanceOutcome.NOT_FOUND;
        }
        PaymentStatus current = PaymentStatus.valueOf(order.getStatus());
        if (current == PaymentStatus.SUCCESS) {
            return AdvanceOutcome.ALREADY_SUCCESS;
        }
        // 状态机是第三道防线：已关闭/已失败的单绝不能被回调改成成功
        if (!current.canTransitTo(PaymentStatus.SUCCESS)) {
            log.warn("[{}] 状态机拒绝推进 paymentNo={} {} -> SUCCESS", source, paymentNo, current);
            return AdvanceOutcome.STATE_REJECTED;
        }

        int updated = paymentOrderMapper.casUpdateSuccess(
                paymentNo, order.getVer(), tradeNo, payTime, LocalDateTime.now());
        if (updated == 0) {
            // 并发下别人已经推进成功，这不是错误，是幂等的正常分支
            log.info("[{}] CAS 未命中，可能是并发重复通知 paymentNo={}", source, paymentNo);
            return AdvanceOutcome.ALREADY_SUCCESS;
        }

        localMessageService.append(LocalMessageService.BIZ_PAYMENT_SUCCESS, paymentNo,
                "{\"paymentNo\":\"" + paymentNo + "\",\"tradeNo\":\"" + tradeNo
                        + "\",\"source\":\"" + source + "\"}");
        log.info("[{}] 支付成功 paymentNo={} tradeNo={}", source, paymentNo, tradeNo);
        return AdvanceOutcome.ADVANCED;
    }

    /**
     * 主动查询渠道并推进（掉单补偿的唯一入口）。
     *
     * <p>注意它和回调共用 {@link #advanceToSuccess}——
     * 这就是「回调与查询复用同一段逻辑」的落地方式。
     */
    public AdvanceOutcome compensate(String paymentNo) {
        PaymentOrder order = paymentOrderMapper.selectByPaymentNo(paymentNo);
        if (order == null) {
            return AdvanceOutcome.NOT_FOUND;
        }
        PaymentStatus current = PaymentStatus.valueOf(order.getStatus());
        if (current == PaymentStatus.SUCCESS) {
            return AdvanceOutcome.ALREADY_SUCCESS;
        }
        if (current != PaymentStatus.PAYING) {
            return AdvanceOutcome.STATE_REJECTED;
        }
        ChannelClient client = channelRouter.route(Channel.valueOf(order.getChannel()));
        ChannelQueryResult queryResult = client.query(order.getOutTradeNo());
        if (!queryResult.found() || !queryResult.paid()) {
            return AdvanceOutcome.CHANNEL_UNPAID;
        }
        return advanceToSuccess(paymentNo, queryResult.channelTradeNo(),
                LocalDateTime.now(), "QUERY");
    }

    /** 超时关单：CAS 到 CLOSED，并记 outbox（W4 账务要消费这个事件做额度释放） */
    @Transactional(rollbackFor = Exception.class)
    public boolean closePayment(String paymentNo, String reason) {
        PaymentOrder order = paymentOrderMapper.selectByPaymentNo(paymentNo);
        if (order == null) {
            return false;
        }
        PaymentStatus current = PaymentStatus.valueOf(order.getStatus());
        if (!current.canTransitTo(PaymentStatus.CLOSED)) {
            return false;
        }
        int updated = paymentOrderMapper.casClose(paymentNo, order.getVer(), LocalDateTime.now());
        if (updated == 0) {
            return false;
        }
        localMessageService.append(LocalMessageService.BIZ_PAYMENT_CLOSED, paymentNo,
                "{\"paymentNo\":\"" + paymentNo + "\",\"reason\":\"" + reason + "\"}");
        log.info("支付单关闭 paymentNo={} reason={}", paymentNo, reason);
        return true;
    }

    public PaymentOrder query(String paymentNo) {
        PaymentOrder order = paymentOrderMapper.selectByPaymentNo(paymentNo);
        if (order == null) {
            throw new BizException("2004", "支付单不存在: " + paymentNo);
        }
        return order;
    }

    public PaymentOrder getByPaymentNo(String paymentNo) {
        return paymentOrderMapper.selectByPaymentNo(paymentNo);
    }

    public PaymentOrder getByMerchantOutTradeNo(String merchantNo, String outTradeNo) {
        return paymentOrderMapper.selectByMerchantOutTradeNo(merchantNo, outTradeNo);
    }

    private PaymentOrder buildOrder(CreatePaymentCommand command, Channel channel) {
        LocalDateTime now = LocalDateTime.now();
        PaymentOrder order = new PaymentOrder();
        order.setPaymentNo(idGenerator.nextPaymentNo());
        order.setMerchantNo(command.merchantNo());
        order.setAppId(command.appId());
        order.setOutTradeNo(command.outTradeNo());
        order.setChannel(channel.name());
        order.setAmount(command.amount());
        order.setCurrency("CNY");
        order.setSubject(command.subject());
        order.setStatus(PaymentStatus.CREATED.name());
        order.setExpireTime(now.plusMinutes(expireMinutes));
        order.setVer(0);
        return order;
    }
}
