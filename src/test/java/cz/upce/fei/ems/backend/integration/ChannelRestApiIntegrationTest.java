package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.domain.MessageServerType;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.domain.MessageReceipt;
import cz.upce.fei.ems.backend.domain.MessageReceiptId;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import cz.upce.fei.ems.backend.dto.UpdateChannelRequestDto;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.MessageReceiptRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class ChannelRestApiIntegrationTest extends BaseRestApiIntegrationTest {

    @Autowired
    private ChannelKeyRepository channelKeyRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private MessageReceiptRepository messageReceiptRepository;

    @Test
    void shouldCreateAndGetChannel() throws Exception {
        String token = loginAndGetToken("channel_admin", "Password123!");

        // Create server first
        CreateServerRequestDto serverReq = new CreateServerRequestDto();
        serverReq.setName("Server for Channel");
        serverReq.setType(MessageServerType.ORGANIZATION);
        HttpResponse<String> serverResp = post("/api/servers", token, serverReq);
        Long serverId = objectMapper.readTree(serverResp.body()).get("id").asLong();

        CreateChannelRequestDto channelReq = new CreateChannelRequestDto();
        channelReq.setName("General Channel");

        HttpResponse<String> createResponse = post("/api/servers/" + serverId + "/channels", token, channelReq);
        assertEquals(201, createResponse.statusCode());
        
        JsonNode channelNode = objectMapper.readTree(createResponse.body());
        Long channelId = channelNode.get("id").asLong();
        assertEquals("General Channel", channelNode.get("name").asText());

        HttpResponse<String> getResponse = get("/api/channels/" + channelId, token);
        assertEquals(200, getResponse.statusCode());
    }

    @Test
    void shouldUpdateChannel() throws Exception {
        String token = loginAndGetToken("channel_update", "Password123!");
        
        CreateServerRequestDto serverReq = new CreateServerRequestDto();
        serverReq.setName("Server for Update");
        serverReq.setType(MessageServerType.ORGANIZATION);
        HttpResponse<String> serverResp = post("/api/servers", token, serverReq);
        Long serverId = objectMapper.readTree(serverResp.body()).get("id").asLong();

        CreateChannelRequestDto channelReq = new CreateChannelRequestDto();
        channelReq.setName("Old Channel");
        HttpResponse<String> createResponse = post("/api/servers/" + serverId + "/channels", token, channelReq);
        Long channelId = objectMapper.readTree(createResponse.body()).get("id").asLong();

        UpdateChannelRequestDto updateReq = new UpdateChannelRequestDto();
        updateReq.setName("New Channel Name");

        HttpResponse<String> updateResponse = put("/api/channels/" + channelId, token, updateReq);
        assertEquals(200, updateResponse.statusCode());

        HttpResponse<String> getResponse = get("/api/channels/" + channelId, token);
        JsonNode node = objectMapper.readTree(getResponse.body());
        assertEquals("New Channel Name", node.get("name").asText());
    }

    @Test
    void shouldDeleteChannelAndDependentMessages() throws Exception {
        String token = loginAndGetToken("channel_delete", "Password123!");
        
        CreateServerRequestDto serverReq = new CreateServerRequestDto();
        serverReq.setName("Server for Delete");
        serverReq.setType(MessageServerType.ORGANIZATION);
        HttpResponse<String> serverResp = post("/api/servers", token, serverReq);
        Long serverId = objectMapper.readTree(serverResp.body()).get("id").asLong();

        CreateChannelRequestDto channelReq = new CreateChannelRequestDto();
        channelReq.setName("To Delete");
        HttpResponse<String> createResponse = post("/api/servers/" + serverId + "/channels", token, channelReq);
        Long channelId = objectMapper.readTree(createResponse.body()).get("id").asLong();

        Long userId = userRepository.findByUsername("channel_delete").orElseThrow().getId();
        // Channel creation already schedules rotation and creates an active key version.
        ChannelKey channelKey = channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId)
                .orElseThrow();

        Message message = new Message();
        message.setChannelId(channelId);
        message.setSenderId(userId);
        message.setChannelKeyId(channelKey.getId());
        message.setCiphertext("ciphertext".getBytes(StandardCharsets.UTF_8));
        message = messageRepository.save(message);

        MessageReceipt receipt = new MessageReceipt();
        receipt.setMessageChannelId(channelId);
        receipt.setUserId(userId);
        receipt.setLastDeliveredMessageId(message.getId());
        receipt.setLastReadMessageId(message.getId());
        messageReceiptRepository.save(receipt);

        HttpResponse<String> deleteResponse = delete("/api/channels/" + channelId, token);
        assertEquals(204, deleteResponse.statusCode());
        assertEquals(204, delete("/api/channels/" + channelId, token).statusCode());

        HttpResponse<String> getResponse = get("/api/channels/" + channelId, token);
        assertEquals(404, getResponse.statusCode());
        assertTrue(messageRepository.findByChannelId(channelId, PageRequest.of(0, 10)).isEmpty());
        assertTrue(channelKeyRepository.findByChannelIdAndVersion(channelId, channelKey.getVersion()).isEmpty());
        assertTrue(messageReceiptRepository.findById(new MessageReceiptId(channelId, userId)).isEmpty());
    }

    @Test
    void deletingMissingChannelReturnsNoContent() throws Exception {
        String token = loginAndGetToken("channel_delete_missing", "Password123!");

        HttpResponse<String> response = delete("/api/channels/999999", token);

        assertEquals(204, response.statusCode());
    }
}
