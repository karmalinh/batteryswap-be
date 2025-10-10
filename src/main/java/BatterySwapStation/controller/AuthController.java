package BatterySwapStation.controller;

import BatterySwapStation.dto.*;

import BatterySwapStation.entity.*;
import BatterySwapStation.service.*;
import BatterySwapStation.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


import java.util.Map;

@RestController
@PreAuthorize("permitAll()")
@RequestMapping("/api/auth")
@RequiredArgsConstructor

public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;


    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        User user = userService.registerUser(req);

        // ✅ Tạo token và gửi email xác thực
        String token = emailVerificationService.createVerificationToken(user);
        String verifyUrl = "http://localhost:5173/verify-email?token=" + token;

        emailService.send(
                user.getEmail(),
                "Xác thực tài khoản BatterySwapStation",
                "Chào " + user.getFullName() + ",\n\nHãy xác thực tài khoản của bạn tại liên kết sau: " + verifyUrl
        );


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Đăng ký thành công! Vui lòng kiểm tra email để xác thực tài khoản.",
                        "userId", user.getUserId()
                ));
    }



    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PutMapping("/role/{userId}")
    public ResponseEntity<?> updateUserRole(
            @PathVariable String userId,
            @RequestBody RoleDTO roleDTO) {
        boolean updated = authService.updateUserRole(userId, roleDTO);
        if (updated) {
            return ResponseEntity.ok("Role updated successfully!");
        } else {
            return ResponseEntity.badRequest().body("User or Role not found!");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        return ResponseEntity.ok(Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "phone", user.getPhone(),
                "role", user.getRole().getRoleName()
        ));
    }


    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        try {
            String result = emailVerificationService.verifyEmail(token);
            return ResponseEntity.ok(Map.of(
                    "status", 200,
                    "success", true,
                    "message", result
            ));
        } catch (RuntimeException ex) {
            // ✅ Bắt lỗi từ EmailVerificationService
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "success", false,
                    "message", ex.getMessage()
            ));
        } catch (Exception ex) {
            // ✅ Lỗi bất ngờ
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", 500,
                    "success", false,
                    "message", "Đã xảy ra lỗi khi xác thực email. Vui lòng thử lại sau."
            ));
        }
    }



}

