package BatterySwapStation.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender; // vẫn giữ để không phá code cũ
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender; // có thể không dùng trên Railway
    private final Gmail gmail;

    @Value("${gmail.user}")
    private String gmailUser;

    public void send(String to, String subject, String text) {
        // Giữ nguyên method cũ. Thử SMTP (dev local), nếu fail thì fallback Gmail API
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(text);
            mailSender.send(msg);
        } catch (Exception ex) {
            sendViaGmailApi(to, subject, text, false);
        }
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

        sendViaGmailApi(email, "Xác minh tài khoản Battery Swap Station", htmlContent, true);
    }

    private void sendViaGmailApi(String to, String subject, String content, boolean isHtml) {
        try {
            Properties props = new Properties();
            Session session = Session.getInstance(props, null);

            MimeMessage mm = new MimeMessage(session);
            mm.setFrom(new InternetAddress(gmailUser, "Battery Swap Station", StandardCharsets.UTF_8.name()));
            mm.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
            mm.setSubject(subject, StandardCharsets.UTF_8.name());

            if (isHtml) {
                mm.setContent(content, "text/html; charset=UTF-8");
            } else {
                mm.setText(content, StandardCharsets.UTF_8.name());
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mm.writeTo(buffer);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());

            Message gMsg = new Message();
            gMsg.setRaw(raw);

            gmail.users().messages().send("me", gMsg).execute(); // "me" = gmailUser đã ủy quyền
        } catch (Exception e) {
            throw new RuntimeException("Gmail API send failed: " + e.getMessage(), e);
        }
    }
}
