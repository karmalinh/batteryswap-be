package BatterySwapStation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "Booking")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BookingId")
    private Long bookingId;

    // N-1: 1 User có nhiều Booking
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    // N-1: 1 Station có nhiều Booking
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StationId", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VehicleId", nullable = false)
    private Vehicle vehicle;

    // Lưu loại xe để không phải join với Vehicle mỗi lần query
    @Column(name = "vehicletype", length = 50)
    private String vehicleType;

    // Giá tiền cho booking này (sẽ được tính vào invoice)
    @Column(name = "amount")
    private Double amount;

    // Các cột thời gian cố định (DATE + TIME) - KHÔNG CÓ NANO GIÂY
    @Column(name = "bookingdate", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "timeslot", nullable = false)
    private LocalTime timeSlot;

    // Enum cho trạng thái booking
    public enum BookingStatus {
        PENDING,    // Đang chờ xác nhận
        CONFIRMED,  // Đã xác nhận
        CANCELLED,  // Đã hủy
        COMPLETED   // Đã hoàn thành
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "bookingstatus", nullable = false, length = 20)
    private BookingStatus bookingStatus = BookingStatus.PENDING;

    @Column(name = "CompletedTime")
    private LocalDate completedTime;

    @Column(name = "CancellationReason", length = 500)
    private String cancellationReason;

    @Column(name = "Notes", length = 1000)
    private String notes;

    // Relationships
    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private Payment payment;

    // N-1: 1 Invoice có nhiều Booking
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "InvoiceId")
    private Invoice invoice;
}
