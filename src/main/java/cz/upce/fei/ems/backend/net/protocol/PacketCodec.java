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
        byte type = buffer.get();
        return new Packet(type, buffer.array());
    }
}