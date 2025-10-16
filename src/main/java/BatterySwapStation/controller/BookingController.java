package BatterySwapStation.controller;

import BatterySwapStation.dto.*;
import BatterySwapStation.service.BookingService;
import BatterySwapStation.service.InvoiceService;
import BatterySwapStation.entity.Invoice;
import BatterySwapStation.entity.Booking;
import BatterySwapStation.entity.Battery;
import BatterySwapStation.repository.BookingRepository;
import BatterySwapStation.repository.BatteryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking API", description = "API quản lý đặt chỗ thay pin")
public class BookingController {

    private final BookingService bookingService;
    private final InvoiceService invoiceService;
    private final BookingRepository bookingRepository;
    private final BatteryRepository batteryRepository;

    @PostMapping
    @Operation(summary = "Tạo booking mới", description = "Tạo một booking mới cho việc thay pin")
    public ResponseEntity<ApiResponseDto> createBooking(
            @RequestBody BookingRequest request) {
        try {
            BookingResponse response = bookingService.createBooking(request);
            return ResponseEntity.ok(new ApiResponseDto(true, "Booking thành công!", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto(false, "Booking thất bại: " + e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Lấy danh sách booking của user", description = "Lấy tất cả booking của một user cụ thể")
    public ResponseEntity<ApiResponseDto> getUserBookings(
            @PathVariable @Parameter(description = "ID của user") String userId) {
        try {
            List<BookingResponse> bookings = bookingService.getUserBookings(userId);
            return ResponseEntity.ok(new ApiResponseDto(true, "Lấy danh sách booking thành công!", bookings));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto(false, "Lỗi lấy danh sách booking: " + e.getMessage()));
        }
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Lấy thông tin booking theo ID", description = "Lấy chi tiết một booking cụ thể")
    public ResponseEntity<ApiResponseDto> getBookingById(
            @PathVariable @Parameter(description = "ID của booking") Long bookingId,
            @RequestParam @Parameter(description = "ID của user") String userId) {
        try {
            BookingResponse booking = bookingService.getBookingById(bookingId, userId);
            return ResponseEntity.ok(new ApiResponseDto(true, "Lấy danh sách booking thành công!", booking));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto(false, "Lỗi lấy danh sách booking: " + e.getMessage()));
        }
    }

    @PutMapping("/cancel")
    @Operation(summary = "Hủy booking", description = "Hủy một booking đã tạo")
    public ResponseEntity<ApiResponseDto> cancelBooking(
            @RequestBody CancelBookingRequest request) {
        try {
            BookingResponse response = bookingService.cancelBooking(request);
            return ResponseEntity.ok(new ApiResponseDto(true, "Hủy booking thành công!", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto(false, "Lỗi hủy booking: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Lấy booking theo trạng thái", description = "Lấy danh sách booking theo trạng thái cụ thể (PENDING, CONFIRMED, CANCELLED, COMPLETED)")
    public ResponseEntity<ApiResponseDto> getBookingsByStatus(
            @PathVariable @Parameter(description = "Trạng thái booking (PENDING, CONFIRMED, CANCELLED, COMPLETED)")
            String status) {
        try {
            // Validate status trước khi gọi service
            String normalizedStatus = status.toUpperCase();
            if (!normalizedStatus.matches("PENDING|CONFIRMED|CANCELLED|COMPLETED")) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponseDto(false, "Trạng thái không hợp lệ. Chỉ chấp nhận: PENDING, CONFIRMED, CANCELLED, COMPLETED"));
            }

            List<BookingResponse> bookings = bookingService.getBookingsByStatus(normalizedStatus);
            return ResponseEntity.ok(new ApiResponseDto(true, "Lấy danh sách booking thành công!", bookings));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto(false, "Lỗi lấy danh sách booking: " + e.getMessage()));
        }
    }

    @GetMapping("/station/{stationId}")
    @Operation(summary = "Lấy booking của station", description = "Lấy tất cả booking của một station cụ thể")
    public ResponseEntity<ApiResponseDto> getStationBookings(
            @PathVariable @Parameter(description = "ID của station") Integer stationId) {
        try {
            List<BookingResponse> bookings = bookingService.getStationBookings(stationId);
            return ResponseEntity.ok(new ApiResponseDto(true, "Lấy danh sách booking thành công!", bookings));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto(false, "Lỗi lấy danh sách booking: " + e.getMessage()));
        }
    }

    @PutMapping("/{bookingId}/status")
    @Operation(summary = "Cập nhật trạng thái booking", description = "Cập nhật trạng thái của một booking (dành cho admin/staff). Trạng thái: PENDING, CONFIRMED, CANCELLED, COMPLETED")
    public ResponseEntity<ApiResponseDto> updateBookingStatus(
            @PathVariable @Parameter(description = "ID của booking") Long bookingId,
            @RequestParam @Parameter(description = "Trạng thái mới (PENDING, CONFIRMED, CANCELLED, COMPLETED)") String status) {
        try {
            // Validate và normalize status
            String normalizedStatus = status.toUpperCase();
            if (!normalizedStatus.matches("PENDING|CONFIRMED|CANCELLED|COMPLETED")) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponseDto(false, "Trạng thái không hợp lệ. Chỉ chấp nhận: PENDING, CONFIRMED, CANCELLED, COMPLETED"));
            }

            BookingResponse response = bookingService.updateBookingStatus(bookingId, normalizedStatus);
            return ResponseEntity.ok(new ApiResponseDto(true, "Cập nhật trạng thái booking thành công!", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto(false, "Cập nhật trạng thái booking thất bại: " + e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Lấy tất cả booking", description = "Lấy danh sách tất cả booking (dành cho admin)")
    public ResponseEntity<ApiResponseDto> getAllBookings() {
        try {
            List<BookingResponse> bookings = bookingService.getAllBookings();
            return ResponseEntity.ok(new ApiResponseDto(true, "Lấy tất cả booking thành công!", bookings));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto(false, "Lỗi lấy booking: " + e.getMessage()));
        }
    }

    @PostMapping("/createinvoice")
    @Operation(summary = "Tạo invoice từ danh sách pin", description = "Tạo invoice ngay khi user chọn pin, trả về invoiceId và tổng tiền")
    public ResponseEntity<Map<String, Object>> createInvoiceFromBatteries(
            @RequestParam @Parameter(description = "ID của user") String userId,
            @RequestBody List<String> batteryIds) {
        try {
            // Tính tổng tiền từ danh sách pin
            double totalAmount = 0.0;
            List<Map<String, Object>> batteryDetails = new ArrayList<>();

            for (String batteryId : batteryIds) {
                // Lấy battery từ database
                Battery battery = batteryRepository.findById(batteryId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy pin: " + batteryId));

                // Kiểm tra pin có sẵn không
                if (!battery.isAvailableForBooking()) {
                    throw new RuntimeException("Pin " + batteryId + " không khả dụng");
                }

                // Tính tiền cho pin - SỬ DỤNG GIÁ THỰC TỪ BATTERY
                double batteryAmount = battery.getCalculatedPrice();
                totalAmount += batteryAmount;

                // Cập nhật trạng thái pin sang IN_USE
                battery.setBatteryStatus(Battery.BatteryStatus.IN_USE);
                batteryRepository.save(battery);

                batteryDetails.add(Map.of(
                    "batteryId", battery.getBatteryId(),
                    "batteryType", battery.getBatteryType().toString(),
                    "price", batteryAmount,
                    "stationId", battery.getStationId()
                ));
            }

            // Tạo invoice với tổng tiền thực tế
            Invoice invoice = new Invoice();
            invoice.setUserId(userId);
            invoice.setTotalAmount(totalAmount);
            invoice.setPricePerSwap(totalAmount / batteryIds.size());
            invoice.setNumberOfSwaps(batteryIds.size());
            invoice.setCreatedDate(java.time.LocalDate.now());

            Invoice savedInvoice = invoiceService.createInvoice(invoice);

            // Trả về response đơn giản
            return ResponseEntity.ok(Map.of(
                "invoiceId", savedInvoice.getInvoiceId(),
                "userId", savedInvoice.getUserId(),
                "totalAmount", savedInvoice.getTotalAmount(),
                "pricePerSwap", savedInvoice.getPricePerSwap(),
                "numberOfSwaps", savedInvoice.getNumberOfSwaps(),
                "createdDate", savedInvoice.getCreatedDate(),
                "status", "PENDING",
                "batteries", batteryDetails
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Tạo invoice thất bại",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/{batteryId}/schedule")
    @Operation(summary = "Cập nhật lịch trình cho pin", description = "Cập nhật ngày giờ sử dụng cho pin")
    public ResponseEntity<Map<String, Object>> updateBatterySchedule(
            @PathVariable @Parameter(description = "ID của pin") String batteryId,
            @RequestParam @Parameter(description = "Ngày sử dụng") String date,
            @RequestParam @Parameter(description = "Giờ sử dụng") String time,
            @RequestParam @Parameter(description = "ID của user") String userId) {
        try {
            // Lấy pin
            Battery battery = batteryRepository.findById(batteryId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy pin"));

            // Tạo booking với thông tin lịch
            BookingRequest request = new BookingRequest();
            request.setUserId(userId);
            request.setStationId(battery.getStationId());
            request.setBookingDate(java.time.LocalDate.parse(date));
            request.setTimeSlot(time);

            BookingResponse booking = bookingService.createBooking(request);

            return ResponseEntity.ok(Map.of(
                "message", "Cập nhật lịch thành công!",
                "batteryId", battery.getBatteryId(),
                "bookingId", booking.getBookingId(),
                "date", date,
                "time", time
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", "Cập nhật lịch thất bại: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/batteries/available/{stationId}")
    @Operation(summary = "Lấy danh sách pin khả dụng", description = "Lấy tất cả pin khả dụng tại một station")
    public ResponseEntity<Map<String, Object>> getAvailableBatteries(
            @PathVariable @Parameter(description = "ID của station") Integer stationId) {
        try {
            List<Battery> batteries = batteryRepository.findByStationIdAndIsActiveTrue(stationId);
            List<Map<String, Object>> availableBatteries = new ArrayList<>();

            for (Battery battery : batteries) {
                if (battery.isAvailableForBooking()) {
                    availableBatteries.add(Map.of(
                        "batteryId", battery.getBatteryId(),
                        "batteryType", battery.getBatteryType().toString(),
                        "price", battery.getCalculatedPrice(),
                        "stateOfHealth", battery.getStateOfHealth(),
                        "status", battery.getBatteryStatus().toString()
                    ));
                }
            }

            return ResponseEntity.ok(Map.of(
                "stationId", stationId,
                "availableBatteries", availableBatteries,
                "count", availableBatteries.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", "Lỗi lấy danh sách pin: " + e.getMessage()
            ));
        }
    }

    @PutMapping("/batteries/{batteryId}/status")
    @Operation(summary = "Cập nhật trạng thái pin", description = "Cập nhật trạng thái của pin (AVAILABLE, IN_USE, CHARGING, DAMAGED)")
    public ResponseEntity<Map<String, Object>> updateBatteryStatus(
            @PathVariable @Parameter(description = "ID của pin") String batteryId,
            @RequestParam @Parameter(description = "Trạng thái mới") String status) {
        try {
            Battery battery = batteryRepository.findById(batteryId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy pin"));

            // Validate status
            Battery.BatteryStatus newStatus = Battery.BatteryStatus.valueOf(status.toUpperCase());
            battery.setBatteryStatus(newStatus);

            Battery savedBattery = batteryRepository.save(battery);

            return ResponseEntity.ok(Map.of(
                "message", "Cập nhật trạng thái pin thành công!",
                "batteryId", savedBattery.getBatteryId(),
                "newStatus", savedBattery.getBatteryStatus().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", "Cập nhật trạng thái pin thất bại: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/{bookingId}/confirm-payment")
    @Operation(summary = "Xác nhận thanh toán và tạo invoice", description = "Xác nhận thanh toán cho booking và tự động tạo invoice")
    public ResponseEntity<Map<String, Object>> confirmPaymentAndCreateInvoice(
            @PathVariable @Parameter(description = "ID của booking") Long bookingId) {
        try {
            // Xác nhận thanh toán
            BookingResponse bookingResponse = bookingService.confirmPayment(bookingId);

            // Lấy booking entity để tạo invoice
            Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));

            // Tạo invoice sau khi thanh toán thành công
            Invoice invoice = invoiceService.createInvoiceForBooking(booking);

            // Trả về response đơn giản
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Thanh toán và tạo invoice thành công",
                "invoiceId", invoice.getInvoiceId(),
                "totalAmount", invoice.getTotalAmount(),
                "bookingId", bookingResponse.getBookingId(),
                "paymentStatus", bookingResponse.getPaymentStatus(),
                "bookingStatus", bookingResponse.getBookingStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Xác nhận thanh toán thất bại",
                "message", e.getMessage()
            ));
        }
    }
}
