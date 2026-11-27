package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.dto.ChannelMemberResponseDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements channel membership operations and delegates receipt-progress updates.
 */

@Service
@RequiredArgsConstructor
public class ChannelMembershipService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelMemberProgressService channelMemberProgressService;
    private final AppUserRepository appUserRepository;
    private final MessageServerService serverService;
    private final KeyRotationService keyRotationService;
    private final ServerMemberRepository serverMemberRepository;

    /**
     * Advances delivery progress for a message acknowledgement.
     *
     * @param messageId acknowledged application message ID
     * @param userId recipient account ID
     */
    @Transactional
    public void markAsDelivered(Long messageId, Long userId) {
        channelMemberProgressService.markAsDelivered(messageId, userId);
    }

    /**
     * Advances read progress and publishes the changed member state after commit.
     *
     * @param messageId ID of the message that was read
     * @param userId authenticated reader account ID
     */
    @Transactional
    public void markAsRead(Long messageId, Long userId) {
        channelMemberProgressService.markAsRead(messageId, userId);
    }

    /**
     * Lists channel members with their current delivery and read positions.
     *
     * @param channelId channel whose memberships are requested
     * @return current member state for every membership in the channel
     */
    public List<ChannelMemberResponseDto> getChannelMembers(Long channelId) {
        return channelMemberRepository.findByChannelId(channelId).stream()
                .map(ChannelMemberResponseDto::fromEntity)
                .toList();
    }

    /**
     * Adds a user to a channel after checking server-admin authority and server membership.
     * If needed, the target is first added to the parent server with the default member role.
     *
     * @param channelId destination channel
     * @param targetUserId account to add
     * @param adminUsername actor whose server role is checked
     */
    @Transactional
    public void addMemberToChannel(Long channelId, Long targetUserId, String adminUsername) {
        Channel channel = findAuthorizedChannel(channelId, adminUsername);
        validateTargetMembership(channelId, targetUserId);
        ensureServerMembership(channel, targetUserId);
        saveChannelMembership(channelId, targetUserId);
    }

    private Channel findAuthorizedChannel(Long channelId, String adminUsername) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found"));
        if (!serverService.isUserAdmin(channel.getServerId(), adminUsername)) {
            throw new SecurityException("Only server administrators can manage channel memberships.");
        }
        return channel;
    }

    private void validateTargetMembership(Long channelId, Long targetUserId) {
        appUserRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user with ID " + targetUserId + " not found"));
        if (channelMemberRepository.existsByChannelIdAndUserId(channelId, targetUserId)) {
            throw new IllegalStateException("User is already a member of this channel.");
        }
    }

    private void ensureServerMembership(Channel channel, Long targetUserId) {
        if (serverMemberRepository.existsByServerIdAndUserId(channel.getServerId(), targetUserId)) {
            return;
        }
        MessageServerMember serverMember = new MessageServerMember();
        serverMember.setServerId(channel.getServerId());
        serverMember.setUserId(targetUserId);
        serverMember.setRole(MessageServerRole.MEMBER);
        serverMemberRepository.save(serverMember);
    }

    private void saveChannelMembership(Long channelId, Long targetUserId) {
        ChannelMember member = new ChannelMember();
        member.setChannelId(channelId);
        member.setUserId(targetUserId);
        channelMemberRepository.save(member);
        channelMemberRepository.flush();
    }

    /**
     * Adds a channel member and requests a new channel-key rotation in the same transaction.
     *
     * @param channelId destination channel
     * @param targetUserId account to add
     * @param adminUsername actor whose server role is checked
     */
    @Transactional
    public void addMemberAndTriggerKeyRotation(Long channelId, Long targetUserId, String adminUsername) {
        addMemberToChannel(channelId, targetUserId, adminUsername);
        triggerKeyRotation(channelId);
    }

    /**
     * Removes a channel membership if it exists; removal requires server-admin authority.
     * A missing channel or membership is treated as an idempotent no-op.
     *
     * @param channelId channel containing the membership
     * @param targetUserId account to remove
     * @param adminUsername actor whose server role is checked
     */
    @Transactional
    public void removeMemberFromChannelAndTriggerKeyRotation(Long channelId, Long targetUserId, String adminUsername) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            return;
        }

        ChannelMemberId memberId = new ChannelMemberId(channelId, targetUserId);
        ChannelMember member = channelMemberRepository.findById(memberId).orElse(null);
        if (member == null) {
            return;
        }

        if (!serverService.isUserAdmin(channel.getServerId(), adminUsername)) {
            throw new SecurityException("Only server administrators can manage channel memberships.");
        }

        channelMemberRepository.delete(member);
        channelMemberRepository.flush();

        triggerKeyRotation(channelId);
    }

    /**
     * Requests a new channel key and distributes its rotation packet to one selected member.
     *
     * @param channelId channel whose key should rotate
     */
    @Transactional
    public void triggerKeyRotation(Long channelId) {
        try {
            Set<String> currentMembers = channelMemberRepository.findByChannelId(channelId).stream()
                    .map(member -> appUserRepository.findById(member.getUserId()).orElse(null))
                    .filter(Objects::nonNull)
                    .map(AppUser::getUsername)
                    .collect(Collectors.toSet());

            if (currentMembers.size() > 1) {
                keyRotationService.solicitateNewKeyRotation(channelId, currentMembers);
            }
        } catch (Exception e) {
            System.err.println("Failed to trigger key rotation for channel " + channelId + ": " + e.getMessage());
        }
    }
}
