package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelMembershipService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final AppUserRepository appUserRepository;
    private final MessageServerService serverService;
    private final KeyRotationService keyRotationService;
    private final ServerMemberRepository serverMemberRepository;


    @Transactional
    public void addMemberToChannel(Long channelId, Long targetUserId, String adminUsername) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found"));

        if (!serverService.isUserAdmin(channel.getServerId(), adminUsername)) {
            throw new SecurityException("Only server administrators can manage channel memberships.");
        }

        if (!appUserRepository.existsById(targetUserId)) {
            throw new IllegalArgumentException("Target user with ID " + targetUserId + " not found");
        }

        if (channelMemberRepository.existsByChannelIdAndUserId(channelId, targetUserId)) {
            throw new IllegalStateException("User is already a member of this channel.");
        }

        if (!serverMemberRepository.existsByServerIdAndUserId(channel.getServerId(), targetUserId)) {
            MessageServerMember serverMember = new MessageServerMember();
            serverMember.setServerId(channel.getServerId());
            serverMember.setUserId(targetUserId);
            serverMember.setRole(MessageServerRole.MEMBER);
            serverMemberRepository.save(serverMember);
        }

        ChannelMember member = new ChannelMember();
        member.setChannelId(channelId);
        member.setUserId(targetUserId);

        channelMemberRepository.save(member);
        channelMemberRepository.flush();
    }

    @Transactional
    public void removeMemberFromChannel(Long channelId, Long targetUserId, String adminUsername) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found"));

        if (!serverService.isUserAdmin(channel.getServerId(), adminUsername)) {
            throw new SecurityException("Only server administrators can manage channel memberships.");
        }

        ChannelMemberId memberId = new ChannelMemberId(channelId, targetUserId);
        ChannelMember member = channelMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Channel member not found"));

        channelMemberRepository.delete(member);
        channelMemberRepository.flush();
    }

    @Transactional
    public void triggerKeyRotation(Long channelId) {
        try {
            Set<String> currentMembers = channelMemberRepository.findByChannelId(channelId).stream()
                    .map(member -> appUserRepository.findById(member.getUserId()).orElse(null))
                    .filter(Objects::nonNull)
                    .map(AppUser::getUsername)
                    .collect(Collectors.toSet());

            if (!currentMembers.isEmpty()) {
                keyRotationService.solicitateNewKeyRotation(channelId, currentMembers);
            }
        } catch (Exception e) {
            System.err.println("Failed to trigger key rotation for channel " + channelId + ": " + e.getMessage());
        }
    }
}