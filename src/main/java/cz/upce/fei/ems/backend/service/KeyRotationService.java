package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketValidation;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Coordinates channel-key rotation and queues key packets for channel members.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyRotationService {
    private final ChannelKeyRepository channelKeyRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final AppUserRepository appUserRepository;
    private final PostgresUserQueue userQueue;
    private final SessionRegistry sessionRegistry;
    private final PacketCodec packetCodec;
    private final QueuedKeyDeliveryService keyDeliveryService;

    private byte[] encodeFrame(Packet packet) {
        ByteBuffer encoded = packetCodec.encode(packet);
        byte[] frame = new byte[encoded.remaining()];
        encoded.get(frame);
        return frame;
    }

    private ChannelKey createNextChannelKey(Long channelId) {
        channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId).ifPresent(channelKey -> {
            channelKey.setInvalidatedAt(OffsetDateTime.now());
            channelKeyRepository.save(channelKey);
            channelKeyRepository.flush();
        });

        UUID version = UUID.randomUUID();
        ChannelKey channelKey = new ChannelKey();
        channelKey.setChannelId(channelId);
        channelKey.setVersion(version);
        return channelKeyRepository.save(channelKey);
    }

    /**
     * Creates a new active key version and queues its recipient-specific ciphertext for every
     * current channel member. The envelopes must already have been validated against membership.
     */
    @Transactional
    public void rotateAndDistributeKey(
            Long channelId, Long senderUserId, String senderUsername, Map<Long, byte[]> ciphertextsByUserId) {
        if (ciphertextsByUserId == null || ciphertextsByUserId.isEmpty()) {
            throw new IllegalArgumentException("At least one key envelope is required for key distribution.");
        }

        Set<Long> channelMemberIds = channelMemberRepository.findByChannelId(channelId).stream()
                .map(ChannelMember::getUserId)
                .collect(Collectors.toSet());
        if (channelMemberIds.size() <= 1) {
            return;
        }
        if (!channelMemberIds.equals(ciphertextsByUserId.keySet())) {
            throw new IllegalArgumentException("Key envelope recipients do not match channel membership.");
        }
        if (senderUserId == null || senderUserId <= 0) {
            throw new IllegalArgumentException("Key distribution sender ID must be positive.");
        }
        for (Map.Entry<Long, byte[]> entry : ciphertextsByUserId.entrySet()) {
            byte[] ciphertext = entry.getValue();
            if (entry.getKey() == null || ciphertext == null || ciphertext.length == 0
                    || ciphertext.length > PacketValidation.MAX_PAYLOAD_BYTES - 3 * Long.BYTES) {
                throw new IllegalArgumentException("Key envelope recipient or ciphertext is invalid.");
            }
            if (!appUserRepository.existsById(entry.getKey())) {
                throw new IllegalArgumentException("Key envelope recipient does not exist.");
            }
        }

        ChannelKey channelKey = createNextChannelKey(channelId);
        List<AppUser> recipients = new ArrayList<>();
        for (Map.Entry<Long, byte[]> entry : ciphertextsByUserId.entrySet()) {
            recipients.add(forwardOrQueueUnicastRotation(
                    channelId, senderUserId, entry.getKey(), entry.getValue(), channelKey));
        }
        dispatchAfterCommit(recipients);
        log.info("Created and queued channel key version {} for channel {} by {} to {} members",
                channelKey.getVersion(), channelId, senderUsername, channelMemberIds.size());
    }

    private AppUser forwardOrQueueUnicastRotation(
            Long channelId, Long senderUserId, Long recipientUserId, byte[] ciphertext, ChannelKey channelKey
    ) {
        AppUser recipient = appUserRepository.findById(recipientUserId).orElse(null);
        if (recipient == null) {
            throw new IllegalStateException(
                    "Recipient user ID " + recipientUserId + " disappeared during key rotation for channel "
                            + channelId);
        }

        Packet packet = createUnicastRotationPacket(senderUserId, channelKey, ciphertext);
        // Persist the complete encoded packet before scheduling online delivery.
        byte[] frame = encodeFrame(packet);
        long queueMessageId = userQueue.enqueueForUser(recipientUserId, frame);
        log.debug("Key packet entered user queue: userId={}, messageId={}, frameLength={} bytes, frame(hex)={}",
                recipientUserId, queueMessageId, frame.length, HexFormat.of().formatHex(frame));
        return recipient;
    }

    private Packet createUnicastRotationPacket(Long senderUserId, ChannelKey channelKey, byte[] ciphertext) {
        ByteBuffer payload = ByteBuffer.allocate(3 * Long.BYTES + ciphertext.length);
        payload.putLong(senderUserId);
        payload.putLong(channelKey.getVersion().getMostSignificantBits());
        payload.putLong(channelKey.getVersion().getLeastSignificantBits());
        payload.put(ciphertext);
        return new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(), payload.array());
    }

    private void dispatchAfterCommit(List<AppUser> recipients) {
        Runnable dispatch = () -> {
            for (AppUser recipient : recipients) {
                WebSocketSession session = sessionRegistry.getSession(recipient.getUsername());
                if (session == null || !session.isOpen()) continue;
                try {
                    keyDeliveryService.sendNextQueuedPacket(
                            recipient.getId(), recipient.getUsername(), session);
                } catch (Exception e) {
                    log.warn("Could not dispatch queued key packet to user {}; retained for retry",
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

    /**
     * Attempts to send the queue head after a user reconnects; the packet remains queued until ACK.
     *
     * @param username reconnecting account name
     * @param session authenticated WebSocket session
     */
    @Transactional
    public void processPendingOnConnect(String username, WebSocketSession session) {
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) return;

        keyDeliveryService.sendNextQueuedPacket(user.getId(), username, session);
    }
}
