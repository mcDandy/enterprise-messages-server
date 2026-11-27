package cz.upce.fei.ems.backend.wss;

import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ChatMessageWssIntegrationTest extends BaseWssIntegrationTest {

    @Test
    void twoChannelMembersCanExchangeMessagesAndBothSeeFullHistory() throws Exception {
        String username1 = "chat_history_1_" + UUID.randomUUID().toString().replace("-", "");
        String username2 = "chat_history_2_" + UUID.randomUUID().toString().replace("-", "");
        String token1 = loginAndGetToken(username1, "Password123!");
        String token2 = loginAndGetToken(username2, "Password123!");
        Long userId1 = userRepository.findByUsername(username1).orElseThrow().getId();
        Long userId2 = userRepository.findByUsername(username2).orElseThrow().getId();

        Channel channel = new Channel();
        channel.setName("Shared History " + UUID.randomUUID().toString().replace("-", ""));
        channel.setServerId(1L);
        channel = channelRepository.save(channel);

        for (Long userId : List.of(userId1, userId2)) {
            ChannelMember member = new ChannelMember();
            member.setChannelId(channel.getId());
            member.setUserId(userId);
            channelMemberRepository.save(member);
        }

        ChannelKey key = new ChannelKey();
        key.setChannelId(channel.getId());
        key.setVersion(UUID.randomUUID());
        channelKeyRepository.save(key);

        TestWebSocketClient client1 = createConnectedClient(token1);
        TestWebSocketClient client2 = createConnectedClient(token2);
        try {
            byte[] firstCiphertext = "first encrypted message".getBytes(StandardCharsets.UTF_8);
            byte[] firstPayload = chatPayload(channel.getId(), firstCiphertext);
            client1.sendPacket(new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), firstPayload));

            Packet firstReceived = client2.awaitPacket(5, TimeUnit.SECONDS);
            assertNotNull(firstReceived, "Second channel member should receive the first message");
            assertEquals(PacketType.CHAT_MESSAGE_ENC.getCode(), firstReceived.getType());
            assertArrayEquals(firstPayload, firstReceived.getPayload());

            byte[] secondCiphertext = "second encrypted message".getBytes(StandardCharsets.UTF_8);
            byte[] secondPayload = chatPayload(channel.getId(), secondCiphertext);
            client2.sendPacket(new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), secondPayload));

            Packet secondReceived = client1.awaitPacket(5, TimeUnit.SECONDS);
            assertNotNull(secondReceived, "First channel member should receive the second message");
            assertEquals(PacketType.CHAT_MESSAGE_ENC.getCode(), secondReceived.getType());
            assertArrayEquals(secondPayload, secondReceived.getPayload());

            HttpResponse<String> history1 = get(
                    "/api/channels/" + channel.getId() + "/messages?page=0&size=10", token1);
            HttpResponse<String> history2 = get(
                    "/api/channels/" + channel.getId() + "/messages?page=0&size=10", token2);
            assertEquals(200, history1.statusCode());
            assertEquals(200, history2.statusCode());

            List<Long> messageIds1 = messageIds(history1.body());
            List<Long> messageIds2 = messageIds(history2.body());
            assertEquals(2, messageIds1.size());
            assertEquals(messageIds1, messageIds2, "Both channel members should see the same full history");

            List<Message> persistedMessages =
                    messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channel.getId());
            assertEquals(2, persistedMessages.size());
            assertTrue(messageIds1.containsAll(persistedMessages.stream().map(Message::getId).toList()));
            assertArrayEquals(firstCiphertext, persistedMessages.get(1).getCiphertext());
            assertArrayEquals(secondCiphertext, persistedMessages.get(0).getCiphertext());
        } finally {
            if (client1.getSession().isOpen()) {
                client1.getSession().close();
            }
            if (client2.getSession().isOpen()) {
                client2.getSession().close();
            }
        }
    }

    @Test
    void shouldQueueMessageForOtherChannelMembers() throws Exception {
        // Setup: Three users, one channel, one channel key
        String username1 = "chat_u1_" + UUID.randomUUID().toString().replace("-", "");
        String username2 = "chat_u2_" + UUID.randomUUID().toString().replace("-", "");
        String username3 = "chat_u3_" + UUID.randomUUID().toString().replace("-", "");
        String username4 = "chat_u4_" + UUID.randomUUID().toString().replace("-", "");
        String token1 = loginAndGetToken(username1, "Password123!");
        String token2 = loginAndGetToken(username2, "Password123!");
        String token3 = loginAndGetToken(username3, "Password123!");
        String token4 = loginAndGetToken(username4, "Password123!");

        Long userId1 = userRepository.findByUsername(username1).orElseThrow().getId();
        Long userId2 = userRepository.findByUsername(username2).orElseThrow().getId();
        Long userId3 = userRepository.findByUsername(username3).orElseThrow().getId();
        Long userId4 = userRepository.findByUsername(username4).orElseThrow().getId();

        Channel channel = new Channel();
        channel.setName("Chat Multi-Point Test " + UUID.randomUUID().toString().replace("-", ""));
        channel.setServerId(1L);
        channel = channelRepository.save(channel);

        Channel otherChannel = new Channel();
        otherChannel.setName("Other Chat Test " + UUID.randomUUID().toString().replace("-", ""));
        otherChannel.setServerId(1L);
        otherChannel = channelRepository.save(otherChannel);

        for (Long uid : new Long[]{userId1, userId2, userId3}) {
            ChannelMember m = new ChannelMember();
            m.setChannelId(channel.getId());
            m.setUserId(uid);
            channelMemberRepository.save(m);
        }
        for (Long uid : new Long[]{userId1, userId4}) {
            ChannelMember m = new ChannelMember();
            m.setChannelId(otherChannel.getId());
            m.setUserId(uid);
            channelMemberRepository.save(m);
        }

        ChannelKey key = new ChannelKey();
        key.setChannelId(channel.getId());
        key.setVersion(UUID.randomUUID());
        channelKeyRepository.save(key);

        // Connect all users
        TestWebSocketClient client1 = createConnectedClient(token1);
        TestWebSocketClient client2 = createConnectedClient(token2);
        TestWebSocketClient client3 = createConnectedClient(token3);
        TestWebSocketClient client4 = createConnectedClient(token4);
        assertNull(sessionRegistry.getSession(username1).getAttributes().get("currentChannelId"));

        // Client 1 sends a channel ID followed by the encrypted message
        byte[] content = "Hello points!".getBytes(StandardCharsets.UTF_8);
        ByteBuffer payload = ByteBuffer.allocate(Integer.BYTES + content.length);
        payload.putInt((int) channel.getId().longValue());
        payload.put(content);
        byte[] packetPayload = payload.array();
        client1.sendPacket(new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), packetPayload));

        Packet received2 = client2.awaitPacket(5, TimeUnit.SECONDS);
        Packet received3 = client3.awaitPacket(5, TimeUnit.SECONDS);
        assertNotNull(received2, "The first channel member should receive the queued frame");
        assertNotNull(received3, "The second channel member should receive the queued frame");
        assertEquals(PacketType.CHAT_MESSAGE_ENC.getCode(), received2.getType());
        assertEquals(PacketType.CHAT_MESSAGE_ENC.getCode(), received3.getType());
        assertArrayEquals(packetPayload, received2.getPayload());
        assertArrayEquals(packetPayload, received3.getPayload());
        assertNull(client1.awaitPacket(200, TimeUnit.MILLISECONDS), "Sender must not receive a copy");
        assertNull(client4.awaitPacket(200, TimeUnit.MILLISECONDS), "Unrelated channel member must not receive it");
        assertArrayEquals(content, messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channel.getId())
                .getFirst().getCiphertext());

        client2.sendPacket(new Packet(PacketType.ACKNOWLEDGE.getCode(), new byte[0]));
        client1.getSession().close();
        client2.getSession().close();
        client3.getSession().close();
        client4.getSession().close();
    }

    @Test
    void shouldRejectChatPacketWithoutFullChannelId() throws Exception {
        String username = "chat_short_" + UUID.randomUUID().toString().replace("-", "");
        TestWebSocketClient client = createConnectedClient(loginAndGetToken(username, "Password123!"));

        client.sendPacket(new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), new byte[]{0, 0, 1}));

        Packet error = client.awaitPacket(5, TimeUnit.SECONDS);
        assertNotNull(error);
        assertEquals(PacketType.UNKNOWN.getCode(), error.getType());
        assertTrue(new String(error.getPayload(), StandardCharsets.UTF_8).contains("missing channel ID"));
        client.getSession().close();
    }

    @Test
    void shouldRejectChatPacketForChannelSenderDoesNotBelongTo() throws Exception {
        String username = "chat_nonmember_" + UUID.randomUUID().toString().replace("-", "");
        TestWebSocketClient client = createConnectedClient(loginAndGetToken(username, "Password123!"));
        Channel channel = new Channel();
        channel.setName("Private Chat " + UUID.randomUUID().toString().replace("-", ""));
        channel.setServerId(1L);
        channel = channelRepository.save(channel);

        ByteBuffer payload = ByteBuffer.allocate(Integer.BYTES + 1);
        payload.putInt((int) channel.getId().longValue());
        payload.put((byte) 42);
        client.sendPacket(new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), payload.array()));

        Packet error = client.awaitPacket(5, TimeUnit.SECONDS);
        assertNotNull(error);
        assertEquals(PacketType.UNKNOWN.getCode(), error.getType());
        assertTrue(new String(error.getPayload(), StandardCharsets.UTF_8).contains("not a member"));
        assertTrue(messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channel.getId()).isEmpty());
        client.getSession().close();
    }

    private byte[] chatPayload(Long channelId, byte[] ciphertext) {
        ByteBuffer payload = ByteBuffer.allocate(Integer.BYTES + ciphertext.length);
        payload.putInt(channelId.intValue());
        payload.put(ciphertext);
        return payload.array();
    }

    private List<Long> messageIds(String responseBody) throws Exception {
        JsonNode content = objectMapper.readTree(responseBody).get("content");
        List<Long> ids = new ArrayList<>();
        for (JsonNode message : content) {
            ids.add(message.get("id").asLong());
        }
        return ids;
    }

}
