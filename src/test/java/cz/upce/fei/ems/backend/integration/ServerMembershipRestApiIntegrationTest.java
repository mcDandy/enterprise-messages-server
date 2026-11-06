package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.domain.MessageServerRole;
import cz.upce.fei.ems.backend.domain.MessageServerType;
import cz.upce.fei.ems.backend.dto.AddMemberRequestDto;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import cz.upce.fei.ems.backend.dto.UpdateMemberRoleRequestDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class ServerMembershipRestApiIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void shouldAddAndRemoveMember() throws Exception {
        String adminToken = loginAndGetToken("srv_admin", "Password123!");
        loginAndGetToken("srv_user", "Password123!");
        Long userId = userRepository.findByUsername("srv_user").orElseThrow().getId();

        // Create server
        CreateServerRequestDto serverReq = new CreateServerRequestDto();
        serverReq.setName("Srv Membership Server");
        serverReq.setType(MessageServerType.ORGANIZATION);
        HttpResponse<String> serverResp = post("/api/servers", adminToken, serverReq);
        Long serverId = objectMapper.readTree(serverResp.body()).get("id").asLong();

        // Add member
        AddMemberRequestDto addReq = new AddMemberRequestDto();
        addReq.setUserId(userId);
        addReq.setRole(MessageServerRole.MEMBER);
        HttpResponse<String> addResp = post("/api/servers/" + serverId + "/members", adminToken, addReq);
        assertEquals(200, addResp.statusCode());

        // Check members
        HttpResponse<String> getResp = get("/api/servers/" + serverId + "/members", adminToken);
        JsonNode node = objectMapper.readTree(getResp.body());
        assertTrue(node.isArray());
        boolean found = false;
        for (JsonNode member : node) {
            if (member.get("userId").asLong() == userId) {
                found = true;
                assertEquals(MessageServerRole.MEMBER.toString(), member.get("role").asText());
                break;
            }
        }
        assertTrue(found);

        // Update role
        UpdateMemberRoleRequestDto updateReq = new UpdateMemberRoleRequestDto();
        updateReq.setRole(MessageServerRole.ADMINISTRATOR);
        HttpResponse<String> updateResp = put("/api/servers/" + serverId + "/members/" + userId + "/role", adminToken, updateReq);
        assertEquals(200, updateResp.statusCode());

        // Check members again
        HttpResponse<String> getResp2 = get("/api/servers/" + serverId + "/members", adminToken);
        JsonNode node2 = objectMapper.readTree(getResp2.body());
        for (JsonNode member : node2) {
            if (member.get("userId").asLong() == userId) {
                assertEquals(MessageServerRole.ADMINISTRATOR.toString(), member.get("role").asText());
                break;
            }
        }

        // Remove member
        HttpResponse<String> removeResp = delete("/api/servers/" + serverId + "/members/" + userId, adminToken);
        assertEquals(204, removeResp.statusCode());
    }
}
