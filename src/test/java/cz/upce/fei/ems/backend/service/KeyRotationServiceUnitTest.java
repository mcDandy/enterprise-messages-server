package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeyRotationServiceUnitTest {
    @Mock
    private SessionRegistry sessionRegistry;
    @Mock
    private PacketDispatcher dispatcher;
    @Mock
    private ChannelKeyRepository channelKeyRepository;
    @Mock
    private ChannelMemberRepository channelMemberRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PostgresUserQueue userQueue;
    @org.mockito.Spy
    private PacketCodec packetCodec = new PacketCodec();
    @Mock
    private QueuedKeyDeliveryService keyDeliveryService;
    @Mock
    private WebSocketSession session;

    @InjectMocks
    private KeyRotationService keyRotationService;

    @Test
    void testSolicitateNewKeyRotationOnline() throws Exception {
        when(sessionRegistry.getSession("user1")).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", "user1");
        when(session.getAttributes()).thenReturn(attributes);
        when(channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(10L))
                .thenReturn(Optional.empty());

        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user(1L, "user1")));
        keyRotationService.solicitateNewKeyRotation(10L, Set.of("user1"));

        verify(channelKeyRepository).save(any(ChannelKey.class));
        verify(userQueue).enqueueForUser(eq(1L), eq(10L), any(byte[].class));
        verify(keyDeliveryService).deliverQueuedKeys(1L, "user1", session);
        verify(dispatcher, never()).sendTo(any(), any());
    }

    @Test
    void testSolicitateNewKeyRotationOffline() throws Exception {
        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user(1L, "user1")));
        keyRotationService.solicitateNewKeyRotation(10L, Set.of("user1"));

        verify(channelKeyRepository).save(any(ChannelKey.class));
        verify(userQueue).enqueueForUser(eq(1L), eq(10L), any(byte[].class));
        verifyNoInteractions(keyDeliveryService);
        verify(dispatcher, never()).sendTo(any(), any());
    }

    @Test
    void testHandleClientUnicastRotationOnline() throws Exception {
        stubValidUnicastRotation();
        when(sessionRegistry.getSession("user2")).thenReturn(session);
        when(session.isOpen()).thenReturn(true);

        keyRotationService.handleClientUnicastRotation(
                10L, 1L, "user1", 2L, "cipher".getBytes(StandardCharsets.UTF_8));

        InOrder inOrder = inOrder(userQueue, keyDeliveryService);
        inOrder.verify(userQueue).enqueueForUser(eq(2L), eq(10L), any(byte[].class));
        inOrder.verify(keyDeliveryService).deliverQueuedKeys(2L, "user2", session);
        verify(dispatcher, never()).sendTo(any(), any());
    }

    @Test
    void testHandleClientUnicastRotationOffline() throws Exception {
        stubValidUnicastRotation();

        keyRotationService.handleClientUnicastRotation(
                10L, 1L, "user1", 2L, "cipher".getBytes(StandardCharsets.UTF_8));

        verify(userQueue).enqueueForUser(eq(2L), eq(10L), any(byte[].class));
        verifyNoInteractions(keyDeliveryService);
        verify(dispatcher, never()).sendTo(any(), any());
    }

    private static AppUser user(long id, String username) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private void stubValidUnicastRotation() {
        when(channelMemberRepository.existsByChannelIdAndUserId(10L, 1L)).thenReturn(true);
        when(channelMemberRepository.existsByChannelIdAndUserId(10L, 2L)).thenReturn(true);

        ChannelKey channelKey = new ChannelKey();
        channelKey.setVersion(UUID.randomUUID());
        when(channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(10L))
                .thenReturn(Optional.of(channelKey));

        AppUser recipient = new AppUser();
        recipient.setUsername("user2");
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(recipient));
    }
}
