package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.MessageReceiptRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelDeletionService {

    private final MessageReceiptRepository messageReceiptRepository;
    private final MessageRepository messageRepository;
    private final ChannelKeyRepository channelKeyRepository;
    private final ChannelRepository channelRepository;

    @Transactional
    public void deleteChannel(Channel channel) {
        Long channelId = channel.getId();
        messageReceiptRepository.deleteByMessageChannelId(channelId);
        messageRepository.deleteByChannelId(channelId);
        channelKeyRepository.deleteByChannelId(channelId);
        channelRepository.delete(channel);
    }
}
