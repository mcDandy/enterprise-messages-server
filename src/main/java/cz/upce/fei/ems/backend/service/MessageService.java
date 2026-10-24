package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final AppUserRepository appUserRepository;
    private final ChannelKeyRepository channelKeyRepository;
    private final ChannelMemberRepository channelMemberRepository;

    @Transactional
    public void processAndSaveMessage(String senderUsername, Long channelId, Long channelKeyId, byte[] ciphertext) {
        AppUser sender = appUserRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + senderUsername));

        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("Encrypted message payload is required.");
        }
        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, sender.getId())) {
            throw new IllegalArgumentException("Sender is not a member of the channel.");
        }
        // Never accept a stale key version after a member has been removed.
        if (channelKeyId == null || channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId)
                .filter(key -> key.getId().equals(channelKeyId)).isEmpty()) {
            throw new IllegalArgumentException("Current channel key version is required.");
        }

        Message message = new Message();
        message.setChannelId(channelId);
        message.setChannelKeyId(channelKeyId);
        message.setSenderId(sender.getId());
        message.setCiphertext(ciphertext);
        message.setSentAt(OffsetDateTime.now());

        messageRepository.save(message);

    }
}