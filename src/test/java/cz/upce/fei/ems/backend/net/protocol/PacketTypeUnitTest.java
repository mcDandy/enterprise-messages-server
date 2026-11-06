package cz.upce.fei.ems.backend.net.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketTypeUnitTest {

    @Test
    void testFromCode() {
        assertEquals(PacketType.UNKNOWN, PacketType.fromCode((byte) 0x00));
        assertEquals(PacketType.CHAT_MESSAGE_ENC, PacketType.fromCode((byte) 0x01));
        assertEquals(PacketType.ROTATE_KEY, PacketType.fromCode((byte) 0x0a));
        assertEquals(PacketType.UNICAST_KEY_ROTATION, PacketType.fromCode((byte) 0x0d));
    }

    @Test
    void testFromCodeUnknown() {
        assertEquals(PacketType.UNKNOWN, PacketType.fromCode((byte) 0xFF));
    }

    @Test
    void testCodes() {
        assertEquals((byte) 0x00, PacketType.UNKNOWN.getCode());
        assertEquals((byte) 0x01, PacketType.CHAT_MESSAGE_ENC.getCode());
        assertEquals((byte) 0x0a, PacketType.ROTATE_KEY.getCode());
        assertEquals((byte) 0x0d, PacketType.UNICAST_KEY_ROTATION.getCode());
    }
}
