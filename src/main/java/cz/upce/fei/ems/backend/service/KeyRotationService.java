package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.queue.PostgresKeyQueue;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyRotationService {
    private final SessionRegistry sessionRegistry;
    private final PacketDispatcher dispatcher;
    private final ChannelKeyRepository channelKeyRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final AppUserRepository appUserRepository;
    private final PostgresKeyQueue keyQueue;
    private final QueuedKeyDeliveryService keyDeliveryService;
    private static final int QUEUE_VISIBILITY_SECONDS = 60;
    private static final int QUEUE_BATCH_SIZE = 100;


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
                keyQueue.enqueueRotation(channelId);
                return;
            }

            UUID uuid = createNextChannelKey(channelId);
            log.info("Initiating key rotation for channel {} using client: {} (requestId: {})",
                    channelId, selected.getAttributes().get("username"), uuid);

            Packet rotationPacket = createRotationPacket(channelId, uuid);
            Runnable dispatch = () -> {
                if (!selected.isOpen()) {
                    log.warn("Rotation request for channel {} could not be sent: session closed", channelId);
                    return;
                }
                try {
                    dispatcher.sendTo(selected, rotationPacket);
                } catch (Exception e) {
                    log.error("Could not send rotation request to online member for channel {}", channelId, e);
                }
            };
            dispatchAfterCommit(dispatch);
        } catch (Exception e) {
            log.error("Failed to schedule key rotation for channel {}", channelId, e);
            throw new IllegalStateException("Could not schedule channel key rotation", e);
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
            throw new SecurityException("Sender or recipient is not a member of channel " + channelId);
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
            throw new IllegalStateException("Failed to store/send key rotation", e);
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
    ) {
        AppUser recipient = appUserRepository.findById(recipientUserId).orElse(null);
        if (recipient == null) {
            log.warn("Recipient user ID {} not found in database during unicast key rotation.", recipientUserId);
            return;
        }

        Packet packet = createUnicastRotationPacket(senderUserId, channelKey, ciphertext);
        // Persist the complete encoded packet *before* dispatching it, whether
        // the recipient is online or offline. Queue insert uses the same DB txn.
        keyQueue.enqueueForUser(recipientUserId, channelId, packet);
        WebSocketSession session = sessionRegistry.getSession(recipient.getUsername());
        if (session != null && session.isOpen()) {
            Runnable dispatch = () -> {
                try {
                    keyDeliveryService.deliverQueuedKeys(recipientUserId, recipient.getUsername(), session);
                } catch (Exception e) {
                    log.warn("Could not dispatch queued key packet to user {} after commit; retained for reconnect",
                            recipientUserId, e);
                }
            };
            dispatchAfterCommit(dispatch);
        } else {
            log.info("Recipient user ID {} is offline; complete key packet queued for channel {}.",
                    recipientUserId, channelId);
        }
    }

    private Packet createUnicastRotationPacket(Long senderUserId, ChannelKey channelKey, byte[] ciphertext) {
        ByteBuffer payload = ByteBuffer.allocate(3 * Long.BYTES + ciphertext.length);
        payload.putLong(senderUserId);
        payload.putLong(channelKey.getVersion().getMostSignificantBits());
        payload.putLong(channelKey.getVersion().getLeastSignificantBits());
        payload.put(ciphertext);
        return new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(), payload.array());
    }

    @Transactional
    public void processPendingOnConnect(String username, WebSocketSession session) {
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) return;

        keyDeliveryService.deliverQueuedKeys(user.getId(), username, session);
        processPendingRotationTriggers(username, user.getId());
    }

    private void processPendingRotationTriggers(String username, Long userId) {
        List<Long> channelIds = channelMemberRepository.findByUserId(userId).stream()
                .map(ChannelMember::getChannelId)
                .toList();
        channelIds.forEach(channelId -> processPendingRotation(username, channelId));
    }

    private void processPendingRotation(String username, Long channelId) {
        List<Long> pendingRotations = keyQueue.claimRotations(
                channelId, QUEUE_VISIBILITY_SECONDS, QUEUE_BATCH_SIZE);
        if (pendingRotations.isEmpty()) return;
        try {
            log.info("Found {} pending rotation triggers for channel {} upon user {} connection",
                    pendingRotations.size(), channelId, username);
            Set<String> members = findChannelMemberUsernames(channelId);
            if (members.isEmpty()) return;

            // Coalesce triggers into one fresh rotation request.
            solicitateNewKeyRotation(channelId, members);
            for (Long messageId : pendingRotations) {
                keyQueue.acknowledgeRotation(messageId);
            }
            // NB: dispatch occurs after commit; application-level ACK/retry is still needed.
        } catch (Exception e) {
            log.warn("Could not dispatch pending key rotation for channel {}", channelId, e);
        }
    }

    private Set<String> findChannelMemberUsernames(Long channelId) {
        return channelMemberRepository.findByChannelId(channelId).stream()
                .map(member -> appUserRepository.findById(member.getUserId()).orElse(null))
                .filter(Objects::nonNull)
                .map(AppUser::getUsername)
                .collect(Collectors.toSet());
    }

    private void dispatchAfterCommit(Runnable dispatch) {
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