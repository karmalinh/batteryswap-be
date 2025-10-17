package BatterySwapStation.dto;

import lombok.Data;

/**
 * FE chỉ truyền invoiceId, BE tự tìm totalAmount trong Invoice.
 */
@Data
public class VnPayCreatePaymentRequest {
    private Long invoiceId;  // 🔹 bắt buộc
    private String bankCode; // optional: VNBANK | VNPAYQR | INTCARD
    private String locale;   // optional: vn | en
    private String orderType;// optional: other
}
