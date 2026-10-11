package cz.upce.fei.ems.backend.net.protocol;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

@Component
public class PacketCodec {

    private static final int TYPE_FIELD_SIZE = 1;
    private static final int LENGTH_FIELD_SIZE = 4;
    private static final int HEADER_SIZE = TYPE_FIELD_SIZE + LENGTH_FIELD_SIZE;

    public ByteBuffer encode(Packet packet) {
        int payloadLength = packet.getPayload() != null ? packet.getPayload().length : 0;

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payloadLength);

        buffer.put(packet.getType());
        buffer.putInt(payloadLength);

        if (payloadLength > 0) {
            buffer.put(packet.getPayload());
        }

        buffer.flip();
        return buffer;
    }

    public Packet decode(ByteBuffer buffer) {
        byte type = buffer.get();
        int payloadLength = buffer.getInt();

        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            buffer.get(payload);
        }

        return new Packet(type, payload);
    }
}