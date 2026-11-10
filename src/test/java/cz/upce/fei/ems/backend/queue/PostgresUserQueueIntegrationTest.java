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

/** Integration tests must run only against the disposable nnpro_test database. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresUserQueue.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresUserQueueIntegrationTest {
    @Autowired private PostgresUserQueue queue;
    @Autowired private JdbcTemplate jdbc;
    private final List<Long> testUsers = new ArrayList<>();

    @BeforeEach
    void requireIsolatedPgmqDatabase() {
        assertEquals("nnpro_test", jdbc.queryForObject("SELECT current_database()", String.class));
        assertEquals("1.10.0", jdbc.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'pgmq'", String.class));
        assertNull(jdbc.queryForObject("SELECT to_regclass('pgmq.q_nnpro_key_delivery')", String.class));
        assertNull(jdbc.queryForObject("SELECT to_regclass('pgmq.q_nnpro_key_rotation')", String.class));
        assertEquals("ems_create_user_queue", jdbc.queryForObject(
                "SELECT p.proname FROM pg_proc p " +
                        "JOIN pg_trigger t ON t.tgfoid = p.oid " +
                        "WHERE t.tgrelid = 'public.app_user'::regclass " +
                        "AND t.tgname = 'ems_user_queue_on_insert' AND NOT t.tgisinternal",
                String.class));
        assertEquals(Long.valueOf(0), jdbc.queryForObject(
                "SELECT count(*) FROM pgmq.meta WHERE queue_name ~ '^nnpro_user_[0-9]+$'", Long.class));
        assertEquals(Long.valueOf(0), jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger " +
                        "WHERE tgrelid = 'public.app_user'::regclass " +
                        "AND tgname = 'nnpro_user_queue_on_insert' AND NOT tgisinternal", Long.class));
    }

    @AfterEach
    void dropOnlyTestQueues() {
        for (long userId : testUsers) queue.dropForUser(userId);
    }

    @Test
    void isolatesQueuesAndPreservesFramesOfDifferentPacketTypes() {
        long userA = createTestQueue();
        long userB = createTestQueue();
        byte[] key = frame(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{0, (byte) 0xff, 42});
        byte[] chat = frame(PacketType.CHAT_MESSAGE_ENC.getCode(), new byte[]{12, 24});
        long idA = queue.enqueueForUser(userA, key);
        long idB = queue.enqueueForUser(userB, chat);
        assertEquals(1L, idA);
        assertEquals(1L, idB); // IDs are local to each user's queue.

        QueuedPacket receivedA = queue.claimForUser(userA, 30, 10).getFirst();
        assertEquals(idA, receivedA.messageId());
        assertArrayEquals(key, receivedA.frame());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM pgmq.\"q_" + PostgresUserQueue.queueName(userA) +
                        "\" WHERE msg_id = ? AND message = jsonb_build_object('frame', ?) ",
                Long.class, idA, java.util.Base64.getEncoder().encodeToString(key)));
        assertTrue(queue.claimForUser(userA, 30, 10).isEmpty());

        QueuedPacket receivedB = queue.claimForUser(userB, 30, 10).getFirst();
        assertEquals(idB, receivedB.messageId());
        assertArrayEquals(chat, receivedB.frame());
        assertTrue(queue.acknowledgeForUser(userA, idA));
        assertTrue(queue.acknowledgeForUser(userB, idB));
        assertFalse(queue.acknowledgeForUser(userA, idA));
        assertTrue(queue.claimForUser(userA, 1, 10).isEmpty());
        assertTrue(queue.claimForUser(userB, 1, 10).isEmpty());
    }

    @Test
    void mixesMultiplePacketTypesInTheSameUserQueueWithoutParsingThem() {
        long user = createTestQueue();
        byte[] chat = frame(PacketType.CHAT_MESSAGE_ENC.getCode(), new byte[]{1});
        byte[] rotate = frame(PacketType.ROTATE_KEY.getCode(), new byte[]{2});
        byte[] unicast = frame(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{3});

        queue.enqueueForUser(user, chat);
        queue.enqueueForUser(user, rotate);
        queue.enqueueForUser(user, unicast);
        List<QueuedPacket> claimed = queue.claimForUser(user, 30, 10);

        assertEquals(3, claimed.size());
        assertArrayEquals(chat, claimed.get(0).frame());
        assertArrayEquals(rotate, claimed.get(1).frame());
        assertArrayEquals(unicast, claimed.get(2).frame());
        for (QueuedPacket packet : claimed) assertTrue(queue.acknowledgeForUser(user, packet.messageId()));
    }

    @Test
    void peeksAndAcknowledgesQueueHeadInFifoOrder() {
        long user = createTestQueue();
        byte[] first = new byte[]{0x01, 10};
        byte[] second = new byte[]{0x02, 20};
        queue.enqueueForUser(user, first);
        queue.enqueueForUser(user, second);

        assertArrayEquals(first, queue.peekFirstForUser(user).getFirst().frame());
        assertArrayEquals(first, queue.peekFirstForUser(user).getFirst().frame());
        assertTrue(queue.acknowledgeFirstForUser(user));
        assertArrayEquals(second, queue.peekFirstForUser(user).getFirst().frame());
        assertTrue(queue.acknowledgeFirstForUser(user));
        assertFalse(queue.acknowledgeFirstForUser(user));
        assertTrue(queue.peekFirstForUser(user).isEmpty());
    }

    @Test
    void retriesUnacknowledgedFrameAfterVisibilityTimeout() throws InterruptedException {
        long user = createTestQueue();
        long id = queue.enqueueForUser(user, new byte[]{0x01, 99});
        assertEquals(id, queue.claimForUser(user, 1, 10).getFirst().messageId());
        assertTrue(queue.claimForUser(user, 1, 10).isEmpty());
        Thread.sleep(1800);
        assertEquals(id, queue.claimForUser(user, 30, 10).getFirst().messageId());
        assertTrue(queue.acknowledgeForUser(user, id));
    }

    @Test
    void registrationTriggerCreatesQueueAutomatically() {
        String username = "pgmq_test_" + UUID.randomUUID().toString().replace("-", "");
        long id = jdbc.queryForObject(
                "INSERT INTO app_user(username, password_hash) VALUES (?, 'test') RETURNING id",
                Long.class, username);
        try {
            assertNotNull(jdbc.queryForObject(
                    "SELECT to_regclass(?)", String.class, "pgmq.q_" + PostgresUserQueue.queueName(id)));
            assertEquals(1L, queue.enqueueForUser(id, new byte[]{(byte) 0xa0}));
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id = ?", id);
            queue.dropForUser(id);
        }
    }

    private long createTestQueue() {
        long id = uniquePositiveId();
        queue.createForUser(id);
        testUsers.add(id);
        return id;
    }

    private static byte[] frame(byte type, byte[] payload) {
        ByteBuffer data = new PacketCodec().encode(new Packet(type, payload));
        byte[] frame = new byte[data.remaining()];
        data.get(frame);
        return frame;
    }

    private static long uniquePositiveId() {
        long id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return id == 0 ? 1 : id;
    }
}
