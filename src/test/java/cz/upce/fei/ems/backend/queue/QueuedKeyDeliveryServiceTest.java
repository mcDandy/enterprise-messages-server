package cz.upce.fei.ems.backend.queue;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.service.QueuedKeyDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QueuedKeyDeliveryServiceTest {
    private final PostgresUserQueue queue = mock(PostgresUserQueue.class);
    private final ChannelMemberRepository members = mock(ChannelMemberRepository.class);
    private final PacketDispatcher dispatcher = mock(PacketDispatcher.class);
    private final PacketCodec codec = new PacketCodec();
    private final QueuedKeyDeliveryService delivery =
            new QueuedKeyDeliveryService(queue, members, codec, dispatcher);
    private final WebSocketSession session = mock(WebSocketSession.class);

    @Test
    void failedSendDoesNotDeleteQueuedPacket() throws Exception {
        byte[] frame = frame();
        when(queue.claimForUser(5L, 60, 100)).thenReturn(List.of(new QueuedPacket(11L, 3L, frame)));
        when(members.existsByChannelIdAndUserId(3L, 5L)).thenReturn(true);
        when(session.isOpen()).thenReturn(true);
        doThrow(new IOException("socket disconnected")).when(dispatcher).sendTo(eq(session), any(Packet.class));

        delivery.deliverQueuedKeys(5L, "bob", session);
        verify(queue, never()).acknowledgeForUser(5L, 11L);
    }

    @Test
    void successfulSendDeletesQueuedPacket() throws Exception {
        byte[] frame = frame();
        when(queue.claimForUser(5L, 60, 100)).thenReturn(List.of(new QueuedPacket(12L, 3L, frame)));
        when(members.existsByChannelIdAndUserId(3L, 5L)).thenReturn(true);
        when(session.isOpen()).thenReturn(true);

        delivery.deliverQueuedKeys(5L, "bob", session);
        verify(dispatcher).sendTo(eq(session), any(Packet.class));
        verify(queue).acknowledgeForUser(5L, 12L);
    }

    @Test
    void removedMemberNeverReceivesPendingKey() throws Exception {
        when(queue.claimForUser(5L, 60, 100)).thenReturn(List.of(new QueuedPacket(13L, 3L, frame())));
        when(members.existsByChannelIdAndUserId(3L, 5L)).thenReturn(false);

        delivery.deliverQueuedKeys(5L, "bob", session);
        verify(dispatcher, never()).sendTo(any(), any());
        verify(queue).acknowledgeForUser(5L, 13L);
    }

    @Test
    void opaqueUnscopedPacketDoesNotRequireChannelMembership() throws Exception {
        byte[] raw = codec.encode(new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), new byte[]{7})).array();
        when(queue.claimForUser(5L, 60, 100)).thenReturn(List.of(new QueuedPacket(14L, null, raw)));
        when(session.isOpen()).thenReturn(true);
        delivery.deliverQueuedKeys(5L, "bob", session);
        verifyNoInteractions(members);
        verify(dispatcher).sendTo(eq(session), any(Packet.class));
        verify(queue).acknowledgeForUser(5L, 14L);
    }

    private byte[] frame() {
        ByteBuffer encoded = codec.encode(new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(),
                new byte[]{8, 9, 10}));
        byte[] data = new byte[encoded.remaining()];
        encoded.get(data);
        return data;
    }
}
