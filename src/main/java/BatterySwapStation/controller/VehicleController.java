package BatterySwapStation.controller;

import BatterySwapStation.dto.VehicleRegistrationRequest;
import BatterySwapStation.dto.ApiResponseDto; // Import DTO mới
import BatterySwapStation.entity.Vehicle;
import BatterySwapStation.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicle", description = "Vehicle management APIs")
@CrossOrigin
public class VehicleController {
    private final VehicleService vehicleService;

    @PostMapping("/register")
    @Operation(summary = "Register a new VinFast vehicle")
    public ResponseEntity<Vehicle> registerVehicle(@Valid @RequestBody VehicleRegistrationRequest request) {
        String userId = "DR001"; // Tạm thời sử dụng userId cố định để test
        Vehicle vehicle = vehicleService.registerVehicle(userId, request);
        return ResponseEntity.ok(vehicle);
    }

    @GetMapping("/my-vehicles")
    @Operation(summary = "Get all vehicles registered by the current user")
    public ResponseEntity<List<Vehicle>> getUserVehicles() {
        String userId = "DR001"; // Tạm thời sử dụng userId cố định để test
        List<Vehicle> vehicles = vehicleService.getUserVehicles(userId);
        return ResponseEntity.ok(vehicles);
    }

    @PutMapping("/{vehicleId}/deactivate")
    @Operation(summary = "Deactivate a registered vehicle, returns success message or error if already deactivated")
    // Cập nhật kiểu trả về sang ResponseEntity<ApiResponse>
    public ResponseEntity<ApiResponseDto> deactivateVehicle(@PathVariable int vehicleId) {
        String userId = "DR001"; // Tạm thời sử dụng userId cố định để test

        // 1. Gọi Service để thực hiện logic ngắt kết nối/deactivate
        // Service (VehicleService) cần ném ra một Exception tùy chỉnh
        // nếu phương tiện đã bị ngắt trước đó (ví dụ: VehicleAlreadyDeactivatedException).

        vehicleService.deactivateVehicle(vehicleId, userId);

        // 2. Nếu service không ném ra exception nào (tức là thành công), trả về thông báo.
        ApiResponseDto response = new ApiResponseDto(true, "Ngắt kết nối phương tiện thành công.");
        return ResponseEntity.ok(response);

        /*
         * LƯU Ý VỀ XỬ LÝ LỖI (Error Handling):
         * Để xử lý trường hợp "đã ngắt kết nối trước đó" (trả về lỗi),
         * bạn nên sử dụng @ControllerAdvice và @ExceptionHandler để bắt các Exception
         * từ tầng Service (ví dụ: VehicleAlreadyDeactivatedException) và tự động
         * trả về HTTP Status 409 Conflict hoặc 404 Not Found kèm theo thông báo lỗi
         * chi tiết trong body JSON.
         */
    }
}
