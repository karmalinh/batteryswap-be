package BatterySwapStation.service;

import BatterySwapStation.entity.Invoice;
import BatterySwapStation.entity.Booking;
import BatterySwapStation.dto.InvoiceResponseDTO;
import BatterySwapStation.dto.BookingInfoDTO;
import BatterySwapStation.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InvoiceService {
    @Autowired
    private InvoiceRepository invoiceRepository;

    /**
     * Lấy chi tiết invoice bao gồm thông tin các booking
     */
    public InvoiceResponseDTO getInvoiceDetail(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn với ID: " + invoiceId));

        InvoiceResponseDTO dto = new InvoiceResponseDTO();
        dto.setId(invoice.getInvoiceId());
        dto.setCreatedDate(invoice.getCreatedDate());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setPricePerSwap(invoice.getPricePerSwap());
        dto.setNumberOfSwaps(invoice.getNumberOfSwaps());

        List<BookingInfoDTO> bookingDTOs = invoice.getBookings().stream().map(booking -> {
            BookingInfoDTO bDto = new BookingInfoDTO();
            bDto.setBookingId(booking.getBookingId());
            bDto.setBookingDate(booking.getBookingDate());
            bDto.setTimeSlot(booking.getTimeSlot());
            bDto.setVehicleType(booking.getVehicleType());
            bDto.setAmount(booking.getAmount());
            return bDto;
        }).collect(Collectors.toList());

        dto.setBookings(bookingDTOs);
        return dto;
    }

    /**
     * Tạo invoice mới và tự động tính toán tổng tiền
     */
    @Transactional
    public Invoice createInvoice(Invoice invoice) {
        invoice.setInvoiceId(null); // Đảm bảo sử dụng sequence tự động

        // Đặt giá mặc định nếu chưa có
        if (invoice.getPricePerSwap() == null) {
            invoice.setPricePerSwap(15000.0);
        }

        // Đặt ngày tạo
        if (invoice.getCreatedDate() == null) {
            invoice.setCreatedDate(LocalDateTime.now());
        }

        // Tính số lần đổi pin dựa trên bookings
        if (invoice.getBookings() != null && !invoice.getBookings().isEmpty()) {
            invoice.setNumberOfSwaps(invoice.getBookings().size());
        } else {
            invoice.setNumberOfSwaps(0);
        }

        // Tính tổng tiền tự động
        invoice.calculateTotalAmount();

        return invoiceRepository.save(invoice);
    }

    /**
     * Cập nhật invoice và tính lại tổng tiền
     */
    @Transactional
    public Invoice updateInvoice(Long id, Invoice invoice) {
        Invoice existing = invoiceRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn với ID: " + id));

        existing.setCreatedDate(invoice.getCreatedDate());

        // Cập nhật giá và số lần đổi nếu có
        if (invoice.getPricePerSwap() != null) {
            existing.setPricePerSwap(invoice.getPricePerSwap());
        }

        if (invoice.getNumberOfSwaps() != null) {
            existing.setNumberOfSwaps(invoice.getNumberOfSwaps());
        }

        // Tính lại tổng tiền
        existing.calculateTotalAmount();

        return invoiceRepository.save(existing);
    }

    /**
     * Thêm booking vào invoice và cập nhật tổng tiền
     */
    @Transactional
    public Invoice addBookingToInvoice(Long invoiceId, Booking booking) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn với ID: " + invoiceId));

        // Thêm booking vào invoice
        booking.setInvoice(invoice);

        // Tăng số lần đổi pin
        invoice.setNumberOfSwaps(invoice.getNumberOfSwaps() + 1);

        // Tính lại tổng tiền
        invoice.calculateTotalAmount();

        return invoiceRepository.save(invoice);
    }

    /**
     * Xóa invoice
     */
    public void deleteInvoice(Long id) {
        invoiceRepository.deleteById(id);
    }

    /**
     * Lấy tất cả invoice
     */
    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    /**
     * Lấy invoice theo ID
     */
    public Invoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn với ID: " + id));
    }
}
