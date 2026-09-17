package com.bai03.client;

import com.bai03.dto.PaymentRequest;
import com.bai03.dto.PaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class BankClient {

    private volatile boolean bankAvailable = true;

    public void setBankAvailable(boolean available) {
        this.bankAvailable = available;
    }

    public boolean isBankAvailable() {
        return this.bankAvailable;
    }

    /**
     * Gọi sang API của Ngân hàng đối tác để trừ tiền
     */
    public PaymentResponse callBankApi(PaymentRequest request) {
        if (!bankAvailable) {
            log.error("Lỗi: Không thể kết nối tới Ngân hàng đối tác hoặc Ngân hàng trả về lỗi 503");
            throw new RuntimeException("Ngân hàng đối tác phản hồi lỗi 503 Service Unavailable");
        }

        log.info("Giao dịch thanh toán thành công qua Ngân hàng đối tác cho đơn hàng {}", request.getOrderId());
        return PaymentResponse.builder()
                .transactionId("BANK-" + UUID.randomUUID())
                .orderId(request.getOrderId())
                .status("SUCCESS")
                .message("Thanh toán thành công qua ngân hàng đối tác")
                .isFallback(false)
                .build();
    }
}
