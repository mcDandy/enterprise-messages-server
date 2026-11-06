package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.dto.AuthResponseDto;
import cz.upce.fei.ems.backend.dto.LoginRequestDto;
import cz.upce.fei.ems.backend.dto.RegisterRequestDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AuthRestApiIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void shouldRegisterAndLoginSuccessfully() throws Exception {
        RegisterRequestDto registerRequest = new RegisterRequestDto();
        registerRequest.setUsername("auth_test_user");
        registerRequest.setPassword("TestPassword123!");
        registerRequest.setPublicKey("test-public-key");

        HttpResponse<String> registerResponse = post("/api/auth/register", null, registerRequest);
        assertEquals(201, registerResponse.statusCode());
        
        JsonNode registerNode = objectMapper.readTree(registerResponse.body());
        assertNotNull(registerNode.get("token").asText());
        assertEquals("auth_test_user", registerNode.get("username").asText());

        LoginRequestDto loginRequest = new LoginRequestDto();
        loginRequest.setUsername("auth_test_user");
        loginRequest.setPassword("TestPassword123!");

        HttpResponse<String> loginResponse = post("/api/auth/login", null, loginRequest);
        assertEquals(200, loginResponse.statusCode());
        
        JsonNode loginNode = objectMapper.readTree(loginResponse.body());
        assertNotNull(loginNode.get("token").asText());
        assertEquals("auth_test_user", loginNode.get("username").asText());
    }

    @Test
    void shouldFailLoginWithWrongPassword() throws Exception {
        RegisterRequestDto registerRequest = new RegisterRequestDto();
        registerRequest.setUsername("auth_fail_user");
        registerRequest.setPassword("CorrectPassword123!");
        registerRequest.setPublicKey("test-public-key");

        post("/api/auth/register", null, registerRequest);

        LoginRequestDto loginRequest = new LoginRequestDto();
        loginRequest.setUsername("auth_fail_user");
        loginRequest.setPassword("WrongPassword123!");

        HttpResponse<String> loginResponse = post("/api/auth/login", null, loginRequest);
        assertEquals(401, loginResponse.statusCode());
    }

    @Test
    void shouldGetUserPublicKey() throws Exception {
        RegisterRequestDto registerRequest = new RegisterRequestDto();
        registerRequest.setUsername("key_user");
        registerRequest.setPassword("TestPassword123!");
        registerRequest.setPublicKey("target-public-key");

        post("/api/auth/register", null, registerRequest);
        Long userId = userRepository.findByUsername("key_user").orElseThrow().getId();

        HttpResponse<String> keyResponse = get("/api/auth/public-key/" + userId, null);
        assertEquals(200, keyResponse.statusCode());
        
        JsonNode keyNode = objectMapper.readTree(keyResponse.body());
        assertEquals("target-public-key", keyNode.get("publicKey").asText());
    }
}
