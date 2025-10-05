package BatterySwapStation.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "VehiclePurchaseInvoice")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehiclePurchaseInvoice {


    @Id
    @Column(name = "VIN", nullable = false, length = 100)
    private String vin;

    // Liên kết 1-1 với Vehicle qua VIN
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VIN", referencedColumnName = "VIN", nullable = false)
    @JsonBackReference
    private Vehicle vehicle;

    @Column(name = "InvoiceNumber", nullable = false, unique = true, length = 50)
    private String invoiceNumber; // Mã hóa đơn từ đại lý/nhà sản xuất

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
