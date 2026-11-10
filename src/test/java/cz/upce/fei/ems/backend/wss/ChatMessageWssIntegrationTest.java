package cz.upce.fei.ems.backend.wss;

import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import cz.upce.fei.ems.backend.queue.QueuedPacket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ChatMessageWssIntegrationTest extends BaseWssIntegrationTest {

    @Autowired
    private PostgresUserQueue userQueue;

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
        key = channelKeyRepository.save(key);

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

        byte[] expectedFrame = encodeFrame(new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), packetPayload));
        assertTrue(awaitQueuedFrame(userId2, expectedFrame), "The first channel member should have a queued frame");
        assertTrue(awaitQueuedFrame(userId3, expectedFrame), "The second channel member should have a queued frame");
        assertFalse(hasQueuedFrame(userId1, expectedFrame),
                "The sender must not receive a queued copy");
        assertFalse(hasQueuedFrame(userId4, expectedFrame),
                "A member of another channel must not receive the message");
        assertNull(client2.awaitPacket(200, TimeUnit.MILLISECONDS), "Message should be queued, not broadcast directly");
        assertNull(client3.awaitPacket(200, TimeUnit.MILLISECONDS), "Message should be queued, not broadcast directly");
        assertArrayEquals(content, messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channel.getId())
                .get(0).getCiphertext());

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

    private boolean awaitQueuedFrame(long userId, byte[] expectedFrame) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            List<QueuedPacket> packets = userQueue.claimForUser(userId, 10, 100);
            if (packets.stream().anyMatch(packet -> java.util.Arrays.equals(packet.frame(), expectedFrame))) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private boolean hasQueuedFrame(long userId, byte[] expectedFrame) {
        return userQueue.claimForUser(userId, 10, 100).stream()
                .anyMatch(packet -> java.util.Arrays.equals(packet.frame(), expectedFrame));
    }

    private byte[] encodeFrame(Packet packet) {
        ByteBuffer encoded = packetCodec.encode(packet);
        byte[] frame = new byte[encoded.remaining()];
        encoded.get(frame);
        return frame;
    }
}
