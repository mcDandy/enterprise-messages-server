package cz.upce.fei.ems.backend.net.protocol;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

/**
 * Encodes and decodes the binary packet frame used by the WebSocket protocol.
 */

@Component
public class PacketCodec {

    private static final int TYPE_FIELD_SIZE = 1;

    /**
     * Encodes a frame as one type byte followed by the payload, without adding a length prefix.
     *
     * @param packet packet to encode
     * @return a flipped buffer ready for reading or sending
     */
    public ByteBuffer encode(Packet packet) {
        int payloadLength = packet.getPayload() != null ? packet.getPayload().length : 0;

        ByteBuffer buffer = ByteBuffer.allocate(TYPE_FIELD_SIZE + payloadLength);

        buffer.put(packet.getType());

        if (payloadLength > 0) {
            buffer.put(packet.getPayload());
        }

        buffer.flip();
        return buffer;
    }

    /**
     * Decodes one complete frame and consumes the supplied buffer’s remaining bytes.
     *
     * @param buffer frame bytes; the first remaining byte is the packet type
     * @return decoded packet with all remaining bytes as its payload
     * @throws IllegalArgumentException when the frame is empty or exceeds the configured maximum size
     */
    public Packet decode(ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) {
            throw new IllegalArgumentException("Packet frame is empty");
        }
        if (buffer.remaining() > PacketValidation.MAX_PAYLOAD_BYTES + TYPE_FIELD_SIZE) {
            throw new IllegalArgumentException("Packet frame exceeds the maximum size");
        }
        byte type = buffer.get();
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);

        return new Packet(type, payload);
    }
}
