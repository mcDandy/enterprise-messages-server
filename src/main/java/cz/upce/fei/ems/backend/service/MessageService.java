package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final AppUserRepository appUserRepository;
    private final KeyRotationService keyRotationService;

    @Transactional
    public Message processAndSaveMessage(String senderUsername, Long channelId, Long channelKeyId, byte[] ciphertext) {
        if (keyRotationService.isChannelLocked(channelId)) {
            throw new IllegalStateException("Channel is temporarily locked due to ongoing key rotation.");
        }

        AppUser sender = appUserRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database: " + senderUsername));

        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("Empty message payload.");
        }

        Message message = new Message();
        message.setChannelId(channelId);
        message.setSenderId(sender.getId());
        message.setCiphertext(ciphertext);
        message.setChannelKeyId(channelKeyId);

        return messageRepository.save(message);
    }
}