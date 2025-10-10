package BatterySwapStation.service;

import BatterySwapStation.entity.EmailVerificationToken;
import BatterySwapStation.entity.User;
import BatterySwapStation.repository.EmailVerificationTokenRepository;
import BatterySwapStation.repository.UserRepository;
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

    public String verifyEmail(String token) {
        var verification = tokenRepo.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token không tồn tại"));

        if (verification.isUsed()) {
            throw new RuntimeException("Token đã được sử dụng");
        }
        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token đã hết hạn");
        }

        User user = verification.getUser();
        user.setVerified(true);
        userRepo.save(user);

        verification.setUsed(true);
        tokenRepo.save(verification);

        return "Xác thực email thành công!";
    }
}
