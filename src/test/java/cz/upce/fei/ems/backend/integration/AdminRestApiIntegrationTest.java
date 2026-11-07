package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.domain.AppUser;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class AdminRestApiIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void shouldBanAndUnbanUser() throws Exception {
        String adminToken = loginAndGetToken("global_admin", "Password123!");
        loginAndGetToken("target_user", "Password123!");
        
        AppUser admin = userRepository.findByUsername("global_admin").orElseThrow();
        admin.setMainAdmin(true);
        userRepository.save(admin);

        Long targetId = userRepository.findByUsername("target_user").orElseThrow().getId();

        HttpResponse<String> banResp = post("/api/admin/users/" + targetId + "/ban", adminToken, null);
        assertEquals(200, banResp.statusCode());

        AppUser target = userRepository.findById(targetId).orElseThrow();
        assertTrue(target.isBanned());

        HttpResponse<String> unbanResp = post("/api/admin/users/" + targetId + "/unban", adminToken, null);
        assertEquals(200, unbanResp.statusCode());

        target = userRepository.findById(targetId).orElseThrow();
        assertFalse(target.isBanned());
    }

    @Test
    void shouldDenyAccessForNonAdmin() throws Exception {
        String userToken = loginAndGetToken("regular_user", "Password123!");
        loginAndGetToken("target_user_2", "Password123!");
        
        AppUser regularUser = userRepository.findByUsername("regular_user").orElseThrow();
        regularUser.setMainAdmin(false);
        userRepository.save(regularUser);

        Long targetId = userRepository.findByUsername("target_user_2").orElseThrow().getId();

        HttpResponse<String> banResp = post("/api/admin/users/" + targetId + "/ban", userToken, null);
        assertEquals(403, banResp.statusCode());
    }
}
