package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.domain.MessageServerType;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.dto.ChannelKeyEnvelopeDto;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ChannelMembershipRestApiIntegrationTest extends BaseRestApiIntegrationTest {
    @Autowired
    private ChannelKeyRepository channelKeyRepository;
    @Autowired
    private PostgresUserQueue userQueue;

    @Test
    void shouldAddAndRemoveMember() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String adminUsername = "membership_admin_" + suffix;
        String memberUsername = "membership_user_" + suffix;
        String adminToken = loginAndGetToken(adminUsername, "Password123!");
        loginAndGetToken(memberUsername, "Password123!");
        Long adminId = userRepository.findByUsername(adminUsername).orElseThrow().getId();
        Long userId = userRepository.findByUsername(memberUsername).orElseThrow().getId();

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
        List<ChannelKeyEnvelopeDto> addEnvelopes = List.of(
                envelope(adminId, "AQ=="), envelope(userId, "Ag=="));
        HttpResponse<String> addResp = post("/api/channels/" + channelId + "/members", adminToken, addEnvelopes);
        assertEquals(200, addResp.statusCode());
        ChannelKey activeKey = channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId).orElseThrow();
        assertEquals(1, userQueue.peekFirstForUser(adminId).size());
        assertEquals(1, userQueue.peekFirstForUser(userId).size());

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
        List<ChannelKeyEnvelopeDto> remainingEnvelopes = List.of(envelope(adminId, "AQ=="));
        HttpResponse<String> removeResp = post(
                "/api/channels/" + channelId + "/members/" + userId + "/remove", adminToken, remainingEnvelopes);
        assertNoContent(removeResp);
        assertNoContent(post(
                "/api/channels/" + channelId + "/members/" + userId + "/remove", adminToken, remainingEnvelopes));
        assertEquals(activeKey.getId(),
                channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId).orElseThrow().getId(),
                "Removing a member so only one remains does not rotate the key");

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

    @Test
    void removingMissingChannelMemberReturnsNoContent() throws Exception {
        String adminUsername = "m_missing_" + UUID.randomUUID().toString().replace("-", "");
        String adminToken = loginAndGetToken(adminUsername, "Password123!");
        Long adminId = userRepository.findByUsername(adminUsername).orElseThrow().getId();
        CreateServerRequestDto serverRequest = new CreateServerRequestDto();
        serverRequest.setName("Membership Missing Server");
        serverRequest.setType(MessageServerType.ORGANIZATION);
        HttpResponse<String> serverResponse = post("/api/servers", adminToken, serverRequest);
        Long serverId = objectMapper.readTree(serverResponse.body()).get("id").asLong();

        CreateChannelRequestDto channelRequest = new CreateChannelRequestDto();
        channelRequest.setName("Membership Missing Channel");
        HttpResponse<String> channelResponse = post(
                "/api/servers/" + serverId + "/channels", adminToken, channelRequest);
        Long channelId = objectMapper.readTree(channelResponse.body()).get("id").asLong();

        HttpResponse<String> response = post(
                "/api/channels/" + channelId + "/members/999999/remove", adminToken,
                List.of(envelope(adminId, "AQ==")));

        assertNoContent(response);
    }

    @Test
    void removingMemberFromMissingChannelReturnsNotFoundProblemJson() throws Exception {
        String adminUsername = "m_missing_channel_" + UUID.randomUUID().toString().replace("-", "");
        String adminToken = loginAndGetToken(adminUsername, "Password123!");

        HttpResponse<String> response = post(
                "/api/channels/999999/members/999999/remove", adminToken, List.of());

        assertEquals(404, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        assertEquals(404, objectMapper.readTree(response.body()).get("status").asInt());
    }

    @Test
    void rejectsRemovalEnvelopeIdsThatHaveTheRightCountButWrongMembers() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String adminUsername = "ma_" + suffix;
        String peerUsername = "mp_" + suffix;
        String thirdUsername = "mt_" + suffix;
        String adminToken = loginAndGetToken(adminUsername, "Password123!");
        loginAndGetToken(peerUsername, "Password123!");
        loginAndGetToken(thirdUsername, "Password123!");
        long adminId = userRepository.findByUsername(adminUsername).orElseThrow().getId();
        long peerId = userRepository.findByUsername(peerUsername).orElseThrow().getId();
        long thirdId = userRepository.findByUsername(thirdUsername).orElseThrow().getId();

        CreateServerRequestDto server = new CreateServerRequestDto();
        server.setName("Membership ID Server " + suffix);
        server.setType(MessageServerType.ORGANIZATION);
        long serverId = objectMapper.readTree(post("/api/servers", adminToken, server).body()).get("id").asLong();
        CreateChannelRequestDto channel = new CreateChannelRequestDto();
        channel.setName("Membership ID Channel " + suffix);
        long channelId = objectMapper.readTree(
                post("/api/servers/" + serverId + "/channels", adminToken, channel).body()).get("id").asLong();

        List<ChannelKeyEnvelopeDto> firstAdd = List.of(envelope(adminId, "AQ=="), envelope(peerId, "Ag=="));
        assertEquals(200, post("/api/channels/" + channelId + "/members", adminToken, firstAdd).statusCode());
        List<ChannelKeyEnvelopeDto> secondAdd = List.of(
                envelope(adminId, "AQ=="), envelope(peerId, "Ag=="), envelope(thirdId, "Aw=="));
        assertEquals(200, post("/api/channels/" + channelId + "/members", adminToken, secondAdd).statusCode());

        // The count is two, but the second ID must be the remaining third member, not an outsider.
        HttpResponse<String> response = post("/api/channels/" + channelId + "/members/" + peerId + "/remove",
                adminToken, List.of(envelope(adminId, "AQ=="), envelope(999999L, "BA==")));

        assertEquals(400, response.statusCode(), response.body());
        JsonNode members = objectMapper.readTree(get("/api/channels/" + channelId + "/members", adminToken).body());
        boolean peerRemains = false;
        for (JsonNode member : members) {
            if (member.get("userId").asLong() == peerId) peerRemains = true;
        }
        assertTrue(peerRemains);
    }

    private static ChannelKeyEnvelopeDto envelope(Long userId, String ciphertext) {
        ChannelKeyEnvelopeDto envelope = new ChannelKeyEnvelopeDto();
        envelope.setUserId(userId);
        envelope.setCiphertext(ciphertext);
        return envelope;
    }

}
