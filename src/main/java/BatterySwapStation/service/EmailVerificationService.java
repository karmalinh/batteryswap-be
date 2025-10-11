package BatterySwapStation.service;

import BatterySwapStation.entity.EmailVerificationToken;
import BatterySwapStation.entity.User;
import BatterySwapStation.repository.EmailVerificationTokenRepository;
import BatterySwapStation.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepo;
    private final UserRepository userRepo;

    public String createVerificationToken(User user) {
        String token = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .token(token)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        tokenRepo.save(verificationToken);
        return token;
    }

    @Transactional
    public String verifyEmail(String token) {
        var verification = tokenRepo.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Liên kết xác thực không hợp lệ hoặc đã hết hạn."));

        if (verification.isUsed()) {
            throw new RuntimeException("Liên kết này đã được sử dụng.");
        }

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Liên kết xác thực đã hết hạn.");
        }

        // ✅ Đánh dấu token đã dùng
        verification.setUsed(true);
        tokenRepo.save(verification);

        // ✅ Kích hoạt tài khoản user
        User user = verification.getUser();
        user.setVerified(true);
        userRepo.save(user);

        return "Tài khoản " + user.getEmail() + " đã được xác thực thành công!";
    }


}