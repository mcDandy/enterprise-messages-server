package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        verify(userQueue).enqueueForUser(eq(1L), any(byte[].class));
        verify(keyDeliveryService).sendNextQueuedPacket(1L, "user1", session);
        verify(dispatcher, never()).sendTo(any(), any());
    }

    @Test
    void testSolicitateNewKeyRotationOffline() throws Exception {
        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user(1L, "user1")));
        keyRotationService.solicitateNewKeyRotation(10L, Set.of("user1"));

        verify(channelKeyRepository).save(any(ChannelKey.class));
        verify(userQueue).enqueueForUser(eq(1L), any(byte[].class));
        verifyNoInteractions(keyDeliveryService);
        verify(dispatcher, never()).sendTo(any(), any());
    }

    @Test
    void rotationRequestQueuesSolicitationForAnotherChannelMember() {
        ChannelMember senderMembership = new ChannelMember();
        senderMembership.setUserId(1L);
        ChannelMember otherMembership = new ChannelMember();
        otherMembership.setUserId(2L);
        when(channelMemberRepository.findByChannelId(10L))
                .thenReturn(List.of(senderMembership, otherMembership));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user(1L, "user1")));
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(user(2L, "user2")));
        when(appUserRepository.findByUsername("user2")).thenReturn(Optional.of(user(2L, "user2")));

        keyRotationService.handleRotationRequest(10L, "user1");

        verify(userQueue).enqueueForUser(eq(2L), any(byte[].class));
        verify(userQueue, never()).enqueueForUser(eq(1L), any(byte[].class));
    }

    @Test
    void testHandleClientUnicastRotationOnline() throws Exception {
        stubValidUnicastRotation();
        when(sessionRegistry.getSession("user2")).thenReturn(session);
        when(session.isOpen()).thenReturn(true);

        keyRotationService.handleClientUnicastRotation(
                10L, 1L, "user1", 2L, "cipher".getBytes(StandardCharsets.UTF_8));

        // The contract is to enqueue even for online recipients. Whether and
        // when a delivery worker is triggered belongs to the backend layer.
        verify(userQueue).enqueueForUser(eq(2L), any(byte[].class));
        verify(keyDeliveryService).sendNextQueuedPacket(2L, "user2", session);
        verify(dispatcher, never()).sendTo(any(), any());
    }

    @Test
    void testHandleClientUnicastRotationOffline() throws Exception {
        stubValidUnicastRotation();

        keyRotationService.handleClientUnicastRotation(
                10L, 1L, "user1", 2L, "cipher".getBytes(StandardCharsets.UTF_8));

        verify(userQueue).enqueueForUser(eq(2L), any(byte[].class));
        verifyNoInteractions(keyDeliveryService);
        verify(dispatcher, never()).sendTo(any(), any());
    }

    @Test
    void rejectsUnicastForRecipientOutsideChannelWithoutEnqueuing() {
        when(channelMemberRepository.existsByChannelIdAndUserId(10L, 1L)).thenReturn(true);
        when(channelMemberRepository.existsByChannelIdAndUserId(10L, 2L)).thenReturn(false);

        assertThrows(SecurityException.class, () -> keyRotationService.handleClientUnicastRotation(
                10L, 1L, "user1", 2L, "cipher".getBytes(StandardCharsets.UTF_8)));
        verifyNoInteractions(userQueue);
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
