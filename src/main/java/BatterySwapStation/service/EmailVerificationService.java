package BatterySwapStation.service;

import BatterySwapStation.entity.EmailVerificationToken;
import BatterySwapStation.entity.User;
import BatterySwapStation.repository.EmailVerificationTokenRepository;
import BatterySwapStation.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final JwtService jwtService;

    public String createVerificationToken(User user) {
        String token = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .token(token)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        tokenRepo.save(verificationToken);
        return jwtService.generateVerifyEmailToken(user.getEmail());
    }

    @Transactional
    public String verifyEmail(String token) {
        String email;
        try {
            email = jwtService.extractEmailAllowExpired(token);
        } catch (Exception e) {
            throw new RuntimeException("Liên kết xác thực không hợp lệ hoặc đã bị thay đổi!");
        }

        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("Không tìm thấy người dùng.");
        }

        if (user.isVerified()) {
            throw new RuntimeException("Tài khoản này đã được xác thực trước đó!");
        }

        user.setVerified(true);
        userRepo.save(user);
        return "Tài khoản " + email + " đã được xác thực thành công!";
    }


    public User getUserByEmail(String email) {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("Không tìm thấy người dùng với email: " + email);
        }
        return user;
    }


}
