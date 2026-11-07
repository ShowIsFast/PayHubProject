package com.payhub.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payhub.core.entity.PaymentOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    @Select("SELECT * FROM payment_order WHERE payment_no = #{paymentNo}")
    PaymentOrder selectByPaymentNo(@Param("paymentNo") String paymentNo);

    @Select("SELECT * FROM payment_order WHERE merchant_no = #{merchantNo} AND out_trade_no = #{outTradeNo}")
    PaymentOrder selectByMerchantOutTradeNo(@Param("merchantNo") String merchantNo,
                                            @Param("outTradeNo") String outTradeNo);

    /**
     * 乐观锁推进到 SUCCESS。
     *
     * <p>三个条件缺一不可：
     * <pre>
     *   payment_no = ?     定位
     *   ver = ?            版本没被别人改过（并发唯一性）
     *   status = 'PAYING'  状态还允许推进（防御性冗余）
     * </pre>
     * 第二个条件保证「并发只有一个成功」，第三个条件保证「就算 ver 被绕过也不会脏写」。
     * 返回 0 行不是异常，是「别人先推进了」，属于正常分支。
     */
    @Update("""
            UPDATE payment_order
               SET status = 'SUCCESS', trade_no = #{tradeNo}, pay_time = #{payTime},
                   ver = ver + 1, update_time = #{now}
             WHERE payment_no = #{paymentNo} AND ver = #{ver} AND status = 'PAYING'
            """)
    int casUpdateSuccess(@Param("paymentNo") String paymentNo,
                         @Param("ver") Integer ver,
                         @Param("tradeNo") String tradeNo,
                         @Param("payTime") LocalDateTime payTime,
                         @Param("now") LocalDateTime now);

    /** 乐观锁关闭（超时关单用） */
    @Update("""
            UPDATE payment_order
               SET status = 'CLOSED', ver = ver + 1, update_time = #{now}
             WHERE payment_no = #{paymentNo} AND ver = #{ver} AND status = 'PAYING'
            """)
    int casClose(@Param("paymentNo") String paymentNo,
                 @Param("ver") Integer ver,
                 @Param("now") LocalDateTime now);

    /**
     * 掉单补偿候选：PAYING 且超过 delay 秒没有更新、但还没过期。
     *
     * <p>为什么要卡 update_time？因为「刚下单 1 秒」的单子大概率正在支付中，
     * 立刻去查渠道只是浪费一次 RPC。给一个延迟窗口，能过滤掉 90% 的无效查询。
     */
    @Select("""
            SELECT * FROM payment_order
             WHERE status = 'PAYING'
               AND update_time < #{deadline}
               AND (expire_time IS NULL OR expire_time > #{now})
             ORDER BY id LIMIT #{limit}
            """)
    List<PaymentOrder> selectPayingForCompensation(@Param("deadline") LocalDateTime deadline,
                                                   @Param("now") LocalDateTime now,
                                                   @Param("limit") int limit);

    /** 超时关单候选：PAYING 且已过 expire_time */
    @Select("""
            SELECT * FROM payment_order
             WHERE status = 'PAYING' AND expire_time IS NOT NULL AND expire_time <= #{now}
             ORDER BY id LIMIT #{limit}
            """)
    List<PaymentOrder> selectExpiredPaying(@Param("now") LocalDateTime now,
                                           @Param("limit") int limit);

    /** 通用状态更新（测试/演示用）：直接按支付单号改成指定状态 */
    @Update("""
            UPDATE payment_order
               SET status = #{status}, ver = ver + 1, update_time = #{now}
             WHERE payment_no = #{paymentNo}
            """)
    int forceStatus(@Param("paymentNo") String paymentNo,
                    @Param("status") String status,
                    @Param("now") LocalDateTime now);
}
