package BatterySwapStation.service;

import BatterySwapStation.config.VnPayProperties;
import BatterySwapStation.dto.VnPayCreatePaymentRequest;
import BatterySwapStation.entity.*;
import BatterySwapStation.repository.*;
import lombok.extern.slf4j.Slf4j;
import BatterySwapStation.utils.VnPayUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final VnPayProperties props;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BookingRepository bookingRepository;
    private final SubscriptionService subscriptionService;

    /**
     * 1️⃣ Tạo URL thanh toán (FE gọi)
     * 👉 Chỉ tạo Payment với trạng thái PENDING, chưa update DB khác.
     */
    @Transactional
    public String createVnPayPaymentUrlByInvoice(VnPayCreatePaymentRequest req, HttpServletRequest http) {
        Invoice invoice = invoiceRepository.findById(req.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hóa đơn: " + req.getInvoiceId()));

        double amount = Optional.ofNullable(invoice.getTotalAmount()).orElse(0d);
        if (amount <= 0)
            throw new IllegalArgumentException("Hóa đơn phải có giá trị lớn hơn 0");

        boolean alreadyPaid = paymentRepository.existsByInvoiceAndPaymentStatus(invoice, Payment.PaymentStatus.SUCCESS);
        if (alreadyPaid)
            throw new IllegalStateException("Hóa đơn đã được thanh toán");

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
        params.put("vnp_OrderInfo", "Thanh toán hóa đơn #" + invoice.getInvoiceId());
        params.put("vnp_OrderType", req.getOrderType());
        params.put("vnp_Locale", (req.getLocale() == null || req.getLocale().isBlank()) ? "vn" : req.getLocale());
        params.put("vnp_ReturnUrl", props.getReturnUrl());
        params.put("vnp_IpAddr", ipAddr);
        params.put("vnp_CreateDate", vnpCreateDate);
        params.put("vnp_ExpireDate", vnpExpireDate);
        if (req.getBankCode() != null && !req.getBankCode().isBlank()) {
            params.put("vnp_BankCode", req.getBankCode());
        }

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
        return VnPayUtils.buildPaymentUrl(props.getPayUrl(), params, props.getHashSecret());
    }

    /**
     * 2️⃣ IPN callback (VNPAY → BE)
     * 👉 Xử lý chính thức: cập nhật DB, trạng thái hóa đơn & booking.
     */
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
                response.put("Message", "Checksum không hợp lệ");
                return response;
            }

            String txnRef = fields.get("vnp_TxnRef");
            Payment payment = paymentRepository.findByVnpTxnRef(txnRef)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch"));

            long amountFromVnp = Long.parseLong(fields.get("vnp_Amount"));
            if (amountFromVnp != (long) (payment.getAmount() * 100)) {
                response.put("RspCode", "04");
                response.put("Message", "Tổng tiền không hợp lệ");
                return response;
            }

            if (payment.getPaymentStatus() != Payment.PaymentStatus.PENDING) {
                response.put("RspCode", "02");
                response.put("Message", "Đã xử lý rồi");
                return response;
            }

            // ✅ fallback nếu sandbox thiếu mã phản hồi
            String respCode = fields.getOrDefault("vnp_ResponseCode", "99");
            String transStatus = fields.getOrDefault("vnp_TransactionStatus", "99");
            boolean success = "00".equals(respCode) && "00".equals(transStatus);

            payment.setChecksumOk(true);
            payment.setVnpResponseCode(respCode);
            payment.setVnpTransactionStatus(transStatus);
            payment.setVnpTransactionNo(fields.get("vnp_TransactionNo"));
            payment.setVnpBankCode(fields.get("vnp_BankCode"));
            payment.setVnpPayDate(fields.get("vnp_PayDate"));
            payment.setPaymentStatus(success ? Payment.PaymentStatus.SUCCESS : Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);

            Invoice invoice = payment.getInvoice();
            if (invoice != null) {
                if (success) {
                    invoice.setInvoiceStatus(Invoice.InvoiceStatus.PAID);
                    invoiceRepository.save(invoice);
                    subscriptionService.activateSubscription(invoice);

                    if (invoice.getBookings() != null) {
                        for (Booking booking : invoice.getBookings()) {
                            booking.setBookingStatus(Booking.BookingStatus.PENDINGSWAPPING);
                            bookingRepository.save(booking);
                        }
                    }
                } else {
                    invoice.setInvoiceStatus(Invoice.InvoiceStatus.PAYMENTFAILED);
                    invoiceRepository.save(invoice);
                    if (invoice.getBookings() != null) {
                        for (Booking booking : invoice.getBookings()) {
                            booking.setBookingStatus(Booking.BookingStatus.FAILED);
                            bookingRepository.save(booking);
                        }
                    }
                }
            }

            response.put("RspCode", "00");
            response.put("Message", VnPayUtils.getVnPayResponseMessage(respCode));
            return response;
        } catch (Exception e) {
            response.put("RspCode", "99");
            response.put("Message", "Lỗi xử lý IPN: " + e.getMessage());
            return response;
        }
    }

    /**
     * 3️⃣ Return URL (VNPAY → BE → FE)
     */
    @Transactional
    public Map<String, Object> handleVnPayReturn(Map<String, String> query) {
        Map<String, Object> result = new HashMap<>();
        Map<String, String> fields = new HashMap<>(query);

        String secureHash = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        String dataToSign = VnPayUtils.buildDataToSign(fields);
        String signed = VnPayUtils.hmacSHA512(props.getHashSecret(), dataToSign);
        boolean checksumOk = signed.equalsIgnoreCase(secureHash);

        String respCode = query.getOrDefault("vnp_ResponseCode", "99");
        String txnRef = query.get("vnp_TxnRef");
        boolean success = checksumOk && "00".equals(respCode);

        if (checksumOk && "24".equals(respCode)) {
            paymentRepository.findByVnpTxnRef(txnRef).ifPresent(payment -> {
                if (payment.getPaymentStatus() == Payment.PaymentStatus.PENDING) {
                    payment.setPaymentStatus(Payment.PaymentStatus.FAILED);
                    paymentRepository.save(payment);

                    Invoice invoice = payment.getInvoice();
                    if (invoice != null) {
                        invoice.setInvoiceStatus(Invoice.InvoiceStatus.PAYMENTFAILED);
                        invoiceRepository.save(invoice);

                        if (invoice.getBookings() != null) {
                            for (Booking booking : invoice.getBookings()) {
                                booking.setBookingStatus(Booking.BookingStatus.FAILED);
                                bookingRepository.save(booking);
                            }
                        }
                    }
                }
            });
        }

        result.put("success", success);
        result.put("checksumOk", checksumOk);
        result.put("vnp_Amount", query.get("vnp_Amount"));
        result.put("vnp_TxnRef", txnRef);
        result.put("vnp_ResponseCode", respCode);
        result.put("message", VnPayUtils.getVnPayResponseMessage(respCode));
        return result;
    }

    @Transactional
    public Map<String, Object> refundBooking(String bookingId) {
        Booking booking = bookingRepository.findById(Long.valueOf(bookingId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking #" + bookingId));

        Invoice invoice = booking.getInvoice();
        if (invoice == null || invoice.getPayments() == null || invoice.getPayments().isEmpty()) {
            throw new IllegalStateException("Booking không thuộc hóa đơn nào hoặc chưa thanh toán.");
        }

        // 🔍 Tìm payment thành công mới nhất
        Payment payment = invoice.getPayments().stream()
                .filter(p -> p.getPaymentStatus() == Payment.PaymentStatus.SUCCESS)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy giao dịch thanh toán thành công."));

        if (payment.getPaymentStatus() != Payment.PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Giao dịch chưa thanh toán, không thể hoàn tiền.");
        }

        // ✅ Tính số tiền refund theo booking
        Double bookingAmount = booking.getAmount();
        if (bookingAmount == null || bookingAmount <= 0) {
            throw new IllegalStateException("Booking không có giá trị thanh toán hợp lệ.");
        }
        Long refundAmount = Math.round(bookingAmount * 100); // VNPay yêu cầu *100

        // ==== DỮ LIỆU REFUND ====
        String vnp_RequestId = "rf" + System.currentTimeMillis();
        String vnp_Version = props.getApiVersion();
        String vnp_Command = "refund";
        String vnp_TmnCode = props.getTmnCode();
        String vnp_TransactionType = "03"; // partial refund
        String vnp_TxnRef = payment.getVnpTxnRef();
        String vnp_TransactionNo = payment.getVnpTransactionNo();
        String vnp_TransactionDate = payment.getVnpPayDate();
        String vnp_OrderInfo = "Hoàn tiền Booking #" + bookingId;
        String vnp_CreateBy = "admin";
        String vnp_CreateDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String vnp_IpAddr = "127.0.0.1";

        String data = String.join("|",
                vnp_RequestId, vnp_Version, vnp_Command, vnp_TmnCode,
                vnp_TransactionType, vnp_TxnRef, refundAmount.toString(),
                vnp_TransactionNo, vnp_TransactionDate, vnp_CreateBy,
                vnp_CreateDate, vnp_IpAddr, vnp_OrderInfo
        );

        String vnp_SecureHash = VnPayUtils.hmacSHA512(props.getHashSecret(), data);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", vnp_RequestId);
        body.put("vnp_Version", vnp_Version);
        body.put("vnp_Command", vnp_Command);
        body.put("vnp_TmnCode", vnp_TmnCode);
        body.put("vnp_TransactionType", vnp_TransactionType);
        body.put("vnp_TxnRef", vnp_TxnRef);
        body.put("vnp_Amount", refundAmount);
        body.put("vnp_OrderInfo", vnp_OrderInfo);
        body.put("vnp_TransactionNo", vnp_TransactionNo);
        body.put("vnp_TransactionDate", vnp_TransactionDate);
        body.put("vnp_CreateBy", vnp_CreateBy);
        body.put("vnp_CreateDate", vnp_CreateDate);
        body.put("vnp_IpAddr", vnp_IpAddr);
        body.put("vnp_SecureHash", vnp_SecureHash);

        // ==== GỬI REQUEST ====
        RestTemplate rest = new RestTemplate();
        ResponseEntity<Map> response = rest.postForEntity(
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                body, Map.class
        );

        Map<String, Object> result = response.getBody();
        String responseCode = (String) result.get("vnp_ResponseCode");

        if ("00".equals(responseCode)) {
            booking.setBookingStatus(Booking.BookingStatus.REFUNDED);
            bookingRepository.save(booking);
        } else {
            throw new IllegalStateException("VNPay refund thất bại: " + result.get("vnp_Message"));
        }

        return result;
    }


}
