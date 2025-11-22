package BatterySwapStation.dto;

import BatterySwapStation.entity.Booking;
import BatterySwapStation.entity.Invoice;
import BatterySwapStation.entity.Vehicle;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
public class BookingSimpleDto {
    private Long bookingId;
    private LocalDate bookingDate;
    private LocalTime timeSlot;
    private Booking.BookingStatus bookingStatus;
    private Double amount;
    private Double totalPrice;

    private Integer stationId;
    private String stationName;
    private String stationAddress;

    private Integer vehicleId;
    private String vehicleVin;
    private Vehicle.VehicleType vehicleType;
    private String licensePlate;

    private Integer batteryCount;
    private String batteryType;
    private String notes;
    private String cancellationReason;
    private LocalDate completedTime;

    private Long invoiceId;
    private Double totalAmount;
    private Invoice.InvoiceStatus invoiceStatus;
    private LocalDateTime invoiceCreatedDate;

    // CONSTRUCTOR 21 THAM SỐ PHẢI KHỚP TUYỆT ĐỐI VỚI JPQL
    public BookingSimpleDto(
            Long bookingId,
            LocalDate bookingDate,
            LocalTime timeSlot,
            Booking.BookingStatus bookingStatus,
            Double amount,
            Double totalPrice,
            Integer stationId,
            String stationName,
            String stationAddress,
            Integer vehicleId,
            String vehicleVin,
            Vehicle.VehicleType vehicleType,
            String licensePlate,
            Integer batteryCount,
            String batteryType,
            String notes,
            String cancellationReason,
            LocalDate completedTime,
            Long invoiceId,
            Double totalAmount,
            Invoice.InvoiceStatus invoiceStatus,
            LocalDateTime invoiceCreatedDate
    ) {
        this.bookingId = bookingId;
        this.bookingDate = bookingDate;
        this.timeSlot = timeSlot;
        this.bookingStatus = bookingStatus;
        this.amount = amount;
        this.totalPrice = totalPrice;
        this.stationId = stationId;
        this.stationName = stationName;
        this.stationAddress = stationAddress;
        this.vehicleId = vehicleId;
        this.vehicleVin = vehicleVin;
        this.vehicleType = vehicleType;
        this.licensePlate = licensePlate;
        this.batteryCount = batteryCount;
        this.batteryType = batteryType;
        this.notes = notes;
        this.cancellationReason = cancellationReason;
        this.completedTime = completedTime;
        this.invoiceId = invoiceId;
        this.totalAmount = totalAmount;
        this.invoiceStatus = invoiceStatus;
        this.invoiceCreatedDate = invoiceCreatedDate;
    }

}