package BatterySwapStation.service;

import BatterySwapStation.dto.BookingRequest;
import BatterySwapStation.dto.BookingResponse;
import BatterySwapStation.dto.CancelBookingRequest;
import BatterySwapStation.entity.*;
import BatterySwapStation.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final StationRepository stationRepository;
    private final VehicleRepository vehicleRepository;

    /**
     * Tạo booking mới (giới hạn tối đa 3 xe, chỉ 1 trạm, ngày trong 2 ngày, khung giờ hợp lệ)
     */
    public BookingResponse createBooking(BookingRequest request) {
        // Validate user
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với mã: " + request.getUserId()));

        // Validate station
        Station station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy trạm với mã: " + request.getStationId()));

        // Validate vehicle
        Integer vehicleId = request.getVehicleId();
        if (vehicleId == null) {
            throw new IllegalArgumentException("Bạn phải chọn một xe để đặt pin.");
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy xe với mã: " + vehicleId));

        // Kiểm tra xe thuộc trạm được chọn
        if (vehicle.getUser() == null || !vehicle.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("Xe này không thuộc về bạn.");
        }

        // Kiểm tra ngày booking trong vòng 2 ngày
        LocalDate now = LocalDate.now();
        if (request.getBookingDate().isBefore(now) || request.getBookingDate().isAfter(now.plusDays(2))) {
            throw new IllegalArgumentException("Ngày đặt pin phải nằm trong vòng 2 ngày kể từ hôm nay.");
        }

        // Kiểm tra timeSlot hợp lệ (chỉ nhận các giá trị: 1h30, 2h, 2h30)
        if (request.getTimeSlot() == null) {
            throw new IllegalArgumentException("Bạn phải chọn khung giờ.");
        }

        // Chuyển đổi LocalTime thành String để so sánh
        String timeSlotStr = request.getTimeSlot().toString();
        if (!("01:30".equals(timeSlotStr) || "02:00".equals(timeSlotStr) || "02:30".equals(timeSlotStr))) {
            throw new IllegalArgumentException("Khung giờ chỉ được chọn 1h30, 2h hoặc 2h30.");
        }

        // Kiểm tra user đã có booking active chưa
        if (bookingRepository.existsActiveBookingForUser(user, now)) {
            throw new IllegalStateException("Bạn đã có một lượt đặt pin đang hoạt động.");
        }

        // Kiểm tra time slot đã được đặt chưa
        if (bookingRepository.existsBookingAtTimeSlot(station, request.getBookingDate(), request.getTimeSlot())) {
            throw new IllegalStateException("Khung giờ này đã có người đặt trước.");
        }

        // Tạo booking cho từng xe (hoặc gộp nếu cần)
        Booking booking = Booking.builder()
                .user(user)
                .station(station)
                .vehicle(vehicle) // Nếu chỉ 1 xe, lấy xe đầu tiên
                .bookingDate(request.getBookingDate())
                .timeSlot(request.getTimeSlot())
                .bookingStatus(Booking.BookingStatus.PENDING)
                .build();
        Booking savedBooking = bookingRepository.save(booking);
        // TODO: Nếu cần gộp hóa đơn, lưu thông tin liên kết các booking lại

        // Tạo battery items nếu có
        if (request.getBatteryItems() != null && !request.getBatteryItems().isEmpty()) {
            // TODO: Implement battery items logic
        }

        return convertToResponse(savedBooking);
    }

    /**
     * Lấy danh sách booking của user
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với mã: " + userId));

        List<Booking> bookings = bookingRepository.findByUser(user);
        return bookings.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy booking theo ID
     */
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long bookingId, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với mã: " + userId));

        Booking booking = bookingRepository.findByBookingIdAndUser(bookingId, user)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lượt đặt pin với mã: " + bookingId));

        return convertToResponse(booking);
    }

    /**
     * Hủy booking
     */
    public BookingResponse cancelBooking(CancelBookingRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với mã: " + request.getUserId()));

        Booking booking = bookingRepository.findByBookingIdAndUser(request.getBookingId(), user)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lượt đặt pin với mã: " + request.getBookingId()));

        // Kiểm tra booking có thể hủy không
        if (booking.getBookingStatus() == Booking.BookingStatus.CANCELLED) {
            throw new IllegalStateException("Lượt đặt pin này đã bị hủy trước đó.");
        }

        if (booking.getBookingStatus() == Booking.BookingStatus.COMPLETED) {
            throw new IllegalStateException("Không thể hủy lượt đặt pin đã hoàn thành.");
        }

        // Hủy booking
        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        return convertToResponse(savedBooking);
    }

    /**
     * Lấy danh sách booking theo trạng thái
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByStatus(Booking.BookingStatus status) {
        List<Booking> bookings = bookingRepository.findByBookingStatus(status);
        return bookings.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách booking của station
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getStationBookings(Integer stationId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy trạm với mã: " + stationId));

        List<Booking> bookings = bookingRepository.findByStation(station);
        return bookings.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Cập nhật trạng thái booking
     */
    public BookingResponse updateBookingStatus(Long bookingId, Booking.BookingStatus newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lượt đặt pin với mã: " + bookingId));

        booking.setBookingStatus(newStatus);
        Booking savedBooking = bookingRepository.save(booking);

        return convertToResponse(savedBooking);
    }

    /**
     * Lấy tất cả booking (dành cho admin)
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings() {
        List<Booking> bookings = bookingRepository.findAll();
        return bookings.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Convert Booking entity to BookingResponse DTO
     */
    private BookingResponse convertToResponse(Booking booking) {
        BookingResponse response = new BookingResponse();
        response.setBookingId(booking.getBookingId());
        response.setUserId(booking.getUser().getUserId());
        response.setUserName(booking.getUser().getFullName());
        response.setStationId(booking.getStation().getStationId());
        response.setStationName(booking.getStation().getStationName());
        response.setStationAddress(booking.getStation().getAddress());

        if (booking.getVehicle() != null) {
            response.setVehicleId(booking.getVehicle().getVehicleId());
            response.setVehicleVin(booking.getVehicle().getVIN());
        }

        response.setBookingDate(booking.getBookingDate());
        response.setTimeSlot(booking.getTimeSlot());
        response.setBookingStatus(booking.getBookingStatus().toString());

        // TODO: Add battery items mapping
        // TODO: Add payment info mapping

        return response;
    }
}
