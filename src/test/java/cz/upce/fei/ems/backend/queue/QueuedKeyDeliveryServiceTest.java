package cz.upce.fei.ems.backend.queue;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.service.QueuedKeyDeliveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests transport invariants only. The backend does not implement client ACK
 * or post-enqueue channel authorization yet; tests must not endorse premature
 * deletion after a successful WebSocket write or silently skip security checks.
 */
class QueuedKeyDeliveryServiceTest {
    private final PostgresUserQueue queue = mock(PostgresUserQueue.class);
    private final PacketDispatcher dispatcher = mock(PacketDispatcher.class);
    private final PacketCodec codec = new PacketCodec();
    private final QueuedKeyDeliveryService delivery =
            new QueuedKeyDeliveryService(queue, codec, dispatcher);
    private final WebSocketSession session = mock(WebSocketSession.class);

    @Test
    void failedSendDoesNotDeleteQueuedPacket() throws Exception {
        when(queue.claimForUser(5L, 60, 100))
                .thenReturn(List.of(new QueuedPacket(11L, frame(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{8, 9, 10}))));
        when(session.isOpen()).thenReturn(true);
        doThrow(new IOException("socket disconnected")).when(dispatcher)
                .sendTo(eq(session), any(Packet.class));

        delivery.deliverQueuedKeys(5L, "bob", session);
        verify(queue, never()).acknowledgeForUser(anyLong(), anyLong());
    }

    @Test
    void closedSessionDoesNotForwardOrDeleteQueuedPacket() throws Exception {
        when(queue.claimForUser(5L, 60, 100))
                .thenReturn(List.of(new QueuedPacket(15L, frame(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{8, 9}))));
        when(session.isOpen()).thenReturn(false);

        delivery.deliverQueuedKeys(5L, "bob", session);
        verifyNoInteractions(dispatcher);
        verify(queue, never()).acknowledgeForUser(anyLong(), anyLong());
    }

    @Test
    void forwardsFullOpaqueFrameWithoutInterpretingItsType() throws Exception {
        byte[] payload = {7, 0, (byte) 0xff};
        byte[] raw = frame(PacketType.CHAT_MESSAGE_ENC.getCode(), payload);
        when(queue.claimForUser(5L, 60, 100))
                .thenReturn(List.of(new QueuedPacket(14L, raw)));
        when(session.isOpen()).thenReturn(true);

        delivery.deliverQueuedKeys(5L, "bob", session);

        ArgumentCaptor<Packet> actual = ArgumentCaptor.forClass(Packet.class);
        verify(dispatcher).sendTo(eq(session), actual.capture());
        assertEquals(PacketType.CHAT_MESSAGE_ENC.getCode(), actual.getValue().getType());
        assertArrayEquals(payload, actual.getValue().getPayload());
        // Do NOT assert that sendTo() triggers pgmq.delete(). Client ACK is not
        // implemented, and an OS-level WebSocket write is not proof of receipt.
    }

    private byte[] frame(byte type, byte[] payload) {
        ByteBuffer encoded = codec.encode(new Packet(type, payload));
        byte[] result = new byte[encoded.remaining()];
        encoded.get(result);
        return result;
    }
}
