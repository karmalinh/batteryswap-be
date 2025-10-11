package BatterySwapStation.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.gmail.Gmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GmailApiConfig {

    @Value("${gmail.client.id}")
    private String clientId;

    @Value("${gmail.client.secret}")
    private String clientSecret;

    @Value("${gmail.refresh.token}")
    private String refreshToken;

    @Bean
    public Gmail gmailService() throws Exception {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = JacksonFactory.getDefaultInstance();

        GoogleCredential credential = new GoogleCredential.Builder()
                .setClientSecrets(clientId, clientSecret)
                .setTransport(transport)
                .setJsonFactory(jsonFactory)
                .build()
                .setRefreshToken(refreshToken);

        if (!credential.refreshToken()) {
            throw new IllegalStateException("❌ Refresh token không hợp lệ hoặc đã hết hạn. Kiểm tra biến môi trường trên Railway!");
        }

        System.out.println("✅ Gmail API đã khởi tạo thành công trên server.");

        return new Gmail.Builder(transport, jsonFactory, credential)
                .setApplicationName("BatterySwapStation")
                .build();
    }
}
