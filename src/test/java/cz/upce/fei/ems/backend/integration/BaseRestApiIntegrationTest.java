package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.dto.LoginRequestDto;
import cz.upce.fei.ems.backend.dto.RegisterRequestDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "JWT_SECRET=integration-test-secret-key-at-least-32-characters-long-so-it-is-safe",
    "JWT_EXPIRATION=3600000"
})
public abstract class BaseRestApiIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AppUserRepository userRepository;

    protected HttpClient httpClient;

    @BeforeEach
    void setUpBase() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }

    protected String loginAndGetToken(String username, String password) throws Exception {
        LoginRequestDto loginRequest = new LoginRequestDto();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);
        
        HttpResponse<String> response = post("/api/auth/login", null, loginRequest);
        if (response.statusCode() != 200) {
            RegisterRequestDto registerRequest = new RegisterRequestDto();
            registerRequest.setUsername(username);
            registerRequest.setPassword(password);
            registerRequest.setPublicKey("initial-key");
            post("/api/auth/register", null, registerRequest);
            
            response = post("/api/auth/login", null, loginRequest);
        }
        
        JsonNode node = objectMapper.readTree(response.body());
        return node.get("token").asText();
    }

    protected HttpResponse<String> post(String path, String token, Object body) throws Exception {
        String jsonBody = body == null ? "" : objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .GET();

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> put(String path, String token, Object body) throws Exception {
        String jsonBody = body == null ? "" : objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .DELETE();

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
