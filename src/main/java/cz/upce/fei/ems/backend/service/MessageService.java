package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.dto.MessageResponseDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final AppUserRepository appUserRepository;
    private final ChannelKeyRepository channelKeyRepository;

    public List<MessageResponseDto> getRecentMessages(Long channelId) {
        return messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channelId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Slice<MessageResponseDto> getMessagesPage(Long channelId, Pageable pageable) {
        return messageRepository.findByChannelId(channelId, pageable).map(this::toResponse);
    }

    private MessageResponseDto toResponse(Message message) {
        UUID keyVersion = channelKeyRepository.findById(message.getChannelKeyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing key version for message " + message.getId()))
                .getVersion();
        return MessageResponseDto.fromEntity(message, keyVersion);
    }

    @Transactional
    public void processAndSaveMessage(String senderUsername, Long channelId, Long channelKeyId, byte[] ciphertext) {
        AppUser sender = appUserRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + senderUsername));

        if (channelKeyId == null) {
            throw new IllegalStateException(
                    "Channel " + channelId + " has no active encryption key yet. Wait for key rotation to complete."
            );
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
