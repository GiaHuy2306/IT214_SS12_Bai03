package com.bai03.controller;

import com.bai03.client.BankClient;
import com.bai03.dto.PaymentRequest;
import com.bai03.dto.PaymentResponse;
import com.bai03.service.PaymentService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final BankClient bankClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostMapping("/pay")
    public ResponseEntity<PaymentResponse> pay(@RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cb-status")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("bankClient");

        Map<String, Object> status = new HashMap<>();
        status.put("name", cb.getName());
        status.put("state", cb.getState().name());
        status.put("failureRate", cb.getMetrics().getFailureRate());
        status.put("numberOfBufferedCalls", cb.getMetrics().getNumberOfBufferedCalls());
        status.put("numberOfFailedCalls", cb.getMetrics().getNumberOfFailedCalls());
        status.put("numberOfSuccessfulCalls", cb.getMetrics().getNumberOfSuccessfulCalls());
        status.put("numberOfNotPermittedCalls", cb.getMetrics().getNumberOfNotPermittedCalls());

        return ResponseEntity.ok(status);
    }

    @PostMapping("/mock/bank-status")
    public ResponseEntity<Map<String, Object>> setMockBankStatus(@RequestParam boolean available) {
        bankClient.setBankAvailable(available);

        Map<String, Object> res = new HashMap<>();
        res.put("bankAvailable", available);
        res.put("message", available ? "Ngân hàng đối tác HOẠT ĐỘNG BÌNH THƯỜNG" : "Ngân hàng đối tác ĐANG BỊ SẬP (LỖI 503)");
        return ResponseEntity.ok(res);
    }
}
