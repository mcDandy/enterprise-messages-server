package cz.upce.fei.ems.backend.net.protocol;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

@Component
public class PacketCodec {

    private static final int TYPE_FIELD_SIZE = 1;

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
