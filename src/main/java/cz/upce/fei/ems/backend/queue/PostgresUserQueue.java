package cz.upce.fei.ems.backend.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persists per-user outbound packets in PostgreSQL and removes them after acknowledgement.
 */

@Repository
@RequiredArgsConstructor
public class PostgresUserQueue {
    private static final String QUEUE_PREFIX = "ems_user_";
    private final JdbcTemplate jdbc;

    /**
     * Builds the internal PGMQ table name for a user.
     *
     * @param userId positive account ID
     * @return a queue name containing only the fixed prefix and numeric ID
     * @throws IllegalArgumentException when {@code userId} is not positive
     */
    public static String queueName(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("User ID must be positive");
        return QUEUE_PREFIX + userId;
    }

    /** For provisioning and isolated tests; database registration triggers create real users' queues. */
    public void createForUser(long userId) {
        jdbc.query("SELECT pgmq.create(?)", rs -> { }, queueName(userId));
    }

    /**
     * Permanently destroys a user's queue. Only call after user deletion and any retention checks.
     */
    public void dropForUser(long userId) {
        jdbc.queryForObject(
                "SELECT pgmq.drop_queue(?)", Boolean.class, queueName(userId));
    }

    /** Stores the complete opaque WebSocket frame, including its multiplex byte. */
    public long enqueueForUser(long userId, byte[] frame) {
        return enqueueForUser(userId, frame, null);
    }

    /** Stores a chat frame with its database message ID for delivery tracking after ACK. */
    public long enqueueMessageForUser(long userId, long applicationMessageId, byte[] frame) {
        if (applicationMessageId <= 0) throw new IllegalArgumentException("Message ID must be positive");
        return enqueueForUser(userId, frame, applicationMessageId);
    }

    private long enqueueForUser(long userId, byte[] frame, Long applicationMessageId) {
        if (frame == null || frame.length == 0) throw new IllegalArgumentException("Empty frame");
        Long id = jdbc.queryForObject(
                "SELECT pgmq.send(?, jsonb_strip_nulls(jsonb_build_object(" +
                        "'frame', encode(CAST(? AS bytea), 'base64'), " +
                        "'applicationMessageId', CAST(? AS bigint))))",
                Long.class, queueName(userId), frame, applicationMessageId);
        if (id == null || id <= 0) throw new IllegalStateException("PGMQ did not return a message ID");
        return id;
    }

    /**
     * Claims up to {@code limit} messages using PGMQ visibility timeout semantics.
     * A claim makes messages temporarily invisible to other consumers but does not delete them.
     *
     * @param userId queue owner
     * @param visibilityTimeoutSeconds duration before unacknowledged messages can be claimed again
     * @param limit maximum number of queue entries to claim
     * @return claimed entries with their optional application message IDs
     */
    public List<QueuedPacket> claimForUser(long userId, int visibilityTimeoutSeconds, int limit) {
        if (visibilityTimeoutSeconds <= 0 || limit <= 0)
            throw new IllegalArgumentException("Invalid queue claim limit/timeout");
        return jdbc.query(
                "SELECT msg_id, decode(message->>'frame', 'base64') AS frame, " +
                        "NULLIF(message->>'applicationMessageId', '')::bigint AS application_message_id " +
                        "FROM pgmq.read(?, ?, ?)",
                (rs, index) -> new QueuedPacket(rs.getLong("msg_id"), rs.getBytes("frame"),
                        rs.getObject("application_message_id", Long.class)),
                queueName(userId), visibilityTimeoutSeconds, limit);
    }

    /** @return the current queue head without claiming or deleting it, or an empty list */
    public List<QueuedPacket> peekFirstForUser(long userId) {
        return jdbc.query(
                "SELECT msg_id, decode(message->>'frame', 'base64') AS frame, " +
                        "NULLIF(message->>'applicationMessageId', '')::bigint AS application_message_id " +
                        "FROM pgmq.\"q_" + queueName(userId) + "\" ORDER BY msg_id LIMIT 1",
                (rs, index) -> new QueuedPacket(rs.getLong("msg_id"), rs.getBytes("frame"),
                        rs.getObject("application_message_id", Long.class)));
    }

    /** @return whether a queue-head packet was removed */
    public boolean acknowledgeFirstForUser(long userId) {
        return acknowledgeFirstPacketForUser(userId).isPresent();
    }

    /** Atomically removes and returns the head packet and its optional application metadata. */
    public Optional<QueuedPacket> acknowledgeFirstPacketForUser(long userId) {
        String queue = queueName(userId);
        List<QueuedPacket> acknowledged = jdbc.query(
                "SELECT head.msg_id, head.frame, head.application_message_id " +
                        "FROM (SELECT msg_id, decode(message->>'frame', 'base64') AS frame, " +
                        "NULLIF(message->>'applicationMessageId', '')::bigint AS application_message_id " +
                        "FROM pgmq.\"q_" + queue + "\" ORDER BY msg_id LIMIT 1 FOR UPDATE) head " +
                        "WHERE pgmq.delete(?, head.msg_id)",
                (rs, index) -> new QueuedPacket(rs.getLong("msg_id"), rs.getBytes("frame"),
                        rs.getObject("application_message_id", Long.class)),
                queue);
        return acknowledged.stream().findFirst();
    }

    /** Message IDs are unique only inside a user's queue. */
    public boolean acknowledgeForUser(long userId, long messageId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pgmq.delete(?, ?)", Boolean.class, queueName(userId), messageId));
    }
}
