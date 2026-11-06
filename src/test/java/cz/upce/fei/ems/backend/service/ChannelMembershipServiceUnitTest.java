package cz.upce.fei.ems.backend.service;

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
    private AppUserRepository appUserRepository;
    @Mock
    private MessageServerService serverService;
    @Mock
    private KeyRotationService keyRotationService;
    @Mock
    private ServerMemberRepository serverMemberRepository;

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
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(10L, "admin")).thenReturn(true);
        when(appUserRepository.existsById(2L)).thenReturn(true);
        when(channelMemberRepository.existsByChannelIdAndUserId(1L, 2L)).thenReturn(false);
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 2L)).thenReturn(true);

        channelMembershipService.addMemberToChannel(1L, 2L, "admin");

        verify(channelMemberRepository).save(any());
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

        channelMembershipService.removeMemberFromChannel(1L, 2L, "admin");

        verify(channelMemberRepository).delete(member);
    }
}
