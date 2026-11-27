package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.domain.MessageServerType;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MessageRestApiIntegrationTest extends BaseRestApiIntegrationTest {

    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private ChannelKeyRepository channelKeyRepository;

    @Test
    void shouldGetRecentMessages() throws Exception {
        String token = loginAndGetToken("msg_user", "Password123!");
        Long userId = userRepository.findByUsername("msg_user").orElseThrow().getId();

        // Setup server, channel and key
        CreateServerRequestDto serverReq = new CreateServerRequestDto();
        serverReq.setName("Msg Server");
        serverReq.setType(MessageServerType.ORGANIZATION);
        HttpResponse<String> serverResp = post("/api/servers", token, serverReq);
        Long serverId = objectMapper.readTree(serverResp.body()).get("id").asLong();

        CreateChannelRequestDto channelReq = new CreateChannelRequestDto();
        channelReq.setName("Msg Channel");
        HttpResponse<String> channelResp = post("/api/servers/" + serverId + "/channels", token, channelReq);
        Long channelId = objectMapper.readTree(channelResp.body()).get("id").asLong();
        
        // This test exercises message history, so create its key fixture directly.
        ChannelKey key = new ChannelKey();
        key.setChannelId(channelId);
        key.setVersion(UUID.randomUUID());
        key = channelKeyRepository.saveAndFlush(key);

        // Insert message
        Message msg = new Message();
        msg.setChannelId(channelId);
        msg.setSenderId(userId);
        msg.setCiphertext("test-ciphertext".getBytes());
        msg.setChannelKeyId(key.getId());
        messageRepository.save(msg);

        HttpResponse<String> response = get("/api/channels/" + channelId + "/messages/recent", token);
        assertEquals(200, response.statusCode());
        
        JsonNode node = objectMapper.readTree(response.body());
        assertTrue(node.isArray());
        assertEquals(1, node.size());
        assertEquals(key.getVersion().toString(), node.get(0).get("keyVersion").asText());

        Message secondMessage = new Message();
        secondMessage.setChannelId(channelId);
        secondMessage.setSenderId(userId);
        secondMessage.setCiphertext("second-ciphertext".getBytes());
        secondMessage.setChannelKeyId(key.getId());
        messageRepository.save(secondMessage);

        HttpResponse<String> pageResponse = get(
                "/api/channels/" + channelId + "/messages?page=0&size=1&sort=id,asc", token);
        assertEquals(200, pageResponse.statusCode());
        JsonNode pageNode = objectMapper.readTree(pageResponse.body());
        assertTrue(pageNode.get("content").isArray());
        assertEquals(1, pageNode.get("content").size());
        assertEquals(secondMessage.getId(), pageNode.get("content").get(0).get("id").asLong());
        assertFalse(pageNode.get("last").asBoolean());
    }

    @Test
    void shouldRejectInvalidMessagePaginationParameters() throws Exception {
        String token = loginAndGetToken("msg_pagination_user", "Password123!");
        String endpoint = "/api/channels/1/messages";

        assertEquals(400, get(endpoint + "?page=invalid", token).statusCode());
        assertEquals(400, get(endpoint + "?page=-1", token).statusCode());
        assertEquals(400, get(endpoint + "?size=0", token).statusCode());
        assertEquals(400, get(endpoint + "?size=101", token).statusCode());
    }
}
