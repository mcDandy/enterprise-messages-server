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

    public long enqueueForUser(long userId, byte[] frame) {
        return enqueueForUser(userId, null, frame);
    }

    /**
     * Channel scope is optional transport metadata used by key-delivery authorization.
     * The frame remains opaque (including its existing multiplex byte).
     */
    public long enqueueForUser(long userId, Long channelId, byte[] frame) {
        if (frame == null || frame.length == 0) throw new IllegalArgumentException("Empty frame");
        Long id = jdbc.queryForObject(
                "SELECT pgmq.send(?, jsonb_build_object('frame', " +
                        "encode(CAST(? AS bytea), 'base64'), 'channelId', CAST(? AS bigint)))",
                Long.class, queueName(userId), frame, channelId);
        if (id == null || id <= 0) throw new IllegalStateException("PGMQ did not return a message ID");
        return id;
    }

    public List<QueuedPacket> claimForUser(long userId, int visibilityTimeoutSeconds, int limit) {
        if (visibilityTimeoutSeconds <= 0 || limit <= 0)
            throw new IllegalArgumentException("Invalid queue claim limit/timeout");
        return jdbc.query(
                "SELECT msg_id, (message->>'channelId')::bigint AS channel_id, " +
                        "decode(message->>'frame', 'base64') AS frame " +
                        "FROM pgmq.read(?, ?, ?)",
                (rs, index) -> new QueuedPacket(rs.getLong("msg_id"),
                        rs.getObject("channel_id", Long.class), rs.getBytes("frame")),
                queueName(userId), visibilityTimeoutSeconds, limit);
    }

    /** Message IDs are unique only inside a user's queue. */
    public boolean acknowledgeForUser(long userId, long messageId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pgmq.delete(?, ?)", Boolean.class, queueName(userId), messageId));
    }
}
