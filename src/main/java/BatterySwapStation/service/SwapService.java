package BatterySwapStation.service;

import BatterySwapStation.dto.SwapRequest;
import BatterySwapStation.dto.SwapResponseDTO;
import BatterySwapStation.entity.*;
import BatterySwapStation.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SwapService {

    private final SwapRepository swapRepository;
    private final BookingRepository bookingRepository;
    private final BatteryRepository batteryRepository;
    private final DockSlotRepository dockSlotRepository;

    @Transactional
    public Object cancelSwap(Long swapId, String cancelType) {
        Swap swap = swapRepository.findById(swapId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy swap #" + swapId));

        Booking booking = swap.getBooking();
        if (booking == null) {
            throw new IllegalStateException("Không xác định được booking của swap này.");
        }

        // 🔹 TEMP = hủy tạm thời (user có thể quay lại retry)
        if ("TEMP".equalsIgnoreCase(cancelType)) {
            swap.setStatus(Swap.SwapStatus.CANCELLED_TEMP);
            swap.setDescription("Swap bị hủy tạm thời. Chờ người dùng quay lại xác nhận.");
            swapRepository.save(swap);
            return Map.of(
                    "swapId", swapId,
                    "status", "CANCELLED_TEMP",
                    "message", "Đã hủy tạm thời swap #" + swapId
            );
        }

        // 🔹 PERMANENT = hủy hoàn toàn, rollback dữ liệu
        if ("PERMANENT".equalsIgnoreCase(cancelType)) {
            // Rollback: đưa lại pinOut vào slot, gỡ pinIn ra
            String batteryOutId = swap.getBatteryOutId();
            String batteryInId = swap.getBatteryInId();

            Battery batteryOut = batteryRepository.findById(batteryOutId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy pinOut: " + batteryOutId));
            Battery batteryIn = batteryRepository.findById(batteryInId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy pinIn: " + batteryInId));

            // Trả pinOut về trạm
            DockSlot emptySlot = dockSlotRepository
                    .findFirstByDock_Station_StationIdAndIsActiveTrueAndBatteryIsNull(
                            booking.getStation().getStationId())
                    .orElseThrow(() -> new IllegalStateException("Không còn slot trống để trả pinOut."));
            emptySlot.setBattery(batteryOut);
            emptySlot.setSlotStatus(DockSlot.SlotStatus.OCCUPIED);

            batteryOut.setBatteryStatus(Battery.BatteryStatus.AVAILABLE);
            batteryOut.setStationId(booking.getStation().getStationId());
            batteryOut.setDockSlot(emptySlot);

            // Gỡ pinIn ra khỏi trạm (vì bị hủy)
            batteryIn.setDockSlot(null);
            batteryIn.setStationId(null);
            batteryIn.setBatteryStatus(Battery.BatteryStatus.IN_USE);

            batteryRepository.save(batteryOut);
            batteryRepository.save(batteryIn);
            dockSlotRepository.save(emptySlot);

            // Cập nhật trạng thái booking + swap
            booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
            booking.setCancellationReason("Staff hủy swap khác model (permanent cancel).");

            swap.setStatus(Swap.SwapStatus.CANCELLED);
            swap.setDescription("Staff đã hủy hoàn toàn swap khác model. Đã rollback pin.");

            bookingRepository.save(booking);
            swapRepository.save(swap);

            return Map.of(
                    "swapId", swapId,
                    "status", "CANCELLED",
                    "message", "Đã hủy hoàn toàn swap #" + swapId + " và rollback pin thành công."
            );
        }

        throw new IllegalArgumentException("Loại hủy không hợp lệ: " + cancelType);
    }


    @Transactional
    public Object commitSwap(SwapRequest request) {
        // Booking từ QR
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking ID: " + request.getBookingId()));

        if (booking.getBookingStatus() == Booking.BookingStatus.COMPLETED) {
            throw new IllegalStateException("Booking đã hoàn thành, không thể swap lại.");
        }

        // Chuẩn bị danh sách pin (1 hoặc nhiều)
        List<String> batteryInIds = new ArrayList<>();
        if (request.getBatteryInIds() != null && !request.getBatteryInIds().isEmpty()) {
            batteryInIds.addAll(request.getBatteryInIds());
        } else if (request.getBatteryInId() != null && !request.getBatteryInId().isBlank()) {
            batteryInIds.add(request.getBatteryInId());
        } else {
            throw new IllegalArgumentException("Thiếu thông tin pin khách đưa.");
        }

        //  Kiểm tra số lượng pin theo booking (thiếu / thừa)
        Integer requiredCount = (booking.getBatteryCount() != null && booking.getBatteryCount() > 0)
                ? booking.getBatteryCount()
                : 1;

        if (batteryInIds.size() < requiredCount) {
            throw new IllegalArgumentException(
                    "Booking #" + booking.getBookingId() + " yêu cầu đổi " + requiredCount + " pin, " +
                            "nhưng chỉ nhận được " + batteryInIds.size() + " pin. Vui lòng nhập đủ."
            );
        }
        if (batteryInIds.size() > requiredCount) {
            throw new IllegalArgumentException(
                    "Booking #" + booking.getBookingId() + " chỉ cho phép đổi " + requiredCount + " pin, " +
                            "nhưng đã nhập " + batteryInIds.size() + " pin. Vui lòng kiểm tra lại."
            );
        }

        // Lấy Staff userId từ SecurityContext hoặc request
        String currentStaffUserId = null;
        Authentication auth = SecurityContextHolder.getContext() != null
                ? SecurityContextHolder.getContext().getAuthentication()
                : null;
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            currentStaffUserId = auth.getName(); // ví dụ: ST001
        }
        if (currentStaffUserId == null || currentStaffUserId.isBlank()) {
            currentStaffUserId = request.getStaffUserId();
        }

        List<SwapResponseDTO> results = new ArrayList<>();
        boolean allSuccess = true;

        for (String batteryInId : batteryInIds) {
            try {
                SwapResponseDTO response = handleSingleSwap(booking, batteryInId, currentStaffUserId);
                results.add(response);
                if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
                    allSuccess = false;
                }
            } catch (Exception e) {
                allSuccess = false;
                results.add(SwapResponseDTO.builder()
                        .bookingId(booking.getBookingId())
                        .batteryInId(batteryInId)
                        .status("FAILED")
                        .message(e.getMessage())
                        .build());
            }
        }

        // Update trạng thái booking
        if (allSuccess) {
            booking.setBookingStatus(Booking.BookingStatus.COMPLETED);
            booking.setCompletedTime(LocalDate.now());
        } else {
            booking.setBookingStatus(Booking.BookingStatus.PENDINGSWAPPING);
        }
        bookingRepository.save(booking);

        // Nếu chỉ có 1 pin thì trả object, còn nhiều thì trả list
        return results.size() == 1 ? results.get(0) : results;
    }

    @Scheduled(fixedRate = 600000) // 600000 ms = 10 phút
    @Transactional
    public void autoCancelUnconfirmedSwaps() {
        List<Swap> pendingSwaps = swapRepository.findByStatus(Swap.SwapStatus.WAITING_USER_RETRY);

        LocalDateTime now = LocalDateTime.now();
        for (Swap swap : pendingSwaps) {
            if (swap.getCompletedTime() != null) {
                Duration duration = Duration.between(swap.getCompletedTime(), now);
                if (duration.toHours() >= 1) {
                    Booking booking = swap.getBooking();
                    if (booking != null) {
                        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
                        booking.setCancellationReason("Auto-cancel sau 1 tiếng không xác nhận lại.");
                        bookingRepository.save(booking);
                    }

                    swap.setStatus(Swap.SwapStatus.CANCELLED);
                    swap.setDescription("Tự động hủy sau 1 tiếng không xác nhận.");
                    swapRepository.save(swap);
                }
            }
        }
    }

    private SwapResponseDTO handleSingleSwap(Booking booking, String batteryInId, String staffUserId) {
        Integer stationId = booking.getStation().getStationId();

        // 1️⃣ Pin khách đưa vào
        Battery batteryIn = batteryRepository.findById(batteryInId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy pin khách đưa: " + batteryInId));

        if (!batteryIn.isActive()) throw new IllegalStateException("Pin " + batteryInId + " bị vô hiệu hóa.");
        if (batteryIn.getBatteryStatus() == Battery.BatteryStatus.MAINTENANCE)
            throw new IllegalStateException("Pin " + batteryInId + " đang bảo trì.");
        if (batteryIn.getBatteryType() == null)
            throw new IllegalStateException("Pin " + batteryInId + " chưa xác định loại.");

        String bookedType = booking.getBatteryType();
        if (bookedType != null && !batteryIn.getBatteryType().name().equalsIgnoreCase(bookedType))
            throw new IllegalStateException("Pin " + batteryInId + " không cùng loại với pin đã booking.");

        // 2️⃣ Chọn pin đầy khả dụng (batteryOut)
        DockSlot dockOutSlot = dockSlotRepository
                .findFirstByDock_Station_StationIdAndSlotStatusAndBattery_BatteryStatusOrderByDock_DockNameAscSlotNumberAsc(
                        stationId,
                        DockSlot.SlotStatus.OCCUPIED,
                        Battery.BatteryStatus.AVAILABLE
                )
                .orElseThrow(() -> new IllegalStateException("Không còn pin đầy khả dụng trong trạm."));

        Battery batteryOut = dockOutSlot.getBattery();
        if (batteryOut == null)
            throw new IllegalStateException("Slot chứa pinOut không hợp lệ (không có pin).");

        // 3️⃣ Slot nhận pinIn
        DockSlot dockInSlot = dockSlotRepository.findByBattery_BatteryId(batteryInId).orElse(null);
        if (dockInSlot == null) {
            dockInSlot = dockSlotRepository
                    .findFirstByDock_Station_StationIdAndIsActiveTrueAndBatteryIsNull(stationId)
                    .orElseThrow(() -> new IllegalStateException("Không còn slot trống trong trạm để nhận pinIn."));
        }

        // 4️⃣ Xử lý mã slot
        String dockOutCode = (dockOutSlot.getDock() != null)
                ? dockOutSlot.getDock().getDockName() + dockOutSlot.getSlotNumber()
                : "UNKNOWN" + dockOutSlot.getSlotNumber();
        String dockInCode = dockInSlot.getDock().getDockName() + dockInSlot.getSlotNumber();

        // 5️⃣ Kiểm tra model
        Swap.SwapStatus swapStatus = Swap.SwapStatus.SUCCESS;
        String description = "Swap hoàn tất.";
        if (!batteryIn.getBatteryType().equals(batteryOut.getBatteryType())) {
            swapStatus = Swap.SwapStatus.WAITING_USER_RETRY;
            description = "Pin khác model - chờ người dùng xác nhận.";
        }

        // 6️⃣ Gắn pinIn vào slot
        dockInSlot.setBattery(batteryIn);
        batteryIn.setStationId(dockInSlot.getDock().getStation().getStationId());
        batteryIn.setDockSlot(dockInSlot);

        if (batteryIn.getStateOfHealth() != null && batteryIn.getStateOfHealth() < 70.0) {
            batteryIn.setBatteryStatus(Battery.BatteryStatus.MAINTENANCE);
            dockInSlot.setSlotStatus(DockSlot.SlotStatus.RESERVED);
            description += " Pin SoH thấp, chuyển MAINTENANCE.";
        } else {
            batteryIn.setBatteryStatus(Battery.BatteryStatus.AVAILABLE);
            dockInSlot.setSlotStatus(DockSlot.SlotStatus.OCCUPIED);
        }

        // 7️⃣ Nhả pinOut
        batteryOut.setBatteryStatus(Battery.BatteryStatus.IN_USE);
        dockOutSlot.setBattery(null);
        dockOutSlot.setSlotStatus(DockSlot.SlotStatus.EMPTY);
        batteryOut.setStationId(null);
        batteryOut.setDockSlot(null);

        // 8️⃣ Lưu DB
        batteryRepository.save(batteryIn);
        batteryRepository.save(batteryOut);
        dockSlotRepository.save(dockInSlot);
        dockSlotRepository.save(dockOutSlot);

        // 9️⃣ Ghi log swap
        Integer dockIdForRecord = dockOutSlot.getDock() != null ? dockOutSlot.getDock().getDockId() : stationId;
        Swap swap = Swap.builder()
                .booking(booking)
                .dockId(dockIdForRecord)
                .userId(booking.getUser().getUserId())
                .batteryOutId(batteryOut.getBatteryId())
                .batteryInId(batteryIn.getBatteryId())
                .staffUserId(staffUserId)
                .status(swapStatus)
                .dockOutSlot(dockOutCode)
                .dockInSlot(dockInCode)
                .completedTime(LocalDateTime.now())
                .description(description)
                .build();

        swapRepository.save(swap);

        return SwapResponseDTO.builder()
                .swapId(swap.getSwapId())
                .status(swap.getStatus().toString())
                .message(swap.getDescription())
                .bookingId(booking.getBookingId())
                .batteryOutId(batteryOut.getBatteryId())
                .batteryInId(batteryIn.getBatteryId())
                .dockOutSlot(dockOutCode)
                .dockInSlot(dockInCode)
                .build();
    }
}
