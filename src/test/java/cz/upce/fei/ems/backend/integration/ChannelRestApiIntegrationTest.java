package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.domain.MessageServerType;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import cz.upce.fei.ems.backend.dto.UpdateChannelRequestDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class ChannelRestApiIntegrationTest extends BaseRestApiIntegrationTest {

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
    void shouldDeleteChannel() throws Exception {
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

        HttpResponse<String> deleteResponse = delete("/api/channels/" + channelId, token);
        assertEquals(204, deleteResponse.statusCode());

        HttpResponse<String> getResponse = get("/api/channels/" + channelId, token);
        assertEquals(404, getResponse.statusCode());
    }
}
