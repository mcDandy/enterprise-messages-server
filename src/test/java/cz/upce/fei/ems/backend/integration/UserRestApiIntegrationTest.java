package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.dto.RotatePublicKeyRequestDto;
import cz.upce.fei.ems.backend.dto.UpdateDisplayNameRequestDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class UserRestApiIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void shouldGetMe() throws Exception {
        String token = loginAndGetToken("user_me", "Password123!");

        HttpResponse<String> response = get("/api/users/me", token);
        assertEquals(200, response.statusCode());
        
        JsonNode node = objectMapper.readTree(response.body());
        assertEquals("user_me", node.get("username").asText());
    }

    @Test
    void shouldUpdateDisplayName() throws Exception {
        String token = loginAndGetToken("user_display", "Password123!");

        UpdateDisplayNameRequestDto request = new UpdateDisplayNameRequestDto();
        request.setDisplayName("New Display Name");

        HttpResponse<String> updateResponse = put("/api/users/me/display-name", token, request);
        assertEquals(200, updateResponse.statusCode());

        HttpResponse<String> meResponse = get("/api/users/me", token);
        JsonNode node = objectMapper.readTree(meResponse.body());
        assertEquals("New Display Name", node.get("displayName").asText());
    }

    @Test
    void shouldRotatePublicKey() throws Exception {
        String token = loginAndGetToken("user_keys", "Password123!");

        RotatePublicKeyRequestDto request = new RotatePublicKeyRequestDto();
        request.setPublicKey("new-rotated-key");

        HttpResponse<String> rotateResponse = post("/api/users/me/keys", token, request);
        assertEquals(200, rotateResponse.statusCode());

        Long userId = userRepository.findByUsername("user_keys").orElseThrow().getId();
        HttpResponse<String> keyResponse = get("/api/users/" + userId + "/key", token);
        assertEquals(200, keyResponse.statusCode());
        
        JsonNode node = objectMapper.readTree(keyResponse.body());
        assertEquals("new-rotated-key", node.get("publicKey").asText());
    }
}
