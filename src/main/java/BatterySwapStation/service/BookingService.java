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
import java.time.LocalDateTime;
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
     * Tạo đặt chỗ mới (giới hạn tối đa 1 xe, chỉ 1 trạm, ngày trong 2 ngày, khung giờ hợp lệ)
     */
    public BookingResponse createBooking(BookingRequest request) {
        // Xác thực xe trước tiên để lấy userId
        Integer vehicleId = request.getVehicleId();
        if (vehicleId == null) {
            throw new IllegalArgumentException("Bạn phải chọn một xe để đặt pin.");
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy xe với mã: " + vehicleId));

        // Tự động lấy userId từ vehicle
        if (vehicle.getUser() == null) {
            throw new IllegalArgumentException("Xe này chưa được đăng ký cho người dùng nào.");
        }

        User user = vehicle.getUser();

        // Xác thực trạm
        Station station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy trạm với mã: " + request.getStationId()));

        // Kiểm tra ngày đặt chỗ trong vòng 7 ngày
        LocalDate now = LocalDate.now();
        if (request.getBookingDate().isBefore(now) || request.getBookingDate().isAfter(now.plusDays(7))) {
            throw new IllegalArgumentException("Ngày đặt pin phải nằm trong vòng 7 ngày kể từ hôm nay.");
        }

        // Kiểm tra khung giờ hợp lệ (chỉ nhận các giá trị ví dụ: 8h30, 10h, 20h30)
        if (request.getTimeSlot() == null) {
            throw new IllegalArgumentException("Bạn phải chọn khung giờ.");
        }

        // Chuyển đổi String sang LocalTime
        LocalTime timeSlot = LocalTime.parse(request.getTimeSlot(), java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

        // Hệ thống hoạt động 24/7 - không giới hạn khung giờ

        // Kiểm tra người dùng đã có đặt chỗ đang hoạt động chưa
        LocalDate currentDate = LocalDate.now();
        if (bookingRepository.existsActiveBookingForUserByDate(user, currentDate)) {
            throw new IllegalStateException("Bạn đã có một lượt đặt pin đang hoạt động.");
        }

        // Kiểm tra khung giờ đã được đặt chưa
        if (bookingRepository.existsBookingAtTimeSlot(station, request.getBookingDate(), timeSlot)) {
            throw new IllegalStateException("Khung giờ này đã có người đặt trước.");
        }

        // Tính giá tiền dựa trên loại xe (giá mặc định: 15,000 VNĐ)
        Double bookingAmount = 15000.0;

        // Lấy vehicleType từ vehicle
        String vehicleTypeStr = vehicle.getVehicleType() != null ? vehicle.getVehicleType().toString() : "UNKNOWN";

        // Tạo đặt chỗ mới
        Booking booking = Booking.builder()
                .user(user)
                .station(station)
                .vehicle(vehicle)
                .vehicleType(vehicleTypeStr) // Lưu loại xe
                .amount(bookingAmount) // Lưu giá tiền
                .bookingDate(request.getBookingDate())
                .timeSlot(timeSlot)
                .bookingStatus(Booking.BookingStatus.PENDING)  // Sử dụng enum
                .notes("Đặt lịch qua API")
                .build();
        Booking savedBooking = bookingRepository.save(booking);

        // Tạo các mục pin nếu có
        if (request.getBatteryItems() != null && !request.getBatteryItems().isEmpty()) {
            // TODO: Triển khai logic các mục pin
        }

        return convertToResponse(savedBooking);
    }

    /**
     * Lấy danh sách đặt chỗ của người dùng
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
     * Lấy đặt chỗ theo ID
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
     * Hủy đặt chỗ
     */
    public BookingResponse cancelBooking(CancelBookingRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với mã: " + request.getUserId()));

        Booking booking = bookingRepository.findByBookingIdAndUser(request.getBookingId(), user)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lượt đặt pin với mã: " + request.getBookingId()));

        // Kiểm tra đặt chỗ có thể hủy không
        if (Booking.BookingStatus.CANCELLED.equals(booking.getBookingStatus())) {
            throw new IllegalStateException("Lượt đặt pin này đã bị hủy trước đó.");
        }

        if (Booking.BookingStatus.COMPLETED.equals(booking.getBookingStatus())) {
            throw new IllegalStateException("Không thể hủy lượt đặt pin đã hoàn thành.");
        }

        // Hủy đặt chỗ
        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        return convertToResponse(savedBooking);
    }

    /**
     * Lấy danh sách đặt chỗ theo trạng thái
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByStatus(String status) {
        // Chuyển đổi String sang enum
        try {
            Booking.BookingStatus bookingStatus = Booking.BookingStatus.valueOf(status.toUpperCase());
            List<Booking> bookings = bookingRepository.findByBookingStatus(bookingStatus);
            return bookings.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + status + ". Các trạng thái hợp lệ: PENDING, CONFIRMED, CANCELLED, COMPLETED");
        }
    }

    /**
     * Lấy danh sách đặt chỗ của trạm
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
     * Cập nhật trạng thái đặt chỗ
     */
    public BookingResponse updateBookingStatus(Long bookingId, String newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lượt đặt pin với mã: " + bookingId));

        // Chuyển đổi String sang enum
        try {
            Booking.BookingStatus status = Booking.BookingStatus.valueOf(newStatus.toUpperCase());
            booking.setBookingStatus(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + newStatus + ". Các trạng thái hợp lệ: PENDING, CONFIRMED, CANCELLED, COMPLETED");
        }

        Booking savedBooking = bookingRepository.save(booking);

        return convertToResponse(savedBooking);
    }

    /**
     * Lấy tất cả đặt chỗ (dành cho quản trị viên)
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings() {
        List<Booking> bookings = bookingRepository.findAll();
        return bookings.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Chuyển đổi Booking entity thành BookingResponse DTO
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

        // Thêm vehicleType và amount
        response.setVehicleType(booking.getVehicleType());
        response.setAmount(booking.getAmount());

        // Sử dụng bookingDate và timeSlot trực tiếp
        response.setBookingDate(booking.getBookingDate());
        response.setTimeSlot(booking.getTimeSlot());
        response.setBookingStatus(booking.getBookingStatus().toString());  // Chuyển enum sang String

        // TODO: Thêm mapping các mục pin
        // TODO: Thêm mapping thông tin thanh toán

        return response;
    }
}
