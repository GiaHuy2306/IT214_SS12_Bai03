package com.bai03;

import com.bai03.client.BankClient;
import com.bai03.dto.PaymentRequest;
import com.bai03.dto.PaymentResponse;
import com.bai03.service.PaymentService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PaymentCircuitBreakerTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BankClient bankClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("bankClient");
        circuitBreaker.reset();
    }

    @Test
    @DisplayName("Kiểm tra các thông số cấu hình của Circuit Breaker 'bankClient'")
    void testCircuitBreakerConfiguration() {
        CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();

        // 1. Sliding Window Size = 20
        assertEquals(20, config.getSlidingWindowSize());

        // 2. Failure Rate Threshold = 50%
        assertEquals(50.0f, config.getFailureRateThreshold());

        // 3. Wait Duration in Open State = 30s (30000ms)
        assertEquals(30000L, config.getWaitIntervalFunctionInOpenState().apply(1));

        // 4. Permitted Calls in Half Open State = 3
        assertEquals(3, config.getPermittedNumberOfCallsInHalfOpenState());

        // 5. Kiểm tra nguyên tắc: minimumNumberOfCalls >= permittedNumberOfCallsInHalfOpenState
        assertTrue(config.getMinimumNumberOfCalls() >= config.getPermittedNumberOfCallsInHalfOpenState(),
                "Vi phạm nguyên tắc: minimumNumberOfCalls phải >= permittedNumberOfCallsInHalfOpenState");
        assertEquals(5, config.getMinimumNumberOfCalls());
    }

    @Test
    @DisplayName("Kịch bản đêm: 5 khách hàng thanh toán và cả 5 đều bị lỗi -> Cầu dao lập tức MỞ MẠCH (OPEN)")
    void testNightScenario_FiveFailedCalls_CircuitBreakerOpens() {
        // Giả lập ngân hàng đối tác bị sập (lỗi 503)
        bankClient.setBankAvailable(false);

        // 5 khách hàng thực hiện thanh toán trong đêm
        for (int i = 1; i <= 5; i++) {
            PaymentRequest request = PaymentRequest.builder()
                    .orderId("ORDER-NIGHT-" + i)
                    .amount(BigDecimal.valueOf(100_000 * i))
                    .bankCode("VCB")
                    .build();

            PaymentResponse response = paymentService.processPayment(request);
            assertTrue(response.isFallback());
        }

        // Sau đúng 5 cuộc gọi bị lỗi (100% lỗi > 50% threshold và đủ minimumNumberOfCalls = 5)
        // Circuit Breaker PHẢI chuyển sang trạng thái OPEN
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(),
                "Circuit Breaker phải mở mạch ngay khi đủ 5 cuộc gọi lỗi!");

        // Khách hàng thứ 6 gọi tới khi mạch đang OPEN:
        // Hệ thống lập tức ngắt mạch, chặn gọi ngân hàng, trả về thông báo bảo trì 30 giây
        PaymentRequest request6 = PaymentRequest.builder()
                .orderId("ORDER-NIGHT-6")
                .amount(BigDecimal.valueOf(600_000))
                .bankCode("VCB")
                .build();

        PaymentResponse response6 = paymentService.processPayment(request6);
        assertEquals("BANK_MAINTENANCE", response6.getStatus());
        assertTrue(response6.getMessage().contains("30s"));
        assertTrue(response6.isFallback());
    }

    @Test
    @DisplayName("Khi Ngân hàng hoạt động bình thường -> Thanh toán thành công, mạch ở trạng thái CLOSED")
    void testPaymentSuccess_CircuitClosed() {
        bankClient.setBankAvailable(true);

        PaymentRequest request = PaymentRequest.builder()
                .orderId("ORDER-SUCCESS-01")
                .amount(BigDecimal.valueOf(250_000))
                .bankCode("MB")
                .build();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertFalse(response.isFallback());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }
}
