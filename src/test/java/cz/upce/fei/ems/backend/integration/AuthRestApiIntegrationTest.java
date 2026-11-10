package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.dto.AuthResponseDto;
import cz.upce.fei.ems.backend.dto.LoginRequestDto;
import cz.upce.fei.ems.backend.dto.RegisterRequestDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AuthRestApiIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void shouldRegisterAndLoginSuccessfully() throws Exception {
        String username = "auth_test_user_" + UUID.randomUUID().toString().replace("-", "");
        RegisterRequestDto registerRequest = new RegisterRequestDto();
        registerRequest.setUsername(username);
        registerRequest.setPassword("TestPassword123!");
        registerRequest.setPublicKey("test-public-key");

        HttpResponse<String> registerResponse = post("/api/auth/register", null, registerRequest);
        assertEquals(201, registerResponse.statusCode());
        
        JsonNode registerNode = objectMapper.readTree(registerResponse.body());
        assertNotNull(registerNode.get("token").asText());
        assertEquals(username, registerNode.get("username").asText());

        LoginRequestDto loginRequest = new LoginRequestDto();
        loginRequest.setUsername(username);
        loginRequest.setPassword("TestPassword123!");

        HttpResponse<String> loginResponse = post("/api/auth/login", null, loginRequest);
        assertEquals(200, loginResponse.statusCode());
        
        JsonNode loginNode = objectMapper.readTree(loginResponse.body());
        assertNotNull(loginNode.get("token").asText());
        assertEquals(username, loginNode.get("username").asText());
    }

    @Test
    void shouldFailLoginWithWrongPassword() throws Exception {
        String username = "auth_fail_user_" + UUID.randomUUID().toString().replace("-", "");
        RegisterRequestDto registerRequest = new RegisterRequestDto();
        registerRequest.setUsername(username);
        registerRequest.setPassword("CorrectPassword123!");
        registerRequest.setPublicKey("test-public-key");

        post("/api/auth/register", null, registerRequest);

        LoginRequestDto loginRequest = new LoginRequestDto();
        loginRequest.setUsername(username);
        loginRequest.setPassword("WrongPassword123!");

        HttpResponse<String> loginResponse = post("/api/auth/login", null, loginRequest);
        assertEquals(401, loginResponse.statusCode());
    }

    @Test
    void shouldGetUserPublicKey() throws Exception {
        String username = "key_user_" + UUID.randomUUID().toString().replace("-", "");
        RegisterRequestDto registerRequest = new RegisterRequestDto();
        registerRequest.setUsername(username);
        registerRequest.setPassword("TestPassword123!");
        registerRequest.setPublicKey("target-public-key");

        post("/api/auth/register", null, registerRequest);
        Long userId = userRepository.findByUsername(username).orElseThrow().getId();

        HttpResponse<String> keyResponse = get("/api/auth/public-key/" + userId, null);
        assertEquals(200, keyResponse.statusCode());
        
        JsonNode keyNode = objectMapper.readTree(keyResponse.body());
        assertEquals("target-public-key", keyNode.get("publicKey").asText());
    }
}
