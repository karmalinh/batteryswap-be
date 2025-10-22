package BatterySwapStation.service;

import BatterySwapStation.dto.SwapRequest;
import BatterySwapStation.dto.SwapResponseDTO;
import BatterySwapStation.entity.*;
import BatterySwapStation.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.context.ApplicationContext;
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
    @Autowired
    private ApplicationContext context;
    private final SwapRepository swapRepository;
    private final BookingRepository bookingRepository;
    private final BatteryRepository batteryRepository;
    private final DockSlotRepository dockSlotRepository;
    private final StaffAssignRepository staffAssignRepository;

    // ====================== CANCEL SWAP ======================
    // ====================== CANCEL SWAP BY BOOKING ======================
    @Transactional
    public Object cancelSwapByBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking #" + bookingId));

        Swap swap = swapRepository.findTopByBooking_BookingIdOrderBySwapIdDesc(bookingId)
                .orElse(null);

        // Nếu chưa có swap nào, chỉ hủy booking thôi
        if (swap == null) {
            booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
            booking.setCancellationReason("Staff hủy booking (chưa phát sinh swap).");
            bookingRepository.save(booking);
            return Map.of(
                    "bookingId", bookingId,
                    "status", "CANCELLED",
                    "message", "Đã hủy booking #" + bookingId + " (chưa có swap)."
            );
        }

        // Có swap, rollback pin nếu đủ thông tin
        String batteryOutId = swap.getBatteryOutId();
        String batteryInId = swap.getBatteryInId();

        if (batteryOutId == null || batteryInId == null) {
            booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
            booking.setCancellationReason("Staff hủy booking #" + bookingId + " (thiếu thông tin pin).");
            swap.setStatus(Swap.SwapStatus.CANCELLED);
            swap.setDescription("Hủy swap theo booking (thiếu thông tin pin).");

            bookingRepository.save(booking);
            swapRepository.save(swap);
            return Map.of(
                    "bookingId", bookingId,
                    "status", "CANCELLED",
                    "message", "Đã hủy booking #" + bookingId + " do thiếu thông tin pin."
            );
        }

        // Rollback pinOut về slot trống
        Battery batteryOut = batteryRepository.findById(batteryOutId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy pinOut: " + batteryOutId));
        Battery batteryIn = batteryRepository.findById(batteryInId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy pinIn: " + batteryInId));

        DockSlot emptySlot = dockSlotRepository
                .findFirstByDock_Station_StationIdAndIsActiveTrueAndBatteryIsNull(
                        booking.getStation().getStationId())
                .orElseThrow(() -> new IllegalStateException("Không còn slot trống để trả pinOut."));

        emptySlot.setBattery(batteryOut);
        emptySlot.setSlotStatus(DockSlot.SlotStatus.OCCUPIED);

        batteryOut.setBatteryStatus(Battery.BatteryStatus.AVAILABLE);
        batteryOut.setStationId(booking.getStation().getStationId());
        batteryOut.setDockSlot(emptySlot);

        batteryIn.setDockSlot(null);
        batteryIn.setStationId(null);
        batteryIn.setBatteryStatus(Battery.BatteryStatus.IN_USE);

        batteryRepository.save(batteryOut);
        batteryRepository.save(batteryIn);
        dockSlotRepository.save(emptySlot);

        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
        booking.setCancellationReason("Staff hủy booking #" + bookingId + " (đã rollback pin).");
        swap.setStatus(Swap.SwapStatus.CANCELLED);
        swap.setDescription("Hủy swap theo booking, rollback pin thành công.");

        bookingRepository.save(booking);
        swapRepository.save(swap);

        return Map.of(
                "bookingId", bookingId,
                "status", "CANCELLED",
                "message", "Đã hủy booking #" + bookingId + " và rollback pin thành công."
        );
    }


    // ====================== COMMIT SWAP ======================
    @Transactional
    public Object commitSwap(SwapRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking ID: " + request.getBookingId()));

        if (booking.getBookingStatus() == Booking.BookingStatus.COMPLETED) {
            throw new IllegalStateException("Booking đã hoàn thành, không thể swap lại.");
        }

        List<String> batteryInIds = request.getBatteryInIds();
        if (batteryInIds == null || batteryInIds.isEmpty()) {
            throw new IllegalArgumentException("Thiếu thông tin pin khách đưa.");
        }

        Integer requiredCount = (booking.getBatteryCount() != null && booking.getBatteryCount() > 0)
                ? booking.getBatteryCount() : 1;

        if (batteryInIds.size() != requiredCount) {
            throw new IllegalArgumentException("Số lượng pin nhập không khớp với booking yêu cầu (" + requiredCount + ").");
        }

        // Kiểm tra model tất cả pinIn trước khi swap
        for (String batteryInId : batteryInIds) {
            Battery battery = batteryRepository.findById(batteryInId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy pin #" + batteryInId));

            if (battery.getBatteryType() == null) {
                throw new IllegalStateException("Pin " + batteryInId + " chưa xác định loại model.");
            }

            if (!battery.getBatteryType().name().equalsIgnoreCase(booking.getBatteryType())) {
                throw new IllegalStateException("Pin " + batteryInId + " khác model (" +
                        battery.getBatteryType().name() + " ≠ " + booking.getBatteryType() + "). Vui lòng hủy swap hoặc chọn pin đúng model.");
            }
        }

        long availableCount = dockSlotRepository.countByDock_Station_StationIdAndBattery_BatteryStatus(
                booking.getStation().getStationId(), Battery.BatteryStatus.AVAILABLE);

        if (availableCount < requiredCount) {
            throw new IllegalStateException("Không đủ pin đầy khả dụng để swap.");
        }

        String currentStaffUserId = resolveStaffUserId(request);
        boolean staffInStation = staffAssignRepository.existsByStationIdAndUser_UserId(
                booking.getStation().getStationId(), currentStaffUserId);
        if (!staffInStation) {
            throw new IllegalStateException("Nhân viên không thuộc trạm này, không thể thực hiện swap.");
        }

        List<SwapResponseDTO> results = new ArrayList<>();
        for (String batteryInId : batteryInIds) {
            SwapService self = context.getBean(SwapService.class);
            SwapResponseDTO response = self.handleSingleSwap(booking, batteryInId, currentStaffUserId);
            results.add(response);
        }

        booking.setBookingStatus(Booking.BookingStatus.COMPLETED);
        booking.setCompletedTime(LocalDate.now());
        bookingRepository.save(booking);

        return results.size() == 1 ? results.get(0) : results;
    }

    // ====================== HANDLE SINGLE SWAP ======================
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected SwapResponseDTO handleSingleSwap(Booking booking, String batteryInId, String staffUserId) {
        Integer stationId = booking.getStation().getStationId();
        Battery batteryIn = batteryRepository.findById(batteryInId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy pin khách đưa: " + batteryInId));

        if (!batteryIn.isActive())
            throw new IllegalStateException("Pin " + batteryInId + " bị vô hiệu hóa.");
        if (batteryIn.getBatteryStatus() == Battery.BatteryStatus.MAINTENANCE)
            throw new IllegalStateException("Pin " + batteryInId + " đang bảo trì.");

        // 🔹 Tìm dock có pin đầy đúng model (pinOut)
        DockSlot dockOutSlot = dockSlotRepository
                .findFirstByDock_Station_StationIdAndBattery_BatteryTypeAndBattery_BatteryStatusAndSlotStatusOrderByDock_DockNameAscSlotNumberAsc(
                        stationId,
                        batteryIn.getBatteryType(),
                        Battery.BatteryStatus.AVAILABLE,
                        DockSlot.SlotStatus.OCCUPIED
                )
                .orElseThrow(() -> new IllegalStateException("Không còn pin đầy đúng model trong trạm."));

        Battery batteryOut = dockOutSlot.getBattery();
        if (batteryOut == null)
            throw new IllegalStateException("Slot chứa pinOut không hợp lệ (không có pin).");


        String dockCode = dockOutSlot.getDock().getDockName() + dockOutSlot.getSlotNumber();

        // Nhả pinOut
        batteryOut.setBatteryStatus(Battery.BatteryStatus.IN_USE);
        batteryOut.setStationId(null);
        batteryOut.setDockSlot(null);
        dockOutSlot.setBattery(null);
        dockOutSlot.setSlotStatus(DockSlot.SlotStatus.EMPTY);

        // Gắn pinIn vào lại chính dock đó
        batteryIn.setBatteryStatus(Battery.BatteryStatus.AVAILABLE);
        batteryIn.setStationId(stationId);
        batteryIn.setDockSlot(dockOutSlot);
        dockOutSlot.setBattery(batteryIn);
        dockOutSlot.setSlotStatus(DockSlot.SlotStatus.OCCUPIED);

        // Lưu DB
        batteryRepository.save(batteryOut);
        batteryRepository.save(batteryIn);
        dockSlotRepository.save(dockOutSlot);

        // Tạo bản ghi swap
        Swap swap = Swap.builder()
                .booking(booking)
                .dockId(dockOutSlot.getDock().getDockId())
                .userId(booking.getUser().getUserId())
                .batteryOutId(batteryOut.getBatteryId())
                .batteryInId(batteryIn.getBatteryId())
                .staffUserId(staffUserId)
                .status(Swap.SwapStatus.SUCCESS)
                .dockOutSlot(dockCode)
                .dockInSlot(dockCode)
                .completedTime(LocalDateTime.now())
                .description("Swap hoàn tất (pinIn vào đúng dock của pinOut).")
                .build();

        swapRepository.save(swap);

        // Trả về response như cũ
        return SwapResponseDTO.builder()
                .swapId(swap.getSwapId())
                .status("SUCCESS")
                .message("Swap hoàn tất (pinIn/pinOut cùng dockSlot).")
                .bookingId(booking.getBookingId())
                .batteryOutId(batteryOut.getBatteryId())
                .batteryInId(batteryIn.getBatteryId())
                .dockOutSlot(dockCode)
                .dockInSlot(dockCode)
                .build();
    }


    // ====================== RESOLVE STAFF ID ======================
    private String resolveStaffUserId(SwapRequest request) {
        Authentication auth = SecurityContextHolder.getContext() != null
                ? SecurityContextHolder.getContext().getAuthentication()
                : null;

        if (auth != null && auth.isAuthenticated()) {
            String name = auth.getName();
            if (name != null && !"anonymousUser".equalsIgnoreCase(name)) {
                return name;
            }
        }

        if (request.getStaffUserId() != null && !request.getStaffUserId().isBlank()) {
            return request.getStaffUserId();
        }

        throw new IllegalStateException("Thiếu staffUserId (chưa đăng nhập staff hoặc không truyền staffUserId).");
    }
}
