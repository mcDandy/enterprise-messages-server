package cz.upce.fei.ems.backend.queue;

/** A single complete WebSocket packet claimed from the persistent queue. */
public record QueuedKeyPacket(long messageId, long channelId, byte[] frame) {}
