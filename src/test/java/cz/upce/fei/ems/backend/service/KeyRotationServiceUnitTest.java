package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
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
    private ChannelKeyPendingRepository channelKeyPendingRepository;
    @Mock
    private ChannelRotationPendingRepository channelRotationPendingRepository;

    @InjectMocks
    private KeyRotationService keyRotationService;

    @Mock
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testSolicitateNewKeyRotationOnline() throws Exception {
        Set<String> members = Set.of("user1");
        when(sessionRegistry.getSession("user1")).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", "user1");
        when(session.getAttributes()).thenReturn(attributes);
        when(channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(10L)).thenReturn(Optional.empty());

        keyRotationService.solicitateNewKeyRotation(10L, members);

        verify(channelKeyRepository).save(any(ChannelKey.class));
        verify(dispatcher).sendTo(eq(session), any());
    }

    @Test
    void testSolicitateNewKeyRotationOffline() throws Exception {
        Set<String> members = Set.of("user1");
        when(sessionRegistry.getSession("user1")).thenReturn(null);

        keyRotationService.solicitateNewKeyRotation(10L, members);

        verify(channelKeyRepository, never()).save(any(ChannelKey.class));
        verify(dispatcher, never()).sendTo(any(), any());
    }

    @Test
    void testHandleClientUnicastRotationOnline() throws Exception {
        when(channelMemberRepository.existsByChannelIdAndUserId(10L, 1L)).thenReturn(true);
        when(channelMemberRepository.existsByChannelIdAndUserId(10L, 2L)).thenReturn(true);
        ChannelKey key = new ChannelKey();
        key.setVersion(UUID.randomUUID());
        when(channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(10L)).thenReturn(Optional.of(key));
        AppUser recipient = new AppUser();
        recipient.setUsername("user2");
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(sessionRegistry.getSession("user2")).thenReturn(session);
        when(session.isOpen()).thenReturn(true);

        keyRotationService.handleClientUnicastRotation(10L, 1L, "user1", 2L, "cipher".getBytes());

        verify(dispatcher).sendTo(eq(session), any());
        verify(channelKeyPendingRepository, never()).save(any());
    }

    @Test
    void testHandleClientUnicastRotationOffline() throws Exception {
        when(channelMemberRepository.existsByChannelIdAndUserId(10L, 1L)).thenReturn(true);
        when(channelMemberRepository.existsByChannelIdAndUserId(10L, 2L)).thenReturn(true);
        ChannelKey key = new ChannelKey();
        key.setVersion(UUID.randomUUID());
        when(channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(10L)).thenReturn(Optional.of(key));
        AppUser recipient = new AppUser();
        recipient.setUsername("user2");
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(sessionRegistry.getSession("user2")).thenReturn(null);

        keyRotationService.handleClientUnicastRotation(10L, 1L, "user1", 2L, "cipher".getBytes());

        verify(dispatcher, never()).sendTo(any(), any());
        verify(channelKeyPendingRepository).save(any(ChannelKeyPending.class));
    }
}
