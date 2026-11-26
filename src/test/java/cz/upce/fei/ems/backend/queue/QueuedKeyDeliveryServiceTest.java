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
 * Tests transport invariants only. Queue entries remain until a client ACK,
 * and queued channel authorization is not implemented yet.
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
        when(queue.peekFirstForUser(5L))
                .thenReturn(List.of(new QueuedPacket(11L, frame(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{8, 9, 10}))));
        when(session.isOpen()).thenReturn(true);
        doThrow(new IOException("socket disconnected")).when(dispatcher)
                .sendTo(eq(session), any(Packet.class));

        delivery.sendNextQueuedPacket(5L, "bob", session);
        verify(queue, never()).acknowledgeForUser(anyLong(), anyLong());
    }

    @Test
    void closedSessionDoesNotForwardOrDeleteQueuedPacket() {
        when(queue.peekFirstForUser(5L))
                .thenReturn(List.of(new QueuedPacket(15L, frame(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{8, 9}))));
        when(session.isOpen()).thenReturn(false);

        delivery.sendNextQueuedPacket(5L, "bob", session);
        verifyNoInteractions(dispatcher);
        verify(queue, never()).acknowledgeForUser(anyLong(), anyLong());
    }

    @Test
    void forwardsFullOpaqueFrameWithoutInterpretingItsType() throws Exception {
        byte[] payload = {7, 0, (byte) 0xff};
        byte[] raw = frame(PacketType.CHAT_MESSAGE_ENC.getCode(), payload);
        when(queue.peekFirstForUser(5L))
                .thenReturn(List.of(new QueuedPacket(14L, raw)));
        when(session.isOpen()).thenReturn(true);

        delivery.sendNextQueuedPacket(5L, "bob", session);

        ArgumentCaptor<Packet> actual = ArgumentCaptor.forClass(Packet.class);
        verify(dispatcher).sendTo(eq(session), actual.capture());
        assertEquals(PacketType.CHAT_MESSAGE_ENC.getCode(), actual.getValue().getType());
        assertArrayEquals(payload, actual.getValue().getPayload());
        verify(queue, never()).acknowledgeForUser(anyLong(), anyLong());
    }

    @Test
    void acknowledgmentRemovesQueueHeadAndSendsOneNextPacket() throws Exception {
        byte[] firstFrame = frame(PacketType.CHAT_MESSAGE_ENC.getCode(), new byte[]{1});
        byte[] secondFrame = frame(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{2});
        when(queue.peekFirstForUser(5L))
                .thenReturn(List.of(new QueuedPacket(21L, firstFrame)),
                        List.of(new QueuedPacket(22L, secondFrame)));
        when(queue.acknowledgeFirstForUser(5L)).thenReturn(true);
        when(session.isOpen()).thenReturn(true);

        delivery.sendNextQueuedPacket(5L, "bob", session);
        delivery.acknowledgeAndSendNext(5L, "bob", session);

        verify(queue).acknowledgeFirstForUser(5L);
        verify(dispatcher, times(2)).sendTo(eq(session), any(Packet.class));
        verify(queue, times(2)).peekFirstForUser(5L);
    }

    @Test
    void keepsUnacknowledgedRotationAheadOfNewlyQueuedMessage() throws Exception {
        byte[] rotationFrame = frame(PacketType.UNICAST_KEY_ROTATION.getCode(), new byte[]{2});
        byte[] messageFrame = frame(PacketType.CHAT_MESSAGE_ENC.getCode(), new byte[]{1});
        when(queue.peekFirstForUser(5L))
                .thenReturn(List.of(new QueuedPacket(31L, rotationFrame)),
                        List.of(new QueuedPacket(31L, rotationFrame)),
                        List.of(new QueuedPacket(32L, messageFrame)));
        when(queue.acknowledgeFirstForUser(5L)).thenReturn(true);
        when(session.isOpen()).thenReturn(true);

        // First delivery sends the existing queue head. A message arriving behind
        // it must not replace that head, even if delivery is triggered again.
        delivery.sendNextQueuedPacket(5L, "bob", session);
        delivery.sendNextQueuedPacket(5L, "bob", session);

        // Only the ACK advances the FIFO to the queued chat message.
        delivery.acknowledgeAndSendNext(5L, "bob", session);

        ArgumentCaptor<Packet> actual = ArgumentCaptor.forClass(Packet.class);
        verify(dispatcher, times(3)).sendTo(eq(session), actual.capture());
        assertEquals(PacketType.UNICAST_KEY_ROTATION.getCode(), actual.getAllValues().get(0).getType());
        assertEquals(PacketType.UNICAST_KEY_ROTATION.getCode(), actual.getAllValues().get(1).getType());
        assertEquals(PacketType.CHAT_MESSAGE_ENC.getCode(), actual.getAllValues().get(2).getType());
        assertArrayEquals(new byte[]{1}, actual.getAllValues().get(2).getPayload());
        verify(queue).acknowledgeFirstForUser(5L);
    }

    @Test
    void acknowledgmentWithEmptyQueueDoesNotSendAnything() {
        when(queue.acknowledgeFirstForUser(5L)).thenReturn(false);

        delivery.acknowledgeAndSendNext(5L, "bob", session);

        verify(queue).acknowledgeFirstForUser(5L);
        verify(queue, never()).peekFirstForUser(anyLong());
        verifyNoInteractions(dispatcher);
    }

    private byte[] frame(byte type, byte[] payload) {
        ByteBuffer encoded = codec.encode(new Packet(type, payload));
        byte[] result = new byte[encoded.remaining()];
        encoded.get(result);
        return result;
    }
}
