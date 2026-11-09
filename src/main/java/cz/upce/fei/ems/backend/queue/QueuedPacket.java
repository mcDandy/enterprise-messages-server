package cz.upce.fei.ems.backend.queue;

/** An opaque WebSocket frame claimed from one recipient's PGMQ queue. */
public record QueuedPacket(long messageId, Long channelId, byte[] frame) {}
