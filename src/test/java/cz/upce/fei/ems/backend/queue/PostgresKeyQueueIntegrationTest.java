package cz.upce.fei.ems.backend.queue;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real PostgresKeyQueue SQL against the isolated PostgreSQL + PGMQ
 * db-test service. Unlike PostgresKeyQueueTest, this does not mock JdbcTemplate.
 * Do not run it against the application's persistent database.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresKeyQueue.class, PacketCodec.class})
// PGMQ visibility timeouts and acknowledgments must survive a committed read.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresKeyQueueIntegrationTest {

    @Autowired
    private PostgresKeyQueue queue;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> deliveryIds = new ArrayList<>();
    private final List<Long> rotationIds = new ArrayList<>();

    @BeforeEach
    void requireIsolatedPgmqDatabase() {
        // Fail closed rather than enqueueing test data into a real application DB.
        assertEquals("nnpro_test", jdbc.queryForObject("SELECT current_database()", String.class),
                "PGMQ integration tests must only run on nnpro_test");
        assertEquals("1.10.0", jdbc.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'pgmq'", String.class),
                "Expected the pinned PGMQ extension version");
        assertNotNull(jdbc.queryForObject("SELECT to_regclass('pgmq.q_nnpro_key_delivery')", String.class));
        assertNotNull(jdbc.queryForObject("SELECT to_regclass('pgmq.q_nnpro_key_rotation')", String.class));
    }

    @AfterEach
    void removeOnlyMessagesCreatedByThisTest() {
        // No TRUNCATE or global purge: this test must not alter other test data.
        for (long id : deliveryIds) {
            jdbc.queryForObject("SELECT pgmq.delete('nnpro_key_delivery', ?)", Boolean.class, id);
        }
        for (long id : rotationIds) {
            jdbc.queryForObject("SELECT pgmq.delete('nnpro_key_rotation', ?)", Boolean.class, id);
        }
    }

    @Test
    void deliversCompleteBinaryPacketOnlyToItsRecipient() {
        long recipientA = uniquePositiveId();
        long recipientB = uniquePositiveId();
        long channel = uniquePositiveId();
        Packet original = new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(),
                new byte[]{0, 1, (byte) 0xff, 42});
        Packet other = new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(),
                new byte[]{12, 24});

        long idA = enqueueDelivery(recipientA, channel, original);
        long idB = enqueueDelivery(recipientB, channel, other);

        List<QueuedKeyPacket> claimedA = queue.claimForUser(recipientA, 30, 10);
        assertEquals(1, claimedA.size(), "Recipient A must not claim recipient B's packet");
        QueuedKeyPacket received = claimedA.getFirst();
        assertEquals(idA, received.messageId());
        assertEquals(channel, received.channelId());
        ByteBuffer expected = new PacketCodec().encode(original);
        byte[] expectedFrame = new byte[expected.remaining()];
        expected.get(expectedFrame);
        assertArrayEquals(expectedFrame, received.frame(), "The full WebSocket frame must survive PGMQ");

        Packet decoded = new PacketCodec().decode(ByteBuffer.wrap(received.frame()));
        assertEquals(original.getType(), decoded.getType());
        assertArrayEquals(original.getPayload(), decoded.getPayload());

        assertTrue(queue.claimForUser(recipientA, 30, 10).isEmpty(),
                "A claimed packet must be temporarily invisible");
        List<QueuedKeyPacket> claimedB = queue.claimForUser(recipientB, 30, 10);
        assertEquals(1, claimedB.size());
        assertEquals(idB, claimedB.getFirst().messageId());

        assertTrue(queue.acknowledgeForUser(idA));
        assertTrue(queue.acknowledgeForUser(idB));
        assertFalse(queue.acknowledgeForUser(idA), "Acknowledging twice should be harmless");
        assertTrue(queue.claimForUser(recipientA, 1, 10).isEmpty());
        assertTrue(queue.claimForUser(recipientB, 1, 10).isEmpty());
    }

    @Test
    void retriesAnUnacknowledgedPacketAfterVisibilityTimeout() throws InterruptedException {
        long recipient = uniquePositiveId();
        long id = enqueueDelivery(recipient, uniquePositiveId(),
                new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{99}));

        assertEquals(id, queue.claimForUser(recipient, 1, 10).getFirst().messageId());
        assertTrue(queue.claimForUser(recipient, 1, 10).isEmpty());

        // Visibility timeout is checked by PostgreSQL, not by an in-memory mock.
        Thread.sleep(1800);

        List<QueuedKeyPacket> retried = queue.claimForUser(recipient, 30, 10);
        assertEquals(1, retried.size(), "An unacknowledged message must become visible again");
        assertEquals(id, retried.getFirst().messageId());
        assertTrue(queue.acknowledgeForUser(id));
    }

    @Test
    void isolatesRotationRequestsByChannelAndAcknowledgesThem() {
        long channelA = uniquePositiveId();
        long channelB = uniquePositiveId();
        long idA = enqueueRotation(channelA);
        long idB = enqueueRotation(channelB);

        assertEquals(List.of(idA), queue.claimRotations(channelA, 30, 10));
        assertTrue(queue.claimRotations(channelA, 30, 10).isEmpty());
        assertEquals(List.of(idB), queue.claimRotations(channelB, 30, 10));

        assertTrue(queue.acknowledgeRotation(idA));
        assertTrue(queue.acknowledgeRotation(idB));
        assertFalse(queue.acknowledgeRotation(idA));
        assertTrue(queue.claimRotations(channelA, 1, 10).isEmpty());
        assertTrue(queue.claimRotations(channelB, 1, 10).isEmpty());
    }

    private long enqueueDelivery(long recipient, long channel, Packet packet) {
        long id = queue.enqueueForUser(recipient, channel, packet);
        deliveryIds.add(id);
        return id;
    }

    private long enqueueRotation(long channel) {
        long id = queue.enqueueRotation(channel);
        rotationIds.add(id);
        return id;
    }

    private static long uniquePositiveId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
