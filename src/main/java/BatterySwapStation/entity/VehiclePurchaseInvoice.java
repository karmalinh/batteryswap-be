package BatterySwapStation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "VehiclePurchaseInvoice")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehiclePurchaseInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "InvoiceId")
    private Long invoiceId;

    @Column(name = "InvoiceNumber", nullable = false, unique = true, length = 50)
    private String invoiceNumber;   // mã hóa đơn từ đại lý/nhà sản xuất

    // Liên kết 1-1 với Vehicle
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VehicleId", nullable = false, unique = true)
    private Vehicle vehicle;

    // Thông tin người mua để xác thực
    @Column(name = "BuyerName", nullable = false, length = 255)
    private String buyerName;

    @Column(name = "BuyerEmail", nullable = false, length = 255)
    private String buyerEmail;

    @Column(name = "BuyerPhone", nullable = false, length = 50)
    private String buyerPhone;

    // Trạng thái xác thực
    @Column(name = "IsVerified", nullable = false)
    private boolean isVerified = false;

}
