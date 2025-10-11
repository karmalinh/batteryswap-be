package BatterySwapStation.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    public void sendVerificationEmail(String fullName, String email, String verifyUrl) {
        String htmlContent = """
                <html>
                    <body>
                        <h2>Xin chào, %s!</h2>
                        <p>Cảm ơn bạn đã đăng ký tài khoản Battery Swap Station.</p>
                        <p>Nhấn vào nút dưới đây để xác thực email:</p>
                        <a href='%s'
                           style='display:inline-block;padding:10px 20px;
                                  background-color:#4CAF50;color:white;
                                  text-decoration:none;border-radius:5px;'>
                           Xác thực tài khoản
                        </a>
                        <p>Nếu bạn không yêu cầu đăng ký, vui lòng bỏ qua email này.</p>
                    </body>
                </html>
                """.formatted(fullName, verifyUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(email);
            helper.setSubject("Xác minh tài khoản Battery Swap Station");
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Không thể gửi email xác thực: " + e.getMessage());
        }
    }
}


