package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.service.QueuedKeyDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

class AcknowledgePacketHandlerTest {
    private final PacketDispatcher dispatcher = mock(PacketDispatcher.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final QueuedKeyDeliveryService deliveryService = mock(QueuedKeyDeliveryService.class);
    private final AcknowledgePacketHandler handler =
            new AcknowledgePacketHandler(dispatcher, appUserRepository, deliveryService);
    private final WebSocketSession session = mock(WebSocketSession.class);

    @Test
    void acknowledgesForAuthenticatedSessionUser() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", "alice");
        when(session.getAttributes()).thenReturn(attributes);
        AppUser user = new AppUser();
        user.setId(42L);
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        handler.handle(session, new Packet(PacketType.ACKNOWLEDGE.getCode(), new byte[0]));

        verify(deliveryService).acknowledgeAndSendNext(42L, "alice", session);
        verifyNoInteractions(dispatcher);
    }

    @Test
    void rejectsAcknowledgmentPayload() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", "alice");
        when(session.getAttributes()).thenReturn(attributes);

        handler.handle(session, new Packet(PacketType.ACKNOWLEDGE.getCode(), new byte[]{1}));

        verifyNoInteractions(appUserRepository, deliveryService);
        verify(dispatcher).sendError(session, "Invalid acknowledgment packet.");
    }

    @Test
    void rejectsUnauthenticatedSession() throws Exception {
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.handle(session, new Packet(PacketType.ACKNOWLEDGE.getCode(), new byte[0]));

        verifyNoInteractions(appUserRepository, deliveryService);
        verify(dispatcher).sendError(session, "Unauthorized connection.");
    }
}
