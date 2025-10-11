package BatterySwapStation.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final Gmail gmail;

    // 🟢 Địa chỉ Gmail dùng để gửi (chính là Gmail bạn cấp refresh token)
    @Value("${gmail.user}")
    private String gmailUser;

    /**
     * Gửi email xác minh tài khoản đến người dùng
     */
    public void sendVerificationEmail(String fullName, String email, String verifyUrl) {
        String html = """
                <html>
                    <body style='font-family:Arial, sans-serif;'>
                        <h2>Xin chào, %s!</h2>
                        <p>Cảm ơn bạn đã đăng ký tài khoản <b>Battery Swap Station</b>.</p>
                        <p>Nhấn vào nút dưới đây để xác thực email của bạn:</p>
                        <a href='%s'
                           style='display:inline-block;padding:10px 20px;
                                  background-color:#4CAF50;color:white;
                                  text-decoration:none;border-radius:5px;'>
                           Xác thực tài khoản
                        </a>
                        <br><br>
                        <p>Nếu bạn không yêu cầu đăng ký, vui lòng bỏ qua email này.</p>
                        <hr>
                        <small>Đây là email tự động, vui lòng không trả lời.</small>
                    </body>
                </html>
                """.formatted(fullName, verifyUrl);

        sendViaGmailApi(email, "Xác minh tài khoản Battery Swap Station", html);
    }

    /**
     * Gửi email HTML thông qua Gmail API
     */
    private void sendViaGmailApi(String to, String subject, String htmlContent) {
        try {
            // 🔹 Chuẩn bị session
            Properties props = new Properties();
            Session session = Session.getInstance(props, null);

            // 🔹 Tạo nội dung email
            MimeMessage mime = new MimeMessage(session);
            mime.setFrom(new InternetAddress(gmailUser, "Battery Swap Station", StandardCharsets.UTF_8.name()));
            mime.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
            mime.setSubject(subject, StandardCharsets.UTF_8.name());
            mime.setContent(htmlContent, "text/html; charset=UTF-8");

            // 🔹 Encode email theo định dạng Gmail yêu cầu
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mime.writeTo(buffer);
            String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());

            // 🔹 Gửi qua Gmail API
            Message message = new Message();
            message.setRaw(encodedEmail);
            gmail.users().messages().send("me", message).execute();

            System.out.println("✅ Email đã gửi đến: " + to);

        } catch (Exception e) {
            System.err.println("❌ Gửi email thất bại: " + e.getMessage());
            throw new RuntimeException("Gửi email thất bại: " + e.getMessage(), e);
        }
    }
}
