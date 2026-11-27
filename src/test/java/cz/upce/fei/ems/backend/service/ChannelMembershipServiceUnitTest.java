package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.ChannelMemberId;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelMembershipServiceUnitTest {

    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelMemberRepository channelMemberRepository;
    @Mock
    private ChannelMemberProgressService channelMemberProgressService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private MessageServerService serverService;
    @Mock
    private ServerMemberRepository serverMemberRepository;
    @Mock
    private KeyRotationService keyRotationService;

    @InjectMocks
    private ChannelMembershipService channelMembershipService;

    private Channel channel;

    @BeforeEach
    void setUp() {
        channel = new Channel();
        channel.setId(1L);
        channel.setServerId(10L);
    }

    @Test
    void testAddMemberSuccess() {
        AppUser target = new AppUser();
        target.setId(2L);
        target.setUsername("target");
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(10L, "admin")).thenReturn(true);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(target));
        when(channelMemberRepository.existsByChannelIdAndUserId(1L, 2L)).thenReturn(false);
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 2L)).thenReturn(true);
        ChannelMember existingMember = new ChannelMember();
        existingMember.setUserId(1L);
        ChannelMember addedMember = new ChannelMember();
        addedMember.setUserId(2L);
        when(channelMemberRepository.findByChannelId(1L)).thenReturn(List.of(existingMember, addedMember));
        AppUser existingUser = new AppUser();
        existingUser.setUsername("existing");
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(target));

        channelMembershipService.addMemberAndTriggerKeyRotation(1L, 2L, "admin");

        verify(channelMemberRepository).save(any());
        verify(keyRotationService).solicitateNewKeyRotation(1L, Set.of("existing", "target"));
    }

    @Test
    void testAddMemberForbidden() {
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(10L, "user")).thenReturn(false);

        assertThrows(SecurityException.class, () -> channelMembershipService.addMemberToChannel(1L, 2L, "user"));
    }

    @Test
    void testRemoveMemberSuccess() {
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(10L, "admin")).thenReturn(true);
        ChannelMember member = new ChannelMember();
        when(channelMemberRepository.findById(any(ChannelMemberId.class))).thenReturn(Optional.of(member));
        ChannelMember remainingMember = new ChannelMember();
        remainingMember.setUserId(3L);
        when(channelMemberRepository.findByChannelId(1L)).thenReturn(List.of(remainingMember));
        AppUser remainingUser = new AppUser();
        remainingUser.setUsername("remaining");
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(remainingUser));

        channelMembershipService.removeMemberFromChannelAndTriggerKeyRotation(1L, 2L, "admin");

        verify(channelMemberRepository).delete(member);
        verifyNoInteractions(keyRotationService);
    }

    @Test
    void removingMemberRotatesKeyWhenAtLeastTwoMembersRemain() {
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(10L, "admin")).thenReturn(true);
        ChannelMember removedMember = new ChannelMember();
        when(channelMemberRepository.findById(any(ChannelMemberId.class))).thenReturn(Optional.of(removedMember));

        ChannelMember remainingMember1 = new ChannelMember();
        remainingMember1.setUserId(3L);
        ChannelMember remainingMember2 = new ChannelMember();
        remainingMember2.setUserId(4L);
        when(channelMemberRepository.findByChannelId(1L)).thenReturn(List.of(remainingMember1, remainingMember2));

        AppUser remainingUser1 = new AppUser();
        remainingUser1.setUsername("remaining-1");
        AppUser remainingUser2 = new AppUser();
        remainingUser2.setUsername("remaining-2");
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(remainingUser1));
        when(appUserRepository.findById(4L)).thenReturn(Optional.of(remainingUser2));

        channelMembershipService.removeMemberFromChannelAndTriggerKeyRotation(1L, 2L, "admin");

        verify(keyRotationService).solicitateNewKeyRotation(1L, Set.of("remaining-1", "remaining-2"));
    }

    @Test
    void markAsReadAdvancesProgressForAuthenticatedMember() {
        channelMembershipService.markAsRead(10L, 2L);

        verify(channelMemberProgressService).markAsRead(10L, 2L);
    }

    @Test
    void markAsReadRejectsNonMember() {
        channelMembershipService.markAsRead(10L, 2L);

        verify(channelMemberProgressService).markAsRead(10L, 2L);
    }
}
