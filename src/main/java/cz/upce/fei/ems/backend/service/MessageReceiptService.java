package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.MessageReceiptRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageReceiptService {

    private final MessageReceiptRepository messageReceiptRepository;
    private final AppUserRepository appUserRepository;
    private final MessageRepository messageRepository;
    private final ChannelMemberRepository channelMemberRepository;

    @Transactional
    public Long markAsDelivered(Long messageId, String username) {
        ReceiptContext context = validateReceipt(messageId, username);

        messageReceiptRepository.upsertDelivered(
                context.channelId(),
                context.userId(),
                messageId
        );

        return context.channelId();
    }

    @Transactional
    public Long markAsRead(Long messageId, String username) {
        ReceiptContext context = validateReceipt(messageId, username);

        messageReceiptRepository.upsertRead(
                context.channelId(),
                context.userId(),
                messageId
        );

        return context.channelId();
    }

    private ReceiptContext validateReceipt(Long messageId, String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalStateException("User not found: " + username)
                );

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Message not found: " + messageId)
                );

        Long channelId = message.getChannelId();

        boolean isMember = channelMemberRepository
                .existsByChannelIdAndUserId(channelId, user.getId());

        if (!isMember) {
            throw new SecurityException("User is not a member of this channel.");
        }

        return new ReceiptContext(channelId, user.getId());
    }

    private record ReceiptContext(Long channelId, Long userId) {
    }
}