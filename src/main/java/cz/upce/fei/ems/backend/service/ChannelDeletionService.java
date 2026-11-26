package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes channel-owned records in dependency order before removing the channel.
 */

@Service
@RequiredArgsConstructor
public class ChannelDeletionService {

    private final MessageRepository messageRepository;
    private final ChannelKeyRepository channelKeyRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;

    /**
     * Removes messages, keys, memberships, and finally the channel within one transaction.
     * Child rows are deleted first to satisfy their foreign-key dependencies.
     *
     * @param channel channel entity to remove
     */
    @Transactional
    public void deleteChannel(Channel channel) {
        Long channelId = channel.getId();
        // Receipt progress now lives on the membership row and references messages.
        channelMemberRepository.deleteByChannelId(channelId);
        messageRepository.deleteByChannelId(channelId);
        channelKeyRepository.deleteByChannelId(channelId);
        channelRepository.delete(channel);
    }
}
