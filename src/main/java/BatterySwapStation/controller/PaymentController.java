package BatterySwapStation.controller;

import BatterySwapStation.dto.VnPayCreatePaymentRequest;
import BatterySwapStation.dto.VnPayCreatePaymentResponse;
import BatterySwapStation.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * Controller xử lý thanh toán qua VNPAY (theo Invoice)
 */
@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
@PreAuthorize("permitAll()")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 🔹 API duy nhất: FE truyền invoiceId, BE tự tính totalAmount → tạo link thanh toán VNPAY
     */
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VnPayCreatePaymentResponse> createPayment(
            @RequestBody VnPayCreatePaymentRequest request,
            HttpServletRequest httpServletRequest) {

        String paymentUrl = paymentService.createVnPayPaymentUrlByInvoice(request, httpServletRequest);
        return ResponseEntity.ok(new VnPayCreatePaymentResponse(paymentUrl));
    }

    /** 🔹 API: IPN callback từ VNPAY */
    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> handleVnPayIpn(
            @RequestParam Map<String, String> queryParams) {

        Map<String, String> response = paymentService.handleVnPayIpn(queryParams);
        return ResponseEntity.ok(response);
    }

    /** 🔹 API: ReturnURL redirect từ VNPAY (hiển thị kết quả cho người dùng) */
    @GetMapping("/return")
    public void handleVnPayReturn(
            @RequestParam Map<String, String> queryParams,
            HttpServletResponse response) throws IOException {

        Map<String, Object> result = paymentService.handleVnPayReturn(queryParams);

        String status = (Boolean.TRUE.equals(result.get("success"))) ? "success" : "failed";
        String amount = (String) result.getOrDefault("vnp_Amount", "0");
        String message = (String) result.getOrDefault("message", "");

        // 👇 FE của bạn (localhost)
        String redirectUrl = String.format(
                "http://localhost:5173/driver/payment?status=%s&amount=%s&message=%s",
                status, amount, java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8)
        );

        response.sendRedirect(redirectUrl);
    }
}
