package com.payhub.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p><b>乐观锁插件</b>：让实体上的 {@code @Version} 真正生效。
 * 注册后，{@code updateById} 会被改写成：
 * <pre>
 *   UPDATE payment_order SET status = ?, ver = ver + 1
 *    WHERE id = ? AND ver = ?
 * </pre>
 * 返回 0 行 = 版本冲突 = 别人先改了。支付系统里「更新了 0 行」必须当成正常分支处理，
 * 而不是当成异常——因为并发回调下它本来就会发生。
 *
 * <p>注意：分页插件（PaginationInnerInterceptor）没有在这里注册。
 * MyBatis-Plus 3.5.9 起分页能力被拆到独立的 {@code mybatis-plus-jsqlparser} 依赖里，
 * W1 用不到，等 W5 做对账明细分页查询时再加，避免现在引入无用依赖。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
