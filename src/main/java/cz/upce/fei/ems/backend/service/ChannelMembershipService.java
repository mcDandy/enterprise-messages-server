package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.dto.ChannelKeyEnvelopeDto;
import cz.upce.fei.ems.backend.dto.ChannelMemberResponseDto;
import cz.upce.fei.ems.backend.net.protocol.PacketValidation;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private Channel findAuthorizedChannel(Long channelId, String adminUsername) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found."));
        if (!serverService.isUserAdmin(channel.getServerId(), adminUsername)) {
            throw new SecurityException("Only server administrators can manage channel memberships.");
        }
        return channel;
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
     * Adds the single user identified by the new envelope recipient and distributes a new key
     * to exactly the resulting channel membership.
     */
    @Transactional
    public void addMemberWithKeyEnvelopes(
            Long channelId, List<ChannelKeyEnvelopeDto> envelopes, String adminUsername) {
        Channel channel = findAuthorizedChannel(channelId, adminUsername);
        Set<Long> currentMemberIds = channelMemberRepository.findByChannelId(channelId).stream()
                .map(ChannelMember::getUserId)
                .collect(Collectors.toSet());
        Map<Long, byte[]> ciphertexts = decodeEnvelopes(envelopes);
        Set<Long> envelopeUserIds = ciphertexts.keySet();

        if (!envelopeUserIds.containsAll(currentMemberIds)) {
            throw new IllegalArgumentException("Key envelopes must include every current channel member.");
        }
        Set<Long> addedUserIds = new java.util.HashSet<>(envelopeUserIds);
        addedUserIds.removeAll(currentMemberIds);
        if (addedUserIds.size() != 1 || envelopeUserIds.size() != currentMemberIds.size() + 1) {
            throw new IllegalArgumentException(
                    "The key envelope recipients must be all current members and exactly one new user.");
        }

        Long targetUserId = addedUserIds.iterator().next();
        appUserRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user with ID " + targetUserId + " not found"));
        ensureServerMembership(channel, targetUserId);
        saveChannelMembership(channelId, targetUserId);

        if (envelopeUserIds.size() > 1) {
            distributeKeyForMembershipChange(channelId, adminUsername, ciphertexts);
        }
    }

    /**
     * Removes a channel membership after validating that envelopes match every remaining member.
     * A missing membership is treated as an idempotent no-op; the channel must exist and the actor
     * must be a server administrator.
     *
     * @param channelId channel containing the membership
     * @param targetUserId account to remove
     * @param adminUsername actor whose server role is checked
     */
    @Transactional
    public void removeMemberWithKeyEnvelopes(
            Long channelId, Long targetUserId, List<ChannelKeyEnvelopeDto> envelopes, String adminUsername) {
        findAuthorizedChannel(channelId, adminUsername);
        List<ChannelMember> currentMembers = channelMemberRepository.findByChannelId(channelId);
        Set<Long> currentMemberIds = currentMembers.stream()
                .map(ChannelMember::getUserId)
                .collect(Collectors.toSet());
        Set<Long> expectedRecipientIds = new java.util.HashSet<>(currentMemberIds);
        expectedRecipientIds.remove(targetUserId);
        Map<Long, byte[]> ciphertexts = decodeEnvelopes(envelopes);

        if (!ciphertexts.keySet().equals(expectedRecipientIds)) {
            throw new IllegalArgumentException(
                    "Key envelopes must match exactly the channel members remaining after removal.");
        }

        ChannelMember target = currentMembers.stream()
                .filter(member -> member.getUserId().equals(targetUserId))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return;
        }

        channelMemberRepository.delete(target);
        channelMemberRepository.flush();

        if (expectedRecipientIds.size() > 1) {
            distributeKeyForMembershipChange(channelId, adminUsername, ciphertexts);
        }
    }

    private Map<Long, byte[]> decodeEnvelopes(List<ChannelKeyEnvelopeDto> envelopes) {
        if (envelopes == null) {
            throw new IllegalArgumentException("Key envelope list is required.");
        }

        Map<Long, byte[]> ciphertexts = new LinkedHashMap<>();
        for (ChannelKeyEnvelopeDto envelope : envelopes) {
            if (envelope == null || envelope.getUserId() == null || envelope.getUserId() <= 0) {
                throw new IllegalArgumentException("Every key envelope must have a positive user ID.");
            }
            if (envelope.getCiphertext() == null || envelope.getCiphertext().isBlank()) {
                throw new IllegalArgumentException("Every key envelope must contain Base64 ciphertext.");
            }

            byte[] ciphertext;
            try {
                ciphertext = Base64.getDecoder().decode(envelope.getCiphertext());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Key envelope ciphertext must use standard Base64.", exception);
            }
            if (ciphertext.length == 0
                    || ciphertext.length > PacketValidation.MAX_PAYLOAD_BYTES - 3 * Long.BYTES
                    || !Base64.getEncoder().encodeToString(ciphertext).equals(envelope.getCiphertext())) {
                throw new IllegalArgumentException("Key envelope ciphertext has an invalid size or Base64 format.");
            }
            if (ciphertexts.putIfAbsent(envelope.getUserId(), ciphertext) != null) {
                throw new IllegalArgumentException("Duplicate key envelope recipient ID.");
            }
        }
        return ciphertexts;
    }

    private void distributeKeyForMembershipChange(
            Long channelId, String adminUsername, Map<Long, byte[]> ciphertexts) {
        AppUser actor = appUserRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated administrator was not found."));
        keyRotationService.rotateAndDistributeKey(channelId, actor.getId(), adminUsername, ciphertexts);
    }
}
