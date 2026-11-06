package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.domain.MessageServerType;
import cz.upce.fei.ems.backend.dto.AddChannelMemberRequestDto;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class ChannelMemberPublicKeysRestApiIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void shouldGetChannelMemberPublicKeys() throws Exception {
        String adminToken = loginAndGetToken("key_admin", "Password123!");
        loginAndGetToken("key_peer", "Password123!");
        
        // Create server and channel
        CreateServerRequestDto serverReq = new CreateServerRequestDto();
        serverReq.setName("Key Server");
        serverReq.setType(MessageServerType.ORGANIZATION);
        HttpResponse<String> serverResp = post("/api/servers", adminToken, serverReq);
        Long serverId = objectMapper.readTree(serverResp.body()).get("id").asLong();

        CreateChannelRequestDto channelReq = new CreateChannelRequestDto();
        channelReq.setName("Key Channel");
        HttpResponse<String> channelResp = post("/api/servers/" + serverId + "/channels", adminToken, channelReq);
        Long channelId = objectMapper.readTree(channelResp.body()).get("id").asLong();

        // Add peer to channel
        Long peerId = userRepository.findByUsername("key_peer").orElseThrow().getId();
        AddChannelMemberRequestDto addReq = new AddChannelMemberRequestDto();
        addReq.setUserId(peerId);
        post("/api/channels/" + channelId + "/members", adminToken, addReq);

        // Get keys as admin (should see peer's key)
        HttpResponse<String> keysResp = get("/api/channels/" + channelId + "/keys", adminToken);
        assertEquals(200, keysResp.statusCode());
        
        JsonNode node = objectMapper.readTree(keysResp.body());
        assertTrue(node.isArray());
        boolean foundPeer = false;
        for (JsonNode key : node) {
            if (key.get("userId").asLong() == peerId) {
                foundPeer = true;
                assertEquals("initial-key", key.get("publicKey").asText());
            }
        }
        assertTrue(foundPeer);
    }
}
