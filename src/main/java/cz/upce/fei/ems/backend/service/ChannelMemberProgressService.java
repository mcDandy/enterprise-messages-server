package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.ChannelMemberId;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelMemberProgressService {

    private final MessageRepository messageRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelMemberInfoService channelMemberInfoService;

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
