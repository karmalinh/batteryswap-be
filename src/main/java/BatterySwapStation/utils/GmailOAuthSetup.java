package BatterySwapStation.utils;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;

import java.io.FileReader;
import java.nio.file.Paths;
import java.util.Collections;

public class GmailOAuthSetup {
    public static void main(String[] args) throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = JacksonFactory.getDefaultInstance();

        // ✅ Đường dẫn tới file JSON bạn vừa tải về
        String clientSecretPath = Paths.get("client_secret_desktop.json").toAbsolutePath().toString();

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(jsonFactory, new FileReader(clientSecretPath));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                jsonFactory,
                clientSecrets,
                Collections.singleton(GmailScopes.GMAIL_SEND)
        )
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888) // callback port local
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user");

        System.out.println("✅ ACCESS TOKEN : " + credential.getAccessToken());
        System.out.println("✅ REFRESH TOKEN: " + credential.getRefreshToken());
        System.out.println("✅ Done. Copy REFRESH TOKEN vào biến môi trường Railway.");
    }
}
