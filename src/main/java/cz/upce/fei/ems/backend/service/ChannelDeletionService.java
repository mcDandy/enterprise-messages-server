package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelDeletionService {

    private final MessageRepository messageRepository;
    private final ChannelKeyRepository channelKeyRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;

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
