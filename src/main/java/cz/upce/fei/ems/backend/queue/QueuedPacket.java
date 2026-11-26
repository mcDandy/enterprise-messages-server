package cz.upce.fei.ems.backend.queue;

/**
 * A WebSocket frame and optional application-message metadata from a user’s PGMQ queue.
 *
 * @param messageId PGMQ identifier, unique only within this user’s queue
 * @param frame complete encoded WebSocket protocol frame
 * @param applicationMessageId database message ID, absent for non-chat and legacy queue entries
 */
public record QueuedPacket(long messageId, byte[] frame, Long applicationMessageId) {
    /** Creates a legacy queue entry without application-message metadata. */
    public QueuedPacket(long messageId, byte[] frame) {
        this(messageId, frame, null);
    }
}
