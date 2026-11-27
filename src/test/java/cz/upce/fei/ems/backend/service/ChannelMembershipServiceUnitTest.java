package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.dto.ChannelKeyEnvelopeDto;
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

import java.util.List;
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
        when(serverMemberRepository.existsByServerIdAndUserId(10L, 2L)).thenReturn(true);
        ChannelMember existingMember = new ChannelMember();
        existingMember.setUserId(1L);
        when(channelMemberRepository.findByChannelId(1L)).thenReturn(List.of(existingMember));
        AppUser admin = new AppUser();
        admin.setId(99L);
        admin.setUsername("admin");
        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        channelMembershipService.addMemberWithKeyEnvelopes(
                1L, List.of(envelope(1L, "AQ=="), envelope(2L, "Ag==")), "admin");

        verify(channelMemberRepository).save(any());
        verify(keyRotationService).rotateAndDistributeKey(eq(1L), eq(99L), eq("admin"), anyMap());
    }

    @Test
    void testAddMemberForbidden() {
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(10L, "user")).thenReturn(false);

        assertThrows(SecurityException.class, () -> channelMembershipService.addMemberWithKeyEnvelopes(
                1L, List.of(envelope(2L, "Ag==")), "user"));
    }

    @Test
    void testRemoveMemberSuccess() {
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(10L, "admin")).thenReturn(true);
        ChannelMember member = new ChannelMember();
        member.setUserId(2L);
        ChannelMember remainingMembership = new ChannelMember();
        remainingMembership.setUserId(3L);
        when(channelMemberRepository.findByChannelId(1L)).thenReturn(List.of(member, remainingMembership));

        channelMembershipService.removeMemberWithKeyEnvelopes(
                1L, 2L, List.of(envelope(3L, "Aw==")), "admin");

        verify(channelMemberRepository).delete(member);
        verifyNoInteractions(keyRotationService);
    }

    @Test
    void removingMemberRotatesKeyWhenAtLeastTwoMembersRemain() {
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(10L, "admin")).thenReturn(true);
        ChannelMember removedMember = new ChannelMember();
        removedMember.setUserId(2L);
        ChannelMember remainingMember1 = new ChannelMember();
        remainingMember1.setUserId(3L);
        ChannelMember remainingMember2 = new ChannelMember();
        remainingMember2.setUserId(4L);
        when(channelMemberRepository.findByChannelId(1L))
                .thenReturn(List.of(removedMember, remainingMember1, remainingMember2));

        AppUser admin = new AppUser();
        admin.setId(99L);
        admin.setUsername("admin");
        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        channelMembershipService.removeMemberWithKeyEnvelopes(
                1L, 2L, List.of(envelope(3L, "Aw=="), envelope(4L, "BA==")), "admin");

        verify(keyRotationService).rotateAndDistributeKey(eq(1L), eq(99L), eq("admin"), anyMap());
    }

    @Test
    void addMemberRejectsWrongRecipientIdsEvenWhenEnvelopeCountIsCorrect() {
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(10L, "admin")).thenReturn(true);
        ChannelMember currentMember = new ChannelMember();
        currentMember.setUserId(1L);
        when(channelMemberRepository.findByChannelId(1L)).thenReturn(List.of(currentMember));

        assertThrows(IllegalArgumentException.class, () -> channelMembershipService.addMemberWithKeyEnvelopes(
                1L, List.of(envelope(2L, "Ag=="), envelope(3L, "Aw==")), "admin"));

        verify(channelMemberRepository, never()).save(any());
        verifyNoInteractions(keyRotationService);
    }

    @Test
    void rejectsNonBase64Ciphertext() {
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(serverService.isUserAdmin(10L, "admin")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> channelMembershipService.addMemberWithKeyEnvelopes(
                1L, List.of(envelope(1L, "not-base64!")), "admin"));
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

    private static ChannelKeyEnvelopeDto envelope(Long userId, String ciphertext) {
        ChannelKeyEnvelopeDto envelope = new ChannelKeyEnvelopeDto();
        envelope.setUserId(userId);
        envelope.setCiphertext(ciphertext);
        return envelope;
    }
}
