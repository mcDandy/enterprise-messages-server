package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final AppUserRepository appUserRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final NotificationService notificationService;

    @Transactional
    public void processAndSaveMessage(String senderUsername, Long channelId, Long channelKeyId, byte[] ciphertext) {
        AppUser sender = appUserRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + senderUsername));

        Message message = new Message();
        message.setChannelId(channelId);
        message.setChannelKeyId(channelKeyId);
        message.setSenderId(sender.getId());
        message.setCiphertext(ciphertext);
        message.setSentAt(OffsetDateTime.now());

       messageRepository.save(message);

        triggerNotificationsForChannel(channelId, senderUsername);

    }

    private void triggerNotificationsForChannel(Long channelId, String senderUsername) {
        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);

        for (ChannelMember member : members) {
            AppUser recipient = appUserRepository.findById(member.getUserId()).orElse(null);

            if (recipient != null && !recipient.getUsername().equals(senderUsername)) {
                notificationService.sendNewMessageNotification(
                        recipient.getFcmToken(), "Channel #" + channelId
                );
            }
        }
    }
}