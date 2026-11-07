package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.domain.MessageServerType;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import cz.upce.fei.ems.backend.dto.UpdateServerRequestDto;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class MessageServerRestApiIntegrationTest extends BaseRestApiIntegrationTest {

    @Autowired
    private ChannelRepository channelRepository;

    @Test
    void shouldCreateAndGetServer() throws Exception {
        String token = loginAndGetToken("server_admin", "Password123!");

        CreateServerRequestDto createRequest = new CreateServerRequestDto();
        createRequest.setName("Integration Test Server");
        createRequest.setType(MessageServerType.ORGANIZATION);

        HttpResponse<String> createResponse = post("/api/servers", token, createRequest);
        assertEquals(201, createResponse.statusCode());
        
        JsonNode serverNode = objectMapper.readTree(createResponse.body());
        Long serverId = serverNode.get("id").asLong();
        assertEquals("Integration Test Server", serverNode.get("name").asText());

        HttpResponse<String> getResponse = get("/api/servers/" + serverId, token);
        assertEquals(200, getResponse.statusCode());
        JsonNode getNode = objectMapper.readTree(getResponse.body());
        assertEquals("Integration Test Server", getNode.get("name").asText());
    }

    @Test
    void shouldUpdateServer() throws Exception {
        String token = loginAndGetToken("server_update", "Password123!");

        CreateServerRequestDto createRequest = new CreateServerRequestDto();
        createRequest.setName("Old Server Name");
        createRequest.setType(MessageServerType.ORGANIZATION);
        HttpResponse<String> createResponse = post("/api/servers", token, createRequest);
        Long serverId = objectMapper.readTree(createResponse.body()).get("id").asLong();

        UpdateServerRequestDto updateRequest = new UpdateServerRequestDto();
        updateRequest.setName("Updated Server Name");

        HttpResponse<String> updateResponse = put("/api/servers/" + serverId, token, updateRequest);
        assertEquals(200, updateResponse.statusCode());

        HttpResponse<String> getResponse = get("/api/servers/" + serverId, token);
        JsonNode getNode = objectMapper.readTree(getResponse.body());
        assertEquals("Updated Server Name", getNode.get("name").asText());
    }

    @Test
    void shouldDeleteServer() throws Exception {
        String token = loginAndGetToken("server_delete", "Password123!");

        CreateServerRequestDto createRequest = new CreateServerRequestDto();
        createRequest.setName("Server To Delete");
        createRequest.setType(MessageServerType.ORGANIZATION);
        HttpResponse<String> createResponse = post("/api/servers", token, createRequest);
        Long serverId = objectMapper.readTree(createResponse.body()).get("id").asLong();

        CreateChannelRequestDto channelRequest = new CreateChannelRequestDto();
        channelRequest.setName("Channel To Delete With Server");
        HttpResponse<String> channelResponse = post("/api/servers/" + serverId + "/channels", token, channelRequest);
        Long channelId = objectMapper.readTree(channelResponse.body()).get("id").asLong();

        HttpResponse<String> deleteResponse = delete("/api/servers/" + serverId, token);
        assertEquals(204, deleteResponse.statusCode());
        assertEquals(204, delete("/api/servers/" + serverId, token).statusCode());

        HttpResponse<String> getResponse = get("/api/servers/" + serverId, token);
        assertEquals(404, getResponse.statusCode());
        assertFalse(channelRepository.existsById(channelId));
    }

    @Test
    void deletingMissingServerReturnsNoContent() throws Exception {
        String token = loginAndGetToken("server_delete_missing", "Password123!");

        HttpResponse<String> response = delete("/api/servers/999999", token);

        assertEquals(204, response.statusCode());
    }
}
