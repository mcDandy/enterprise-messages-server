package cz.upce.fei.ems.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.config.path:#{null}}")
    private String configPath;

    @PostConstruct
    public void initialize() {
        try {
            InputStream serviceAccount = null;

            String envJson = System.getenv("FIREBASE_CONFIG_JSON");
            if (envJson != null && !envJson.isBlank()) {
                serviceAccount = new ByteArrayInputStream(envJson.getBytes(StandardCharsets.UTF_8));
            }
            else if (configPath != null && !configPath.isBlank()) {
                serviceAccount = new FileInputStream(configPath);
            }

            if (serviceAccount == null) {
                System.err.println("Firebase credentials not found in environment variables (FIREBASE_CONFIG_JSON)!");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        } catch (IOException e) {
            System.err.println("Failed to initialize Firebase: " + e.getMessage());
        }
    }
}