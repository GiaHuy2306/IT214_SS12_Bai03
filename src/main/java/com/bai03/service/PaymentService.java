package com.bai03.service;

import com.bai03.client.BankClient;
import com.bai03.dto.PaymentRequest;
import com.bai03.dto.PaymentResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BankClient bankClient;

    /**
     * Xử lý thanh toán gọi sang Ngân hàng đối tác.
     * Được bảo vệ bởi Circuit Breaker 'bankClient'
     */
    @CircuitBreaker(name = "bankClient", fallbackMethod = "processPaymentFallback")
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Bắt đầu xử lý thanh toán đơn hàng {}", request.getOrderId());
        return bankClient.callBankApi(request);
    }

    /**
     * Phương thức Fallback bảo vệ hệ thống và tuân thủ yêu cầu ngắt kết nối của ngân hàng
     */
    public PaymentResponse processPaymentFallback(PaymentRequest request, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.warn("[CIRCUIT BREAKER OPEN] Cầu dao 'bankClient' ĐANG MỞ! Chặn gọi ngân hàng đối tác trong 30s để họ khắc phục sự cố. OrderId: {}",
                    request.getOrderId());
            return PaymentResponse.builder()
                    .orderId(request.getOrderId())
                    .status("BANK_MAINTENANCE")
                    .message("Hệ thống ngân hàng đối tác đang bảo trì (tạm ngắt kết nối 30s). Quý khách vui lòng thử lại sau.")
                    .isFallback(true)
                    .build();
        }

        log.error("[PAYMENT ERROR] Lỗi khi gọi thanh toán đơn hàng {}: {}", request.getOrderId(), throwable.getMessage());
        return PaymentResponse.builder()
                .orderId(request.getOrderId())
                .status("PAYMENT_FAILED")
                .message("Thanh toán thất bại: " + throwable.getMessage())
                .isFallback(true)
                .build();
    }
}
