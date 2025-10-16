package BatterySwapStation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "Invoice")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "invoice_seq")
    @SequenceGenerator(
        name = "invoice_seq",
        sequenceName = "invoice_sequence",
        initialValue = 10000,
        allocationSize = 1
    )
    @Column(name = "invoiceid")
    private Long invoiceId;

    @Column(name = "createddate")
    private LocalDateTime createdDate;

    @Column(name = "totalamount")
    private Double totalAmount;

    // Giá mỗi lần đổi pin (ví dụ: 15,000 VNĐ)
    @Column(name = "priceperswap")
    private Double pricePerSwap = 15000.0;

    // Số lần đổi pin
    @Column(name = "numberofswaps")
    private Integer numberOfSwaps = 0;

    @OneToMany(mappedBy = "invoice")
    private List<Booking> bookings;

    // Getters and setters
    public Long getInvoiceId(){
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    // Backward compatibility method - ẨN KHỎI JSON
    @JsonIgnore
    public Long getId(){
        return invoiceId;
    }

    @JsonIgnore
    public void setId(Long id) {
        this.invoiceId = id;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Double getPricePerSwap() {
        return pricePerSwap;
    }

    public void setPricePerSwap(Double pricePerSwap) {
        this.pricePerSwap = pricePerSwap;
    }

    public Integer getNumberOfSwaps() {
        return numberOfSwaps;
    }

    public void setNumberOfSwaps(Integer numberOfSwaps) {
        this.numberOfSwaps = numberOfSwaps;
    }

    public List<Booking> getBookings() {
        return bookings;
    }

    public void setBookings(List<Booking> bookings) {
        this.bookings = bookings;
    }

    // Phương thức tính tổng tiền tự động
    public void calculateTotalAmount() {
        if (pricePerSwap != null && numberOfSwaps != null) {
            this.totalAmount = pricePerSwap * numberOfSwaps;
        }
    }
}
