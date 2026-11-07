package cz.upce.fei.ems.backend.net.protocol;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class PacketCodecUnitTest {

    private final PacketCodec codec = new PacketCodec();

    @Test
    void testEncodeDecode() {
        byte type = 0x01;
        byte[] payload = "Hello".getBytes();
        Packet packet = new Packet(type, payload);

        ByteBuffer encoded = codec.encode(packet);
        assertEquals(1 + payload.length, encoded.remaining());

        Packet decoded = codec.decode(encoded);
        assertEquals(type, decoded.getType());
        assertArrayEquals(payload, decoded.getPayload());
    }

    @Test
    void testEncodeDecodeEmptyPayload() {
        byte type = 0x0a;
        byte[] payload = new byte[0];
        Packet packet = new Packet(type, payload);

        ByteBuffer encoded = codec.encode(packet);
        assertEquals(1, encoded.remaining());

        Packet decoded = codec.decode(encoded);
        assertEquals(type, decoded.getType());
        assertArrayEquals(payload, decoded.getPayload());
    }

    @Test
    void testEncodeDecodeNullPayload() {
        byte type = 0x00;
        Packet packet = new Packet(type, null);

        ByteBuffer encoded = codec.encode(packet);
        assertEquals(1, encoded.remaining());

        Packet decoded = codec.decode(encoded);
        assertEquals(type, decoded.getType());
        assertEquals(0, decoded.getPayload().length);
    }
}
