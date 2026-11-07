package com.payhub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PayHubProject 启动类。
 *
 * <p>W1 的目标只有一件事：让一笔支付从「商户请求」走到「拿到支付链接」。
 * 所以这里刻意只保留四层，包名即分层：
 *
 * <pre>
 *   openapi  ← 对外接口（签名校验、参数校验、协议转换）
 *      ↓
 *   core     ← 支付域（支付单、渠道适配、状态）
 *      ↓
 *   merchant ← 商户应用（谁在调我）
 *      ↓
 *   common   ← 基础设施（单号、签名、统一返回）
 * </pre>
 *
 * 现在是一个单体应用，包边界就是未来的服务边界：
 * pay-openapi / pay-core / pay-merchant 随时可以拆出去。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan({"com.payhub.core.mapper", "com.payhub.merchant.mapper"})
public class PayHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayHubApplication.class, args);
    }
}
