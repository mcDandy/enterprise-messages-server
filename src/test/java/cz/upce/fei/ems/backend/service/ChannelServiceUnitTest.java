package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.UpdateChannelRequestDto;
import cz.upce.fei.ems.backend.repository.*;
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
class ChannelServiceUnitTest {

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
    private MessageServerRepository messageServerRepository;

    @InjectMocks
    private ChannelService channelService;

    private AppUser user;
    private Channel channel;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setId(1L);
        user.setUsername("testuser");

        channel = new Channel();
        channel.setId(10L);
        channel.setServerId(100L);
        channel.setName("general");
    }

    @Test
    void testGetChannelById() {
        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));
        Channel found = channelService.getChannelById(10L);
        assertNotNull(found);
        assertEquals("general", found.getName());
    }

    @Test
    void testCreateChannelSuccess() {
        CreateChannelRequestDto request = new CreateChannelRequestDto();
        request.setName("new-channel");

        when(serverService.isUserAdmin(100L, "testuser")).thenReturn(true);
        when(channelRepository.existsByServerIdAndName(100L, "new-channel")).thenReturn(false);
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(channelRepository.save(any())).thenReturn(channel);

        Channel created = channelService.createChannel(100L, request, "testuser");

        assertNotNull(created);
        verify(channelRepository).save(any());
        verify(channelMemberRepository).save(any());
        verify(keyRotationService).solicitateNewKeyRotation(any(), any());
    }

    @Test
    void testCreateChannelForbidden() {
        CreateChannelRequestDto request = new CreateChannelRequestDto();
        when(serverService.isUserAdmin(100L, "testuser")).thenReturn(false);
        assertThrows(SecurityException.class, () -> channelService.createChannel(100L, request, "testuser"));
    }

    @Test
    void testUpdateChannelSuccess() {
        UpdateChannelRequestDto request = new UpdateChannelRequestDto();
        request.setName("updated-name");

        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(100L, "testuser")).thenReturn(true);
        when(channelRepository.save(any())).thenReturn(channel);

        Channel updated = channelService.updateChannel(10L, request, "testuser");

        assertNotNull(updated);
        assertEquals("updated-name", channel.getName());
    }

    @Test
    void testDeleteChannelSuccess() {
        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(100L, "testuser")).thenReturn(true);

        channelService.deleteChannel(10L, "testuser");

        verify(channelRepository).delete(channel);
    }
}
