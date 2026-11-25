package cz.upce.fei.ems.backend.wss;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class QueuedPacketConnectionWssIntegrationTest extends BaseWssIntegrationTest {
    @Autowired
    private PostgresUserQueue userQueue;

    @Test
    void sendsQueuedFramesOneAtATimeWhenUserConnects() throws Exception {
        String username = "queued_connect_" + UUID.randomUUID().toString().replace("-", "");
        String token = loginAndGetToken(username, "Password123!");
        long userId = userRepository.findByUsername(username).orElseThrow().getId();
        Packet first = new Packet(PacketType.ROTATE_KEY.getCode(), new byte[]{1, 2});
        Packet second = new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{3, 4});
        userQueue.enqueueForUser(userId, encodeFrame(first));
        userQueue.enqueueForUser(userId, encodeFrame(second));

        TestWebSocketClient client = createConnectedClient(token);
        try {
            Packet receivedFirst = client.awaitPacket(5, TimeUnit.SECONDS);
            assertNotNull(receivedFirst, "The FIFO head should be sent immediately after connection");
            assertPacketEquals(first, receivedFirst);
            assertArrayEquals(encodeFrame(first), userQueue.peekFirstForUser(userId).getFirst().frame());
            assertNull(client.awaitPacket(200, TimeUnit.MILLISECONDS),
                    "The next queued frame must wait for acknowledgment");

            client.sendPacket(new Packet(PacketType.ACKNOWLEDGE.getCode(), new byte[0]));
            Packet receivedSecond = client.awaitPacket(5, TimeUnit.SECONDS);
            assertNotNull(receivedSecond, "Acknowledgment should trigger the next queued frame");
            assertPacketEquals(second, receivedSecond);
            assertArrayEquals(encodeFrame(second), userQueue.peekFirstForUser(userId).getFirst().frame());
            assertNull(client.awaitPacket(200, TimeUnit.MILLISECONDS));

            client.sendPacket(new Packet(PacketType.ACKNOWLEDGE.getCode(), new byte[0]));
            assertQueueEmpty(userId);
            assertNull(client.awaitPacket(200, TimeUnit.MILLISECONDS));
        } finally {
            client.getSession().close();
        }
    }

    private byte[] encodeFrame(Packet packet) {
        ByteBuffer encoded = packetCodec.encode(packet);
        byte[] frame = new byte[encoded.remaining()];
        encoded.get(frame);
        return frame;
    }

    private void assertPacketEquals(Packet expected, Packet actual) {
        assertEquals(expected.getType(), actual.getType());
        assertArrayEquals(expected.getPayload(), actual.getPayload());
    }

    private void assertQueueEmpty(long userId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (userQueue.peekFirstForUser(userId).isEmpty()) return;
            Thread.sleep(25);
        }
        assertTrue(userQueue.peekFirstForUser(userId).isEmpty(), "All ACKed frames should be removed");
    }
}
