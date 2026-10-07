package cz.upce.fei.ems.backend.net.protocol;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Component
public class PacketCodec {

    private static final int HEADER_SIZE = 16; // 4 + 2 + 1 + 1 + 8
    private static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024; // 10MB limit

    public Packet decode(ByteBuffer buffer) {
        buffer.order(ByteOrder.BIG_ENDIAN);

        if (buffer.remaining() < HEADER_SIZE) {
            throw new IllegalArgumentException("Bajtové pole je kratší než hlavička paketu (16B).");
        }

        int payloadLength = buffer.getInt();
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Neplatná délka payloadu: " + payloadLength);
        }

        if (buffer.remaining() < (HEADER_SIZE - 4) + payloadLength) {
            throw new IllegalArgumentException("Nedostatek bajtů pro kompletní paket.");
        }

        short type = buffer.getShort();
        byte flags = buffer.get();
        byte reserved = buffer.get(); // přeskočení rezervovaného bajtu (0x00)
        long timestamp = buffer.getLong();

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        return new Packet(type, flags, timestamp, payload);
    }

    public ByteBuffer encode(Packet packet) {
        byte[] payload = packet.getPayload() != null ? packet.getPayload() : new byte[0];
        int totalSize = HEADER_SIZE + payload.length;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(payload.length);
        buffer.putShort(packet.getType());
        buffer.put(packet.getFlags());
        buffer.put((byte) 0x00); // Reserved byte
        buffer.putLong(packet.getTimestamp());
        buffer.put(payload);

        buffer.flip();
        return buffer;
    }
}