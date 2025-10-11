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

        GoogleCredential cred = new GoogleCredential.Builder()
                .setClientSecrets(clientId, clientSecret)
                .setTransport(transport)
                .setJsonFactory(jsonFactory)
                .build()
                .setRefreshToken(refreshToken);

        // Lấy access token mới từ refresh token (server-side)
        cred.refreshToken();

        return new Gmail.Builder(transport, jsonFactory, cred)
                .setApplicationName("batteryswap")
                .build();
    }
}
