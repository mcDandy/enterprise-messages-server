package cz.upce.fei.ems.backend.queue;

/** A WebSocket frame and optional application-message metadata from a user's PGMQ queue. */
public record QueuedPacket(long messageId, byte[] frame, Long applicationMessageId) {
    public QueuedPacket(long messageId, byte[] frame) {
        this(messageId, frame, null);
    }
}
