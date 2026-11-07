package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyRotationService {
    private final SessionRegistry sessionRegistry;
    private final PacketDispatcher dispatcher;
    private final ChannelKeyRepository channelKeyRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final AppUserRepository appUserRepository;
    private final ChannelKeyPendingRepository channelKeyPendingRepository;
    private final ChannelRotationPendingRepository channelRotationPendingRepository;


    @Transactional
    public void solicitateNewKeyRotation(Long channelId, Set<String> currentChannelMembers) {
        doSolicitateNewKeyRotation(channelId, currentChannelMembers);
    }

    private void doSolicitateNewKeyRotation(Long channelId, Set<String> currentChannelMembers) {
        try {
            WebSocketSession selected = selectOnlineClient(currentChannelMembers);
            if (selected == null) {
                log.warn(
                        "No online client available for channel {}. Storing pending rotation trigger in DB.",
                        channelId
                );
                //todo add to front
                return;
            }

            UUID uuid = createNextChannelKey(channelId);
            log.info("Initiating key rotation for channel {} using client: {} (requestId: {})",
                    channelId, selected.getAttributes().get("username"), uuid);
            dispatcher.sendTo(selected, createRotationPacket(channelId, uuid));
        } catch (Exception e) {
            log.error("Failed to send rotation solicitation for channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private UUID createNextChannelKey(Long channelId) {
        channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId).ifPresent(channelKey -> {
            channelKey.setInvalidatedAt(OffsetDateTime.now());
            channelKeyRepository.save(channelKey);
            channelKeyRepository.flush();
        });

        UUID version = UUID.randomUUID();
        ChannelKey channelKey = new ChannelKey();
        channelKey.setChannelId(channelId);
        channelKey.setVersion(version);
        channelKeyRepository.save(channelKey);
        return version;
    }

    private Packet createRotationPacket(Long channelId, UUID version) {
        ByteBuffer buffer = ByteBuffer.allocate(3 * Long.BYTES);
        buffer.putLong(version.getMostSignificantBits());
        buffer.putLong(version.getLeastSignificantBits());
        buffer.putLong(channelId);
        return new Packet(PacketType.ROTATE_KEY.getCode(), buffer.array());
    }

    @Transactional
    public void handleRotationRequest(Long channelId, String senderUsername) {
        log.info("Received rotation request from user {} for channel {}", senderUsername, channelId);

        Set<String> currentMembers = findChannelMemberUsernames(channelId);
        if (!currentMembers.isEmpty()) {
            solicitateNewKeyRotation(channelId, currentMembers);
        }
    }

    @Transactional
    public void handleClientUnicastRotation(
            Long channelId, Long senderUserId, String senderUsername, Long recipientUserId, byte[] ciphertext
    ) {
        logUnicastRotationRequest(channelId, senderUsername, recipientUserId);
        if (!areBothUsersChannelMembers(channelId, senderUserId, recipientUserId)) {
            log.error("User {} and user {} are not together in channel {}", senderUsername, recipientUserId, channelId);
        }

        Optional<ChannelKey> activeChannelKey = findActiveChannelKey(
                channelId, senderUsername, ciphertext.length
        );
        if (activeChannelKey.isEmpty()) {
            return;
        }

        try {
            forwardOrQueueUnicastRotation(
                    channelId, senderUserId, recipientUserId, ciphertext, activeChannelKey.get()
            );
        } catch (Exception e) {
            log.error("Failed to process unicast key rotation ciphertext from user {} to user {}: {}",
                    senderUsername, recipientUserId, e.getMessage(), e);
        }
    }

    private void logUnicastRotationRequest(Long channelId, String senderUsername, Long recipientUserId) {
        log.info(
                "Handling client unicast rotation for channel {} from user {} to recipient ID {}",
                channelId,
                senderUsername,
                recipientUserId
        );
    }

    private Optional<ChannelKey> findActiveChannelKey(
            Long channelId, String senderUsername, int ciphertextLength
    ) {
        Optional<ChannelKey> channelKey = channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId);
        if (channelKey.isEmpty()) {
            log.error("Channel key does not exists");
            return Optional.empty();
        }
        log.info(
                "User {} distributed new channel key version {} for channel {} (ciphertext size: {} bytes)",
                senderUsername,
                channelKey,
                channelId,
                ciphertextLength
        );
        return channelKey;
    }

    private boolean areBothUsersChannelMembers(Long channelId, Long senderUserId, Long recipientUserId) {
        return channelMemberRepository.existsByChannelIdAndUserId(channelId, senderUserId)
                && channelMemberRepository.existsByChannelIdAndUserId(channelId, recipientUserId);
    }

    private void forwardOrQueueUnicastRotation(
            Long channelId, Long senderUserId, Long recipientUserId, byte[] ciphertext, ChannelKey channelKey
    ) throws Exception {
        AppUser recipient = appUserRepository.findById(recipientUserId).orElse(null);
        if (recipient == null) {
            log.warn("Recipient user ID {} not found in database during unicast key rotation.", recipientUserId);
            return;
        }

        Packet packet = createUnicastRotationPacket(senderUserId, channelKey, ciphertext);
        WebSocketSession session = sessionRegistry.getSession(recipient.getUsername());
        if (session != null && session.isOpen()) {
            dispatcher.sendTo(session, packet);
            log.info("Forwarded unicast key rotation ciphertext to online user ID {}", recipientUserId);
            return;
        }
        queueUnicastRotation(channelId, recipientUserId, ciphertext);
    }

    private Packet createUnicastRotationPacket(Long senderUserId, ChannelKey channelKey, byte[] ciphertext) {
        ByteBuffer payload = ByteBuffer.allocate(3 * Long.BYTES + ciphertext.length);
        payload.putLong(senderUserId);
        payload.putLong(channelKey.getVersion().getMostSignificantBits());
        payload.putLong(channelKey.getVersion().getLeastSignificantBits());
        payload.put(ciphertext);
        return new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(), payload.array());
    }

    private void queueUnicastRotation(Long channelId, Long recipientUserId, byte[] ciphertext) {
        // TODO: Replace the pending-key table with a proper queue.
        ChannelKeyPending pending = new ChannelKeyPending();
        pending.setChannelId(channelId);
        pending.setRecipientUserId(recipientUserId);
        pending.setCiphertext(ciphertext);
        channelKeyPendingRepository.save(pending);
        log.warn("Recipient user ID {} is offline. Ciphertext stored in DB queue for channel {}.",
                recipientUserId, channelId);
    }

    @Transactional
    public void processPendingOnConnect(String username, WebSocketSession session) {
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) return;

        sendPendingKeys(username, user.getId(), session);
        processPendingRotationTriggers(username, user.getId());
    }

    private void sendPendingKeys(String username, Long userId, WebSocketSession session) {
        List<ChannelKeyPending> pendingKeys = channelKeyPendingRepository.findByRecipientUserId(userId);
        if (!pendingKeys.isEmpty()) {
            log.info("Flushing {} pending key rotation packets for reconnected user {}", pendingKeys.size(), username);
            for (ChannelKeyPending pending : pendingKeys) {
                try {
                    Packet packet = new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(), pending.getCiphertext());
                    dispatcher.sendTo(session, packet);
                } catch (Exception e) {
                    log.error("Failed to send pending key rotation to user {}: {}", username, e.getMessage());
                }
            }
            channelKeyPendingRepository.deleteAll(pendingKeys);
        }
    }

    private void processPendingRotationTriggers(String username, Long userId) {
        List<Long> channelIds = channelMemberRepository.findByUserId(userId).stream()
                .map(ChannelMember::getChannelId)
                .toList();
        channelIds.forEach(channelId -> processPendingRotation(username, channelId));
    }

    private void processPendingRotation(String username, Long channelId) {
        channelRotationPendingRepository.findByChannelId(channelId).ifPresent(pending -> {
            log.info(
                    "Found pending rotation trigger for channel {} upon user {} connection. Initiating...",
                    channelId,
                    username
            );
            channelRotationPendingRepository.deleteByChannelId(channelId);
            doSolicitateNewKeyRotation(channelId, findChannelMemberUsernames(channelId));
        });
    }

    private Set<String> findChannelMemberUsernames(Long channelId) {
        return channelMemberRepository.findByChannelId(channelId).stream()
                .map(member -> appUserRepository.findById(member.getUserId()).orElse(null))
                .filter(Objects::nonNull)
                .map(AppUser::getUsername)
                .collect(Collectors.toSet());
    }

    private WebSocketSession selectOnlineClient(Set<String> members) {
        for (String username : members) {
            WebSocketSession session = sessionRegistry.getSession(username);
            if (session != null && session.isOpen()) {
                return session;
            }
        }
        return null;
    }
}