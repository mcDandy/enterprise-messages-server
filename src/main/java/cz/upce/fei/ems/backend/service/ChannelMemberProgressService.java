package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.ChannelMemberId;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Advances delivered and read message positions for channel memberships.
 */

@Service
@RequiredArgsConstructor
public class ChannelMemberProgressService {

    private final MessageRepository messageRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelMemberInfoService channelMemberInfoService;

    /**
     * Advances a member’s delivered position when the queue acknowledges a chat packet.
     * The database update is monotonic, so stale or duplicate acknowledgements do not move it backwards.
     *
     * @param messageId application message ID associated with the acknowledged queue item
     * @param userId recipient account ID
     * @return {@code true} if a membership progress row was advanced; {@code false} if the message,
     *         membership, or a newer delivered position makes the update inapplicable
     */
    @Transactional
    public boolean markAsDelivered(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            return false;
        }
        Long channelId = message.getChannelId();
        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            return false;
        }
        int updated = channelMemberRepository.advanceDelivered(channelId, userId, messageId);
        if (updated == 0) {
            return false;
        }
        publishProgress(channelId, userId);
        return true;
    }

    /**
     * Advances a member’s read position and schedules a progress packet after the transaction commits.
     *
     * @param messageId ID of the message the member has read
     * @param userId authenticated reader account ID
     * @throws IllegalArgumentException when the message does not exist
     * @throws SecurityException when the user is not a member of the message’s channel
     */
    @Transactional
    public void markAsRead(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        Long channelId = message.getChannelId();
        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            throw new SecurityException("User is not a member of this channel.");
        }
        int updated = channelMemberRepository.advanceRead(channelId, userId, messageId);
        if (updated == 0) {
            throw new SecurityException("User is not a member of this channel.");
        }
        publishProgress(channelId, userId);
    }

    private void publishProgress(Long channelId, Long userId) {
        ChannelMember member = channelMemberRepository.findById(new ChannelMemberId(channelId, userId))
                .orElseThrow(() -> new IllegalStateException("Channel membership disappeared while updating progress."));
        channelMemberInfoService.publishToChannelMembers(member);
    }
}
