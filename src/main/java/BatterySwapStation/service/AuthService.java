package BatterySwapStation.service;

import java.time.LocalDateTime;

import BatterySwapStation.dto.RoleDTO;
import BatterySwapStation.utils.UserIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import BatterySwapStation.dto.LoginRequest;
import BatterySwapStation.dto.RegisterRequest;
import BatterySwapStation.dto.AuthResponse;
import BatterySwapStation.entity.Role;
import BatterySwapStation.entity.User;
import BatterySwapStation.repository.RoleRepository;
import BatterySwapStation.repository.UserRepository;


@Service
@RequiredArgsConstructor

public class AuthService {

    private UserRepository userRepository;
    private final UserService userService;
    private RoleRepository roleRepository;
    //    @Autowired
//    private final UserIdGenerator userIdGenerator;
    private final JwtService jwtService;

    //private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


//    // Đăng ký
//    public AuthResponse register(RegisterRequest request) {
//        if (userRepository.existsByEmail(request.getEmail())) {
//            throw new RuntimeException("Email đã tồn tại!");
//        }
//
//        Role role = roleRepository.findById(request.getRoleId())
//                .orElseThrow(() -> new RuntimeException("Role không tồn tại!"));
//
//        // Đếm số user đã có trong role này
//        String generatedId = userIdGenerator.generateUserId(role);
//
//        // Tạo user mới
//        User user = new User();
//        user.setUserId(generatedId); // ✅ Gán ID trước khi save
//        user.setFullName(request.getFullName());
//        user.setEmail(request.getEmail());
//        user.setPhone(request.getPhone());
//        user.setAddress(request.getAddress());
//        user.setPassword(passwordEncoder.encode(request.getPassword()));
//        user.setRole(role);
//
//        userRepository.save(user);
//
//        return new AuthResponse(
//                "Đăng ký thành công",
//                user.getUserId(),
//                user.getEmail(),
//                role.getRoleName(),
//                "fake-jwt-token"
//        );
//    }


    // Đăng nhập
    public AuthResponse login(LoginRequest req) {
        User user = userService.findByEmail(req.getEmail());
        if (user == null) {
            throw new RuntimeException("Email không tồn tại");
        }

        if (!userService.checkPassword(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Mật khẩu không đúng");
        }

        if (user.getRole() == null || user.getRole().getRoleId() != req.getRoleId()) {
            throw new RuntimeException("Loại tài khoản không hợp lệ");
        }

        String token = jwtService.generateToken(
                user.getUserId(),
                user.getEmail(),
                user.getRole().getRoleName()
        );

        return new AuthResponse(
                "Đăng nhập thành công",
                user.getUserId(),
                user.getEmail(),
                user.getRole().getRoleName(),
                token
        );
    }

//    public AuthResponse updateRole(String userId, int newRoleId) {
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User không tồn tại"));
//
//        Role newRole = roleRepository.findById(newRoleId)
//                .orElseThrow(() -> new RuntimeException("Role không tồn tại"));
//
//        user.setRole(newRole);
//        user.setUpdateAt(LocalDateTime.now());
//        userRepository.save(user);
//
//        return new AuthResponse(
//                "Cập nhật role thành công",
//                user.getUserId(),
//                user.getEmail(),
//                newRole.getRoleName(),
//                "fake-jwt-token"
//        );
//    }


    // Cập nhật role cho user (nhận userId và RoleDTO)
    public boolean updateUserRole(String userId, RoleDTO roleDTO) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;

        Role role = null;
        if (roleDTO.getRoleId() != 0) {
            role = roleRepository.findById(roleDTO.getRoleId()).orElse(null);
        }
        if (role == null && roleDTO.getRoleName() != null) {
            role = roleRepository.findByRoleName(roleDTO.getRoleName());
        }

        if (role == null) return false;

        user.setRole(role);
        user.setUpdateAt(LocalDateTime.now());
        userRepository.save(user);
        return true;
    }
}
