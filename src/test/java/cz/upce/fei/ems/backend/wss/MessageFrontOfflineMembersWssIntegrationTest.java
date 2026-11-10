package cz.upce.fei.ems.backend.wss;

import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MessageFrontOfflineMembersWssIntegrationTest extends BaseWssIntegrationTest {
    private static final String PASSWORD = "Password123!";

    @Autowired
    private PostgresUserQueue userQueue;

    @Test
    void preservesFifoForOfflineMembersAcrossChatRotationAndUnicastTraffic() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String[] usernames = {
                "front_a_" + suffix,
                "front_b_" + suffix,
                "front_c_" + suffix,
                "front_d_" + suffix
        };
        String[] tokens = new String[usernames.length];
        long[] userIds = new long[usernames.length];
        List<TestWebSocketClient> clientsToClose = new ArrayList<>();

        for (int i = 0; i < usernames.length; i++) {
            tokens[i] = loginAndGetToken(usernames[i], PASSWORD);
            userIds[i] = userRepository.findByUsername(usernames[i]).orElseThrow().getId();
        }

        Channel channel = new Channel();
        channel.setName("Offline message front " + suffix);
        channel.setServerId(1L);
        channel = channelRepository.save(channel);
        long channelId = channel.getId();
        addMember(channelId, userIds[0]);
        addMember(channelId, userIds[1]);
        addMember(channelId, userIds[2]);

        ChannelKey initialKey = new ChannelKey();
        initialKey.setChannelId(channelId);
        initialKey.setVersion(UUID.randomUUID());
        initialKey = channelKeyRepository.save(initialKey);

        TestWebSocketClient clientA = connect(tokens[0], usernames[0], channelId, initialKey.getId());
        TestWebSocketClient clientB = connect(tokens[1], usernames[1], channelId, initialKey.getId());
        TestWebSocketClient clientC = connect(tokens[2], usernames[2], channelId, initialKey.getId());
        clientsToClose.addAll(List.of(clientA, clientB, clientC));

        int expectedMessageCount = 0;
        Packet aToOthers = chatPacket(channelId, "A: first");
        clientA.sendPacket(aToOthers);
        expectedMessageCount = awaitMessageCount(channelId, ++expectedMessageCount);
        receiveAndAcknowledgeSingle(clientB, userIds[1], aToOthers);
        receiveAndAcknowledgeSingle(clientC, userIds[2], aToOthers);

        Packet bToOthers = chatPacket(channelId, "B: first");
        clientB.sendPacket(bToOthers);
        expectedMessageCount = awaitMessageCount(channelId, ++expectedMessageCount);
        receiveAndAcknowledgeSingle(clientA, userIds[0], bToOthers);
        receiveAndAcknowledgeSingle(clientC, userIds[2], bToOthers);

        Packet cToOthers = chatPacket(channelId, "C: first");
        clientC.sendPacket(cToOthers);
        expectedMessageCount = awaitMessageCount(channelId, ++expectedMessageCount);
        receiveAndAcknowledgeSingle(clientA, userIds[0], cToOthers);
        receiveAndAcknowledgeSingle(clientB, userIds[1], cToOthers);

        disconnect(clientB);
        disconnect(clientC);

        Packet beforeFourth1 = chatPacket(channelId, "A: while B and C are offline 1");
        clientA.sendPacket(beforeFourth1);
        expectedMessageCount = awaitMessageCount(channelId, ++expectedMessageCount);
        Packet beforeFourth2 = chatPacket(channelId, "A: while B and C are offline 2");
        clientA.sendPacket(beforeFourth2);
        expectedMessageCount = awaitMessageCount(channelId, ++expectedMessageCount);

        addMember(channelId, userIds[3]);
        TestWebSocketClient clientD = connect(tokens[3], usernames[3], channelId, initialKey.getId());
        clientsToClose.add(clientD);

        Packet afterFourth = chatPacket(channelId, "A: after the fourth member joined");
        clientA.sendPacket(afterFourth);
        expectedMessageCount = awaitMessageCount(channelId, ++expectedMessageCount);
        receiveAndAcknowledgeSingle(clientD, userIds[3], afterFourth);

        clientA.sendPacket(new Packet(PacketType.ROTATE_KEY.getCode(), new byte[0]));
        Packet solicitation = clientD.awaitPacket(5, TimeUnit.SECONDS);
        assertNotNull(solicitation, "The newly added online member should receive the rotation solicitation");
        assertEquals(PacketType.ROTATE_KEY.getCode(), solicitation.getType());
        ByteBuffer rotationPayload = ByteBuffer.wrap(solicitation.getPayload());
        assertEquals(3 * Long.BYTES, rotationPayload.remaining());
        rotationPayload.getLong();
        rotationPayload.getLong();
        assertEquals(channelId, rotationPayload.getLong());
        acknowledgeAndAwaitEmpty(clientD, userIds[3]);

        ChannelKey rotatedKey = channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId)
                .orElseThrow();
        assertNotEquals(initialKey.getId(), rotatedKey.getId());

        Packet unicastToOfflineC = unicastPacket(userIds[2], channelId, new byte[]{11, 12, 13});
        clientA.sendPacket(unicastToOfflineC);
        Packet unicastToOnlineD = unicastPacket(userIds[3], channelId, new byte[]{21, 22});
        clientA.sendPacket(unicastToOnlineD);
        Packet receivedUnicast = clientD.awaitPacket(5, TimeUnit.SECONDS);
        assertNotNull(receivedUnicast, "The online recipient should receive its unicast rotation");
        assertUnicastPayload(receivedUnicast, userIds[0], rotatedKey.getVersion(), new byte[]{21, 22});
        acknowledgeAndAwaitEmpty(clientD, userIds[3]);

        Packet afterRotation = chatPacket(channelId, "A: after rotation");
        clientA.sendPacket(afterRotation);
        expectedMessageCount = awaitMessageCount(channelId, ++expectedMessageCount);
        receiveAndAcknowledgeSingle(clientD, userIds[3], afterRotation);

        assertQueueHead(userIds[1], beforeFourth1);
        assertQueueHead(userIds[2], beforeFourth1);
        assertQueueHead(userIds[3], null);

        clientB = connect(tokens[1], usernames[1], channelId, initialKey.getId());
        clientsToClose.add(clientB);
        receiveAndAcknowledge(clientB, userIds[1], beforeFourth1);
        receiveAndAcknowledge(clientB, userIds[1], beforeFourth2);
        receiveAndAcknowledge(clientB, userIds[1], afterFourth);
        receiveAndAcknowledge(clientB, userIds[1], afterRotation);
        assertQueueEmpty(userIds[1]);

        clientC = connect(tokens[2], usernames[2], channelId, initialKey.getId());
        clientsToClose.add(clientC);
        receiveAndAcknowledge(clientC, userIds[2], beforeFourth1);
        receiveAndAcknowledge(clientC, userIds[2], beforeFourth2);
        receiveAndAcknowledge(clientC, userIds[2], afterFourth);
        Packet receivedOfflineUnicast = clientC.awaitPacket(5, TimeUnit.SECONDS);
        assertNotNull(receivedOfflineUnicast, "Offline unicast rotation should be delivered after reconnect");
        assertUnicastPayload(receivedOfflineUnicast, userIds[0], rotatedKey.getVersion(), new byte[]{11, 12, 13});
        acknowledge(clientC, userIds[2]);
        receiveAndAcknowledge(clientC, userIds[2], afterRotation);
        assertQueueEmpty(userIds[2]);
        assertQueueEmpty(userIds[3]);

        assertEquals(expectedMessageCount,
                messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channelId).size());
    }

    private TestWebSocketClient connect(String token, String username, long channelId, long channelKeyId)
            throws Exception {
        TestWebSocketClient client = createConnectedClient(token);
        setSessionAttributes(username, channelId, channelKeyId);
        return client;
    }

    private void addMember(long channelId, long userId) {
        ChannelMember member = new ChannelMember();
        member.setChannelId(channelId);
        member.setUserId(userId);
        channelMemberRepository.save(member);
    }

    private Packet chatPacket(long channelId, String message) {
        byte[] ciphertext = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer payload = ByteBuffer.allocate(Integer.BYTES + ciphertext.length);
        payload.putInt((int) channelId);
        payload.put(ciphertext);
        return new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), payload.array());
    }

    private Packet unicastPacket(long recipientId, long channelId, byte[] ciphertext) {
        ByteBuffer payload = ByteBuffer.allocate(2 * Long.BYTES + ciphertext.length);
        payload.putLong(recipientId);
        payload.putLong(channelId);
        payload.put(ciphertext);
        return new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(), payload.array());
    }

    private void receiveAndAcknowledge(TestWebSocketClient client, long userId, Packet expected) throws Exception {
        Packet received = client.awaitPacket(5, TimeUnit.SECONDS);
        assertNotNull(received, "Expected queued packet type " + PacketType.fromCode(expected.getType()));
        assertPacketEquals(expected, received);
        acknowledge(client, userId);
    }

    private void receiveAndAcknowledgeSingle(TestWebSocketClient client, long userId, Packet expected)
            throws Exception {
        receiveAndAcknowledge(client, userId, expected);
        assertQueueEmpty(userId);
    }

    private void acknowledge(TestWebSocketClient client, long userId) throws Exception {
        client.sendPacket(new Packet(PacketType.ACKNOWLEDGE.getCode(), new byte[0]));
    }

    private void acknowledgeAndAwaitEmpty(TestWebSocketClient client, long userId) throws Exception {
        acknowledge(client, userId);
        assertQueueEmpty(userId);
    }

    private void assertUnicastPayload(
            Packet packet, long expectedSenderId, UUID expectedKeyVersion, byte[] expectedCiphertext
    ) {
        assertEquals(PacketType.UNICAST_KEY_ROTATION.getCode(), packet.getType());
        ByteBuffer payload = ByteBuffer.wrap(packet.getPayload());
        assertTrue(payload.remaining() >= 3 * Long.BYTES);
        assertEquals(expectedSenderId, payload.getLong());
        assertEquals(expectedKeyVersion.getMostSignificantBits(), payload.getLong());
        assertEquals(expectedKeyVersion.getLeastSignificantBits(), payload.getLong());
        byte[] ciphertext = new byte[payload.remaining()];
        payload.get(ciphertext);
        assertArrayEquals(expectedCiphertext, ciphertext);
    }

    private void assertQueueHead(long userId, Packet expected) {
        var queue = userQueue.peekFirstForUser(userId);
        if (expected == null) {
            assertTrue(queue.isEmpty());
            return;
        }
        assertFalse(queue.isEmpty());
        assertArrayEquals(encodeFrame(expected), queue.getFirst().frame());
    }

    private void assertQueueEmpty(long userId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (userQueue.peekFirstForUser(userId).isEmpty()) return;
            Thread.sleep(25);
        }
        assertTrue(userQueue.peekFirstForUser(userId).isEmpty(), "All ACKed frames should be removed");
    }

    private int awaitMessageCount(long channelId, int expectedCount) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channelId).size() == expectedCount) {
                return expectedCount;
            }
            Thread.sleep(25);
        }
        assertEquals(expectedCount,
                messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channelId).size());
        return expectedCount;
    }

    private void assertPacketEquals(Packet expected, Packet actual) {
        assertEquals(expected.getType(), actual.getType());
        assertArrayEquals(expected.getPayload(), actual.getPayload());
    }

    private byte[] encodeFrame(Packet packet) {
        ByteBuffer encoded = packetCodec.encode(packet);
        byte[] frame = new byte[encoded.remaining()];
        encoded.get(frame);
        return frame;
    }

    private void disconnect(TestWebSocketClient client) throws Exception {
        client.getSession().close();
    }
}
