package cz.upce.fei.ems.backend.queue;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * PGMQ adapter; data is durable in PostgreSQL, not in JVM memory.
 * A read reserves a row for visibilityTimeoutSeconds; ack deletes it.
 * Repeated deliveries are possible, especially if the process crashes after send.
 * Filtering by JSONB field uses PGMQ's experimental conditional-read API;
 * keep the extension version pinned and integration-test upgrades.
 */
@Repository
@RequiredArgsConstructor
public class PostgresKeyQueue {
    private static final String DELIVERY_QUEUE = "nnpro_key_delivery";
    private static final String ROTATION_QUEUE = "nnpro_key_rotation";
    private final JdbcTemplate jdbc;
    private final PacketCodec packetCodec;

    public long enqueueForUser(long recipientUserId, long channelId, Packet packet) {
        ByteBuffer buffer = packetCodec.encode(packet);
        byte[] frame = new byte[buffer.remaining()];
        buffer.get(frame);
        Long id = jdbc.queryForObject(
                "SELECT pgmq.send('" + DELIVERY_QUEUE + "', " +
                        "jsonb_build_object('recipientUserId', CAST(? AS bigint), " +
                        "'channelId', CAST(? AS bigint), 'frame', encode(CAST(? AS bytea), 'base64')))",
                Long.class, recipientUserId, channelId, frame);
        return requireId(id);
    }

    public List<QueuedKeyPacket> claimForUser(long userId, int visibilityTimeoutSeconds, int limit) {
        checkClaimArgs(visibilityTimeoutSeconds, limit);
        return jdbc.query(
                "SELECT msg_id, (message->>'channelId')::bigint AS channel_id, " +
                        "decode(message->>'frame', 'base64') AS frame " +
                        "FROM pgmq.read('" + DELIVERY_QUEUE + "', ?, ?, " +
                        "jsonb_build_object('recipientUserId', CAST(? AS bigint)))",
                (rs, index) -> new QueuedKeyPacket(rs.getLong("msg_id"),
                        rs.getLong("channel_id"), rs.getBytes("frame")),
                visibilityTimeoutSeconds, limit, userId);
    }

    public boolean acknowledgeForUser(long messageId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pgmq.delete('" + DELIVERY_QUEUE + "', ?)", Boolean.class, messageId));
    }

    public long enqueueRotation(long channelId) {
        Long id = jdbc.queryForObject(
                "SELECT pgmq.send('" + ROTATION_QUEUE + "', " +
                        "jsonb_build_object('channelId', CAST(? AS bigint)))",
                Long.class, channelId);
        return requireId(id);
    }

    public List<Long> claimRotations(long channelId, int visibilityTimeoutSeconds, int limit) {
        checkClaimArgs(visibilityTimeoutSeconds, limit);
        return jdbc.query(
                "SELECT msg_id FROM pgmq.read('" + ROTATION_QUEUE + "', ?, ?, " +
                        "jsonb_build_object('channelId', CAST(? AS bigint)))",
                (rs, index) -> rs.getLong("msg_id"),
                visibilityTimeoutSeconds, limit, channelId);
    }

    public boolean acknowledgeRotation(long messageId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pgmq.delete('" + ROTATION_QUEUE + "', ?)", Boolean.class, messageId));
    }

    private static long requireId(Long id) {
        if (id == null || id <= 0) throw new IllegalStateException("PGMQ did not return a message ID");
        return id;
    }

    private static void checkClaimArgs(int timeout, int limit) {
        if (timeout <= 0 || limit <= 0) throw new IllegalArgumentException("Invalid queue claim limit/timeout");
    }
}
