package cz.upce.fei.ems.backend.queue;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class PostgresKeyQueueTest {
    @Test
    void encodesTheCompletePacketIncludingItsTypeBeforeEnqueue() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(8L), eq(2L), any(byte[].class)))
                .thenReturn(41L);
        PostgresKeyQueue queue = new PostgresKeyQueue(jdbc, new PacketCodec());
        Packet packet = new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{10, 20, 30});

        assertEquals(41L, queue.enqueueForUser(8L, 2L, packet));
        verify(jdbc).queryForObject(contains("nnpro_key_delivery"), eq(Long.class), eq(8L), eq(2L),
                aryEq(new byte[]{PacketType.UNICAST_KEY_ROTATION.getCode(), 10, 20, 30}));
    }

    @Test
    void packetCodecRoundTripPreservesOriginalBytes() {
        PacketCodec codec = new PacketCodec();
        Packet original = new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{0, 12, -5, 127});
        ByteBuffer encoded = codec.encode(original);
        Packet decoded = codec.decode(encoded);
        assertEquals(original.getType(), decoded.getType());
        assertArrayEquals(original.getPayload(), decoded.getPayload());
    }

    @Test
    void rejectsNonPositiveClaimParameters() {
        PostgresKeyQueue queue = new PostgresKeyQueue(mock(JdbcTemplate.class), new PacketCodec());
        assertThrows(IllegalArgumentException.class, () -> queue.claimForUser(1L, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> queue.claimRotations(2L, 60, 0));
    }
}
