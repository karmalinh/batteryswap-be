package BatterySwapStation.service;

import BatterySwapStation.dto.SwapRequest;
import BatterySwapStation.dto.SwapResponseDTO;
import BatterySwapStation.entity.*;
import BatterySwapStation.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
