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
    void preservesFifoForOfflineMembersAcrossChatTraffic() throws Exception {
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

        assertQueueHead(userIds[1], beforeFourth1);
        assertQueueHead(userIds[2], beforeFourth1);
        assertQueueHead(userIds[3], null);

        clientB = connect(tokens[1], usernames[1], channelId, initialKey.getId());
        clientsToClose.add(clientB);
        receiveAndAcknowledge(clientB, userIds[1], beforeFourth1);
        receiveAndAcknowledge(clientB, userIds[1], beforeFourth2);
        receiveAndAcknowledge(clientB, userIds[1], afterFourth);
        assertQueueEmpty(userIds[1]);

        clientC = connect(tokens[2], usernames[2], channelId, initialKey.getId());
        clientsToClose.add(clientC);
        receiveAndAcknowledge(clientC, userIds[2], beforeFourth1);
        receiveAndAcknowledge(clientC, userIds[2], beforeFourth2);
        receiveAndAcknowledge(clientC, userIds[2], afterFourth);
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

    private void receiveAndAcknowledge(TestWebSocketClient client, long userId, Packet expected) throws Exception {
        Packet received = awaitQueuedPacket(client);
        assertNotNull(received, "Expected queued packet type " + PacketType.fromCode(expected.getType()));
        assertPacketEquals(expected, received);
        acknowledge(client, userId);
    }

    private Packet awaitQueuedPacket(TestWebSocketClient client) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            long remaining = deadline - System.nanoTime();
            Packet packet = client.awaitPacket(remaining, TimeUnit.NANOSECONDS);
            if (packet == null) return null;
            if (packet.getType() == PacketType.CHANNEL_MEMBER_INFO.getCode()) {
                assertEquals(4 * Long.BYTES, packet.getPayload().length,
                        "Channel member info should contain four long values");
                continue;
            }
            return packet;
        }
        return null;
    }

    private void receiveAndAcknowledgeSingle(TestWebSocketClient client, long userId, Packet expected)
            throws Exception {
        receiveAndAcknowledge(client, userId, expected);
        assertQueueEmpty(userId);
    }

    private void acknowledge(TestWebSocketClient client, long userId) throws Exception {
        client.sendPacket(new Packet(PacketType.ACKNOWLEDGE.getCode(), new byte[0]));
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
