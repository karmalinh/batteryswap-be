package BatterySwapStation.service;

import BatterySwapStation.config.VnPayProperties;
import BatterySwapStation.dto.VnPayCreatePaymentRequest;
import BatterySwapStation.entity.Booking;
import BatterySwapStation.entity.Payment;
import BatterySwapStation.repository.PaymentRepository;
import BatterySwapStation.utils.VnPayUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import BatterySwapStation.repository.InvoiceRepository;
import BatterySwapStation.entity.Invoice;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final VnPayProperties props;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;


    @Transactional
    public String createVnPayPaymentUrlByInvoice(
            VnPayCreatePaymentRequest req, HttpServletRequest http) {

        // 1️⃣ Kiểm tra invoice tồn tại
        Invoice invoice = invoiceRepository.findById(req.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + req.getInvoiceId()));

        // 2️⃣ Lấy tổng tiền hóa đơn
        double amount = invoice.getTotalAmount() == null ? 0d : invoice.getTotalAmount();
        if (amount <= 0) throw new IllegalArgumentException("Invoice totalAmount must be > 0");

        // 3️⃣ Chặn nếu hóa đơn đã thanh toán
        boolean alreadyPaid = paymentRepository.existsByInvoiceAndPaymentStatus(
                invoice, Payment.PaymentStatus.SUCCESS);
        if (alreadyPaid) {
            throw new IllegalStateException("Invoice already paid");
        }

        // 4️⃣ Sinh các thông tin cần cho VNPAY
        String ipAddr = VnPayUtils.getClientIp(http);
        String txnRef = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long amountTimes100 = Math.round(amount) * 100L;

        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime now = ZonedDateTime.now(zone);

        String vnpCreateDate = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String vnpExpireDate = now.plusMinutes(props.getExpireMinutes())
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", props.getApiVersion());
        params.put("vnp_Command", props.getCommand());
        params.put("vnp_TmnCode", props.getTmnCode());
        params.put("vnp_Amount", String.valueOf(amountTimes100));
        params.put("vnp_CurrCode", props.getCurrCode());
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan hoa don #" + invoice.getInvoiceId());
        params.put("vnp_OrderType", (req.getOrderType() == null) ? "other" : req.getOrderType());
        params.put("vnp_Locale", (req.getLocale() == null) ? props.getLocale() : req.getLocale());
        params.put("vnp_ReturnUrl", props.getReturnUrl());
        params.put("vnp_IpnUrl", props.getIpnUrl()); // ✅ thêm dòng này
        params.put("vnp_IpAddr", ipAddr);
        params.put("vnp_CreateDate", vnpCreateDate);
        params.put("vnp_ExpireDate", vnpExpireDate);

        if (req.getBankCode() != null && !req.getBankCode().isBlank()) {
            params.put("vnp_BankCode", req.getBankCode());
        }

        // 5️⃣ Lưu Payment trạng thái PENDING
        Payment payment = Payment.builder()
                .invoice(invoice)
                .amount(amount)
                .paymentMethod(Payment.PaymentMethod.QR_BANKING)
                .paymentStatus(Payment.PaymentStatus.PENDING)
                .gateway("VNPAY")
                .vnpTxnRef(txnRef)
                .createdAt(LocalDateTime.now(zone))
                .build();

        paymentRepository.save(payment);

        // 6️⃣ Sinh URL thanh toán
        return VnPayUtils.buildPaymentUrl(props.getPayUrl(), params, props.getHashSecret());
    }


    /** 2️⃣ Xử lý IPN callback (VNPAY → BE) */
    @Transactional
    public Map<String, String> handleVnPayIpn(Map<String, String> query) {
        Map<String, String> response = new HashMap<>();
        try {
            Map<String, String> fields = new HashMap<>(query);
            String secureHash = fields.remove("vnp_SecureHash");
            fields.remove("vnp_SecureHashType");

            String dataToSign = VnPayUtils.buildDataToSign(fields);
            String signed = VnPayUtils.hmacSHA512(props.getHashSecret(), dataToSign);

            if (!signed.equalsIgnoreCase(secureHash)) {
                response.put("RspCode", "97");
                response.put("Message", "Invalid Checksum");
                return response;
            }

            String txnRef = fields.get("vnp_TxnRef");
            Optional<Payment> optionalPayment = paymentRepository.findByVnpTxnRef(txnRef);
            if (optionalPayment.isEmpty()) {
                response.put("RspCode", "01");
                response.put("Message", "Order not Found");
                return response;
            }

            Payment payment = optionalPayment.get();
            long amountFromVnp = Long.parseLong(fields.get("vnp_Amount"));
            boolean checkAmount = (amountFromVnp == (long) (payment.getAmount() * 100));
            if (!checkAmount) {
                response.put("RspCode", "04");
                response.put("Message", "Invalid Amount");
                return response;
            }

            if (payment.getPaymentStatus() != Payment.PaymentStatus.PENDING) {
                response.put("RspCode", "02");
                response.put("Message", "Order already confirmed");
                return response;
            }

            // ✅ Giao dịch hợp lệ → cập nhật trạng thái
            String respCode = fields.get("vnp_ResponseCode");
            String transStatus = fields.get("vnp_TransactionStatus");
            boolean success = "00".equals(respCode) && "00".equals(transStatus);

            payment.setChecksumOk(true);
            payment.setVnpResponseCode(respCode);
            payment.setVnpTransactionStatus(transStatus);
            payment.setVnpTransactionNo(fields.get("vnp_TransactionNo"));
            payment.setVnpBankCode(fields.get("vnp_BankCode"));
            payment.setVnpPayDate(fields.get("vnp_PayDate"));
            payment.setPaymentStatus(success ? Payment.PaymentStatus.SUCCESS : Payment.PaymentStatus.FAILED);

            paymentRepository.save(payment);

            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
            return response;
        } catch (Exception e) {
            response.put("RspCode", "99");
            response.put("Message", "Unknown error");
            return response;
        }
    }

    /** 3️⃣ Xử lý return URL (hiển thị kết quả cho người dùng) */
    @Transactional
    public Map<String, Object> handleVnPayReturn(Map<String, String> query) {
        Map<String, Object> result = new HashMap<>();
        Map<String, String> fields = new HashMap<>(query);

        String secureHash = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        String dataToSign = VnPayUtils.buildDataToSign(fields);
        String signed = VnPayUtils.hmacSHA512(props.getHashSecret(), dataToSign);
        boolean checksumOk = signed.equalsIgnoreCase(secureHash);
        String respCode = query.get("vnp_ResponseCode");
        boolean success = checksumOk && "00".equals(respCode);

        result.put("checksumOk", checksumOk);
        result.put("success", success);
        result.put("vnp_ResponseCode", respCode);
        result.put("vnp_TxnRef", query.get("vnp_TxnRef"));
        result.put("vnp_TransactionNo", query.get("vnp_TransactionNo"));
        result.put("vnp_Amount", query.get("vnp_Amount"));
        result.put("vnp_PayDate", query.get("vnp_PayDate"));
        result.put("vnp_BankCode", query.get("vnp_BankCode"));
        result.put("message", success ? "Giao dịch thành công" : "Giao dịch thất bại hoặc sai chữ ký");

        // ✅ Cập nhật Payment & Invoice khi thanh toán thành công
        if (checksumOk) {
            paymentRepository.findByVnpTxnRef(query.get("vnp_TxnRef")).ifPresent(p -> {
                p.setChecksumOk(true);

                if (success) {
                    // cập nhật thông tin thanh toán
                    p.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
                    p.setVnpPayDate(query.get("vnp_PayDate"));
                    p.setVnpTransactionNo(query.get("vnp_TransactionNo"));
                    p.setVnpResponseCode(respCode);
                    p.setVnpBankCode(query.get("vnp_BankCode"));
                    paymentRepository.save(p);

                    // cập nhật trạng thái Invoice
                    Invoice invoice = p.getInvoice();
                    invoice.setInvoiceStatus(Invoice.InvoiceStatus.PAID);
                    invoiceRepository.save(invoice);

                } else {
                    p.setPaymentStatus(Payment.PaymentStatus.FAILED);
                    paymentRepository.save(p);
                }
            });
        }

        return result;
    }

}
