package BatterySwapStation.service;

import BatterySwapStation.dto.RoleDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import BatterySwapStation.dto.LoginRequest;
import BatterySwapStation.dto.AuthResponse;
import BatterySwapStation.entity.Role;
import BatterySwapStation.entity.User;
import BatterySwapStation.repository.RoleRepository;
import BatterySwapStation.repository.UserRepository;
import BatterySwapStation.dto.GoogleUserInfo;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

public class AuthService {

    private UserRepository userRepository;
    private final UserService userService;
    private RoleRepository roleRepository;

    //    @Autowired

    private final JwtService jwtService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    // Đăng nhập
    public AuthResponse login(LoginRequest req) {
        User user = userService.findByEmail(req.getEmail());
        if (user == null) {
            throw new RuntimeException("Email không tồn tại");
        }

        if (!userService.checkPassword(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Mật khẩu không đúng");
        }
        if (!user.isActive()) {
            throw new RuntimeException("Tài khoản của bạn đã bị ban do chua du trinh de vao.");
        }
        if (!user.isVerified()) {
            throw new RuntimeException("Bạn chưa xác thực email, chưa đủ điều kiện để đăng nhập.");
        }

        String token = jwtService.generateToken(
                user.getUserId(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().getRoleName()
        );

        return new AuthResponse(
                "Đăng nhập thành công",
                user.getUserId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole().getRoleName(),
                token
        );
    }



    // Cập nhật role cho user
    public boolean updateUserRole(String userId, RoleDTO roleDTO) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;

        Role role = null;
        if (roleDTO.getRoleId() != 0) {
            role = roleRepository.findByRoleId(roleDTO.getRoleId());
        }
        if (role == null && roleDTO.getRoleName() != null) {
            role = roleRepository.findByRoleName(roleDTO.getRoleName());
        }
        if (role == null) return false;

        user.setRole(role);
        userRepository.save(user);
        return true;
    }


    @Transactional
    public Map<String, Object> handleGoogleLogin(GoogleUserInfo info) {
        // tìm user theo email (User, không Optional)
        User user = userRepository.findByEmail(info.getEmail());

        if (user == null) {
            // tìm role USER (Role, không Optional)
            Role defaultRole = roleRepository.findByRoleName("USER");
            if (defaultRole == null) {
                throw new IllegalStateException("Role USER chưa tồn tại trong hệ thống");
            }

            user = new User();
            user.setUserId(generateUserId());
            user.setFullName(info.getName());
            user.setEmail(info.getEmail());
            user.setPassword(""); // để trống
            user.setAddress("");
            user.setPhone("");
            user.setActive(true);
            user.setVerified(info.isEmailVerified());
            user.setRole(defaultRole);

            userRepository.save(user);
        }

        // tạo JWT (hàm generateToken(User))
        String jwt = jwtService.generateToken(user);

        return Map.of(
                "token", jwt,
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "isNewUser", user.getPassword() == null || user.getPassword().isBlank()
        );
    }

    // Sinh mã userId tự động
    private String generateUserId() {
        long count = userRepository.count() + 1;
        return String.format("US%03d", count);
    }


}
