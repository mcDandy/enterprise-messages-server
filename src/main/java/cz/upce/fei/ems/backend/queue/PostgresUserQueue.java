package cz.upce.fei.ems.backend.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresUserQueue {
    private static final String QUEUE_PREFIX = "ems_user_";
    private final JdbcTemplate jdbc;

    public static String queueName(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("User ID must be positive");
        return QUEUE_PREFIX + userId;
    }

    /** For provisioning and isolated tests; database registration triggers create real users' queues. */
    public void createForUser(long userId) {
        jdbc.query("SELECT pgmq.create(?)", rs -> { }, queueName(userId));
    }

    /** Permanently destroys a user's queue. Only call after user deletion and any retention checks. */
    public boolean dropForUser(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pgmq.drop_queue(?)", Boolean.class, queueName(userId)));
    }

    /** Stores the complete opaque WebSocket frame, including its multiplex byte. */
    public long enqueueForUser(long userId, byte[] frame) {
        if (frame == null || frame.length == 0) throw new IllegalArgumentException("Empty frame");
        Long id = jdbc.queryForObject(
                "SELECT pgmq.send(?, jsonb_build_object('frame', " +
                        "encode(CAST(? AS bytea), 'base64')))",
                Long.class, queueName(userId), frame);
        if (id == null || id <= 0) throw new IllegalStateException("PGMQ did not return a message ID");
        return id;
    }

    public List<QueuedPacket> claimForUser(long userId, int visibilityTimeoutSeconds, int limit) {
        if (visibilityTimeoutSeconds <= 0 || limit <= 0)
            throw new IllegalArgumentException("Invalid queue claim limit/timeout");
        return jdbc.query(
                "SELECT msg_id, decode(message->>'frame', 'base64') AS frame " +
                        "FROM pgmq.read(?, ?, ?)",
                (rs, index) -> new QueuedPacket(rs.getLong("msg_id"), rs.getBytes("frame")),
                queueName(userId), visibilityTimeoutSeconds, limit);
    }

    public List<QueuedPacket> peekFirstForUser(long userId) {
        return jdbc.query(
                "SELECT msg_id, decode(message->>'frame', 'base64') AS frame " +
                        "FROM pgmq.\"q_" + queueName(userId) + "\" ORDER BY msg_id LIMIT 1",
                (rs, index) -> new QueuedPacket(rs.getLong("msg_id"), rs.getBytes("frame")));
    }

    public boolean acknowledgeFirstForUser(long userId) {
        String queue = queueName(userId);
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COALESCE((SELECT pgmq.delete(?, msg_id) FROM (" +
                        "SELECT msg_id FROM pgmq.\"q_" + queue + "\" ORDER BY msg_id LIMIT 1" +
                        ") head), false)",
                Boolean.class, queue));
    }

    /** Message IDs are unique only inside a user's queue. */
    public boolean acknowledgeForUser(long userId, long messageId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pgmq.delete(?, ?)", Boolean.class, queueName(userId), messageId));
    }
}
