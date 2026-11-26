package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.dto.MessageResponseDto;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final AppUserRepository appUserRepository;
    private final ChannelRepository channelRepository;
    private final ChannelKeyRepository channelKeyRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final PostgresUserQueue userQueue;
    private final PacketCodec packetCodec;
    private final SessionRegistry sessionRegistry;
    private final QueuedKeyDeliveryService queuedPacketDeliveryService;

    public List<MessageResponseDto> getRecentMessages(Long channelId) {
        requireChannelExists(channelId);
        return messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channelId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Slice<MessageResponseDto> getMessagesPage(Long channelId, int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }

        requireChannelExists(channelId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return messageRepository.findByChannelId(channelId, pageable).map(this::toResponse);
    }

    private void requireChannelExists(Long channelId) {
        if (!channelRepository.existsById(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found");
        }
    }

    private MessageResponseDto toResponse(Message message) {
        UUID keyVersion = channelKeyRepository.findById(message.getChannelKeyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing key version for message " + message.getId()))
                .getVersion();
        return MessageResponseDto.fromEntity(message, keyVersion);
    }

    @Transactional
    public void processAndQueueMessage(
            String senderUsername,
            Long channelId,
            Long channelKeyId,
            byte[] ciphertext,
            Packet packet
    ) {
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

        messageRepository.saveAndFlush(message);

        ByteBuffer encodedFrame = packetCodec.encode(packet);
        byte[] frame = new byte[encodedFrame.remaining()];
        encodedFrame.get(frame);

        Set<Long> recipientIds = channelMemberRepository.findByChannelId(channelId).stream()
                .map(ChannelMember::getUserId)
                .filter(userId -> !sender.getId().equals(userId))
                .collect(Collectors.toSet());
        recipientIds.forEach(userId -> {
            long queueMessageId = userQueue.enqueueMessageForUser(userId, message.getId(), frame);
            log.debug(
                    "Message packet entered user queue: userId={}, messageId={}, frameLength={} bytes, frame(hex)={}",
                    userId, queueMessageId, frame.length, HexFormat.of().formatHex(frame)
            );
        });

        List<AppUser> recipients = new ArrayList<>(appUserRepository.findAllById(recipientIds));
        dispatchAfterCommit(recipients);
    }

    private void dispatchAfterCommit(List<AppUser> recipients) {
        Runnable dispatch = () -> {
            for (AppUser recipient : recipients) {
                WebSocketSession session = sessionRegistry.getSession(recipient.getUsername());
                if (session == null || !session.isOpen()) {
                    continue;
                }
                try {
                    queuedPacketDeliveryService.sendNextQueuedPacket(
                            recipient.getId(), recipient.getUsername(), session);
                } catch (Exception e) {
                    log.warn("Could not dispatch queued packet to user {}; retained for retry",
                            recipient.getId(), e);
                }
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }
}
