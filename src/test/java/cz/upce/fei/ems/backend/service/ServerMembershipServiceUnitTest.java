package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.MessageServerMember;
import cz.upce.fei.ems.backend.domain.MessageServerRole;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
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
class ServerMembershipServiceUnitTest {

    @Mock
    private ServerMemberRepository serverMemberRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private MessageServerService serverService;

    @InjectMocks
    private ServerMembershipService serverMembershipService;

    @Test
    void testAddMemberSuccess() {
        when(serverService.isUserAdmin(10L, "admin")).thenReturn(true);
        when(appUserRepository.existsById(2L)).thenReturn(true);
        when(serverMemberRepository.findByServerIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        serverMembershipService.addMemberToServer(10L, 2L, MessageServerRole.MEMBER, "admin");

        verify(serverMemberRepository).save(any());
    }

    @Test
    void testUpdateRoleSuccess() {
        AppUser admin = new AppUser();
        admin.setId(1L);
        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        MessageServerMember adminMember = new MessageServerMember();
        adminMember.setRole(MessageServerRole.ADMINISTRATOR);
        when(serverMemberRepository.findByServerIdAndUserId(10L, 1L)).thenReturn(Optional.of(adminMember));

        MessageServerMember targetMember = new MessageServerMember();
        when(serverMemberRepository.findByServerIdAndUserId(10L, 2L)).thenReturn(Optional.of(targetMember));

        serverMembershipService.updateMemberRole(10L, 2L, MessageServerRole.ADMINISTRATOR, "admin");

        assertEquals(MessageServerRole.ADMINISTRATOR, targetMember.getRole());
        verify(serverMemberRepository).save(targetMember);
    }
}
