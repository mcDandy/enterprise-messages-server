package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.domain.MessageServerType;
import cz.upce.fei.ems.backend.dto.AddChannelMemberRequestDto;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class ChannelMembershipRestApiIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void shouldAddAndRemoveMember() throws Exception {
        String adminToken = loginAndGetToken("membership_admin", "Password123!");
        loginAndGetToken("membership_user", "Password123!");
        Long userId = userRepository.findByUsername("membership_user").orElseThrow().getId();

        // Create server and channel
        CreateServerRequestDto serverReq = new CreateServerRequestDto();
        serverReq.setName("Membership Server");
        serverReq.setType(MessageServerType.ORGANIZATION);
        HttpResponse<String> serverResp = post("/api/servers", adminToken, serverReq);
        Long serverId = objectMapper.readTree(serverResp.body()).get("id").asLong();

        CreateChannelRequestDto channelReq = new CreateChannelRequestDto();
        channelReq.setName("Membership Channel");
        HttpResponse<String> channelResp = post("/api/servers/" + serverId + "/channels", adminToken, channelReq);
        Long channelId = objectMapper.readTree(channelResp.body()).get("id").asLong();

        // Add member
        AddChannelMemberRequestDto addReq = new AddChannelMemberRequestDto();
        addReq.setUserId(userId);
        HttpResponse<String> addResp = post("/api/channels/" + channelId + "/members", adminToken, addReq);
        assertEquals(200, addResp.statusCode());

        // Check members
        HttpResponse<String> getResp = get("/api/channels/" + channelId + "/members", adminToken);
        JsonNode node = objectMapper.readTree(getResp.body());
        assertTrue(node.isArray());
        boolean found = false;
        for (JsonNode member : node) {
            if (member.get("userId").asLong() == userId) {
                found = true;
                break;
            }
        }
        assertTrue(found);

        // Remove member
        HttpResponse<String> removeResp = delete("/api/channels/" + channelId + "/members/" + userId, adminToken);
        assertEquals(204, removeResp.statusCode());

        // Check members again
        HttpResponse<String> getResp2 = get("/api/channels/" + channelId + "/members", adminToken);
        JsonNode node2 = objectMapper.readTree(getResp2.body());
        found = false;
        for (JsonNode member : node2) {
            if (member.get("userId").asLong() == userId) {
                found = true;
                break;
            }
        }
        assertFalse(found);
    }
}
