package BatterySwapStation.service;

import jakarta.mail.MessagingException;
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

    private final String fromEmail = "batteryswapstation36@gmail.com";

    // ✅ Gửi email text đơn giản (ít dùng)
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    // ✅ Gửi email HTML xác thực tài khoản
    public void sendVerificationEmail(String fullName, String email, String verifyUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "Battery Swap Station");
            helper.setTo(email);
            helper.setSubject("Xác minh tài khoản Battery Swap Station");

            String html = """
                    <html>
                    <body style="font-family: Arial, sans-serif; background-color:#f8f9fa; padding:20px;">
                        <div style="max-width:600px; margin:auto; background:white; border-radius:10px; padding:20px;">
                            <h2>Xin chào %s,</h2>
                            <p>Cảm ơn bạn đã đăng ký tài khoản tại <b>Battery Swap Station</b>.</p>
                            <p>Vui lòng nhấn vào nút bên dưới để xác minh email của bạn:</p>
                            <div style="text-align:center; margin:30px;">
                                <a href="%s" style="background-color:#28a745; color:white; padding:12px 24px;
                                text-decoration:none; border-radius:6px;">Xác minh ngay</a>
                            </div>
                            <p>Nếu bạn không tạo tài khoản này, vui lòng bỏ qua email này.</p>
                            <hr>
                            <p style="font-size:12px; color:gray;">© 2025 Battery Swap Station Team</p>
                        </div>
                    </body>
                    </html>
                    """.formatted(fullName, verifyUrl);

            helper.setText(html, true);
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Không thể gửi email xác minh: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi gửi email: " + e.getMessage(), e);
        }
    }
}
