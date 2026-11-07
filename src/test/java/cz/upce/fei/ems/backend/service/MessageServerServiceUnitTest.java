package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.MessageServerRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServerServiceUnitTest {

    @Mock
    private MessageServerRepository serverRepository;
    @Mock
    private ServerMemberRepository serverMemberRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelDeletionService channelDeletionService;

    @InjectMocks
    private MessageServerService messageServerService;

    private AppUser user;
    private MessageServer server;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setId(1L);
        user.setUsername("testuser");

        server = new MessageServer();
        server.setId(10L);
        server.setName("test-server");
    }

    @Test
    void testCreateServerSuccess() {
        CreateServerRequestDto request = new CreateServerRequestDto();
        request.setName("test-server");
        request.setType(MessageServerType.ORGANIZATION);
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(serverRepository.save(any())).thenReturn(server);

        var created = messageServerService.createServer(request, "testuser");

        assertNotNull(created);
        verify(serverRepository).save(any());
        verify(serverMemberRepository).save(argThat(member ->
                member.getRole() == MessageServerRole.ADMINISTRATOR &&
                member.getUserId().equals(1L)
        ));
    }

    @Test
    void testIsUserAdminTrue() {
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        MessageServerMember member = new MessageServerMember();
        member.setRole(MessageServerRole.ADMINISTRATOR);
        when(serverMemberRepository.findByServerIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));

        assertTrue(messageServerService.isUserAdmin(10L, "testuser"));
    }

    @Test
    void testIsUserAdminFalse() {
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        MessageServerMember member = new MessageServerMember();
        member.setRole(MessageServerRole.MEMBER);
        when(serverMemberRepository.findByServerIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));

        assertFalse(messageServerService.isUserAdmin(10L, "testuser"));
    }

    @Test
    void testDeleteServerForbidden() {
        when(serverRepository.findById(10L)).thenReturn(Optional.of(server));
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(serverMemberRepository.findByServerIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(SecurityException.class, () -> messageServerService.deleteServer(10L, "testuser"));
    }

    @Test
    void testDeleteServerNotFoundIsNoOp() {
        when(serverRepository.findById(10L)).thenReturn(Optional.empty());

        assertFalse(messageServerService.deleteServer(10L, "testuser"));
        verifyNoInteractions(channelRepository, channelDeletionService);
    }
}
