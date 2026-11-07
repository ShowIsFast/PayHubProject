package com.payhub.openapi.controller;

import com.payhub.common.ApiResult;
import com.payhub.core.command.CreatePaymentCommand;
import com.payhub.core.entity.PaymentOrder;
import com.payhub.core.service.PaymentOrderService;
import com.payhub.openapi.dto.PayQueryResponse;
import com.payhub.openapi.dto.UnifiedOrderRequest;
import com.payhub.openapi.dto.UnifiedOrderResponse;
import com.payhub.openapi.support.MerchantSignSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对商户开放的支付接口。
 *
 * <p>Controller 只做三件事：接参、验签、转调 service。
 * 业务逻辑一律不下沉到 Controller——否则以后加个定时任务调用同一个逻辑，就要复制粘贴。
 *
 * <p>注意这里多做了一步「DTO → Command」的转换。看着啰嗦，但它保证了
 * core 包不依赖 openapi 包（依赖方向只能向内），这是分层能拆得开的前提。
 */
@Slf4j
@RestController
@RequestMapping("/openapi/pay")
@RequiredArgsConstructor
public class PayOpenApiController {

    private final PaymentOrderService paymentOrderService;
    private final MerchantSignSupport merchantSignSupport;

    /**
     * 统一下单。
     *
     * <pre>
     * curl -X POST http://localhost:8080/openapi/pay/unifiedorder \
     *   -H "Content-Type: application/json" \
     *   -d '{"merchantNo":"M100001","appId":"APP100001","outTradeNo":"T20261106001",
     *        "amount":100,"subject":"测试商品","channel":"MOCK"}'
     *
     * 私钥：MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD42QApCJvfxAjLyOsJpAy6PV8Rqe7Q91/nz/xL+B1UX3UgLonmSN5yBNcjE9LXn6Q23/9WjMwVUNmu16ziEU+Geegkzh1XrMgumVAzu1AmSAeomirlWekbPgvc1pjjS6enMkGdzplW93qS/Iu7a5RvuI9AhrQ1y/N93asJZGx70CRyUBtSYYPkF6q7jIqxTIcM49KNGpLKELagK0T450Hel9hYPVl6vCk1hzkAJ4BjUyhcwQcBekrPAbYBuoNRJvhb2PiHXWmtwSKJvo/E7Itk0xyIGfSPEsKy+8OgK+FNm4Rx643RKqb+b5rmfJJmf7WJoJz9VZvOyXMA5O+6IWUFAgMBAAECggEAAx6qaVlJP4ahFKsZ4R7DTFpOSR5fg+ApztAHWfPONLpbHDFpLE/ToMeVOOaGjopa3Ozby68FNKcTAOnrw5TeLVXsyqREnhtXmr44t3zIdihE9Ngj0u9Hqsa77awnNjU6To5G+bgS3ImgFIs1AN0n+xWnoINv0dC1M0equ08/CMyWEo6sWI+lrr163JdZoaKwMJV9O0ryzOu+59EeHSccX7Z5gAWK0Yo6DkjYWneHUlg4AnU1U4ViNZY+j5AWWxx4LdWvM+ZxLSo951dA6q7a/o5ddhyZbgw+uP4qgXAZhp90kwyO7Kjdt8qaHzu35VZp5uAw8ulwPiwjb0sZBlJ5ywKBgQD52l9JTVlTpELRx7Zv0sYPIWcEUcLHn1utNntk6cpcnZB0F+XQ+9uPwh8Y/QOw1dOVyHNNfdefkPWarPWW4gq3cWh/oMfasNclqUntVeegIuTQoMiu8zhMjn4sVV77Thwux0mxdj3lT0sttYYr+KUlJyKCHEKQ63nclWwHds8CTwKBgQD++EvkgY9gnrpaSSGiS1zNlJM+l1jC+LoDg9TO2TvjjcPT7mJ3onSWl1dkXjKtKcrSWwKGoqT3EWnATr2odZVNpcJKWzaeY01WgFvLYkjUF/QgvKourDAMwZmyC3n1TLjHiQYiObFzPjC4GoGU7gWsc1SF3M9MsGfLcJ0QqFgyawKBgQDB117Hwv/DeZPxpyzuMqtAf9ypqDkioIsCplszXoKGOiQhPY0qBsxACw4PV/SuFCa0dTCndTN9c3xoxTlAFxzTV2QIaJfFHh+880sMm/I49flptuQbf001b/OJt64Z8G7DCBAvYxoANvWPf96ZbK3LVkJaiUxHnLkNnsuSOuW91wKBgHY1R3o5Vu/Ep0EUhrNnnS7yoxFxuYUrK5JVnGQJ8uoc351x99TfTI5EvA+cXIJLzeyYpSMmZLTnLdAqsWNYoE/PebRfdYMZJtHj0KsKdhlewaAr1/qredtwJoGVMIW5fDGGJYHYnZyASLYSE+GoOJkrHxSwuqsjXfvDyg3e/oThAoGBAIOPCa7cT1NM94ZOBpsHjNbtIdNzSRKc7CD462Wysw6+3s/YotqyktSzEZcMojRPjTBkxFsfu+QgUeKtS/fAs7/X8zR/QmReqcNarmHkFp1RA3JC4BF4S+8NA0waMiMKlOdOmkaVFF0H7W57hOuZmD57/bkGTx9RnangWcF8RjlL
     * 公钥：MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA+NkAKQib38QIy8jrCaQMuj1fEanu0Pdf58/8S/gdVF91IC6J5kjecgTXIxPS15+kNt//VozMFVDZrtes4hFPhnnoJM4dV6zILplQM7tQJkgHqJoq5VnpGz4L3NaY40unpzJBnc6ZVvd6kvyLu2uUb7iPQIa0Ncvzfd2rCWRse9AkclAbUmGD5Bequ4yKsUyHDOPSjRqSyhC2oCtE+OdB3pfYWD1ZerwpNYc5ACeAY1MoXMEHAXpKzwG2AbqDUSb4W9j4h11prcEiib6PxOyLZNMciBn0jxLCsvvDoCvhTZuEceuN0Sqm/m+a5nySZn+1iaCc/VWbzslzAOTvuiFlBQIDAQAB
     * ----------------------------------------
     * </pre>
     */
    @PostMapping("/unifiedorder")
    public ApiResult<UnifiedOrderResponse> unifiedOrder(@RequestBody @Valid UnifiedOrderRequest request) {
        merchantSignSupport.verifyIfEnabled(request);
        CreatePaymentCommand command = new CreatePaymentCommand(
                request.merchantNo(), request.appId(), request.outTradeNo(),
                request.amount(), request.subject(), request.channel(), request.notifyUrl());
        PaymentOrder order = paymentOrderService.create(command);
        return ApiResult.ok(UnifiedOrderResponse.of(order));
    }

    /** 查询支付单（本地查，不调渠道；主动查渠道在 W2） */
    @GetMapping("/query/{paymentNo}")
    public ApiResult<PayQueryResponse> query(@PathVariable String paymentNo) {
        return ApiResult.ok(PayQueryResponse.of(paymentOrderService.query(paymentNo)));
    }
}
