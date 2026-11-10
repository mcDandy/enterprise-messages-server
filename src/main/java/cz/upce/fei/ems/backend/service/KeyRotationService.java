package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
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
    private final ChannelKeyRepository channelKeyRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final AppUserRepository appUserRepository;
    private final PostgresUserQueue userQueue;
    private final PacketCodec packetCodec;
    private final QueuedKeyDeliveryService keyDeliveryService;

    @Transactional
    public void solicitateNewKeyRotation(Long channelId, Set<String> currentChannelMembers) {
        doSolicitateNewKeyRotation(channelId, currentChannelMembers);
    }

    private void doSolicitateNewKeyRotation(Long channelId, Set<String> currentChannelMembers) {
        try {
            // Only one member should initiate the new key distribution.
            // Prefer an online member; if everyone is offline, choose a stable recipient.
            WebSocketSession online = selectOnlineClient(currentChannelMembers);
            String username = online != null
                    ? (String) online.getAttributes().get("username")
                    : currentChannelMembers.stream().sorted().findFirst().orElse(null);
            if (username == null) return;
            AppUser recipient = appUserRepository.findByUsername(username).orElse(null);
            if (recipient == null) throw new IllegalStateException("Rotation recipient not found");

            UUID version = createNextChannelKey(channelId);
            Packet packet = createRotationPacket(channelId, version);
            userQueue.enqueueForUser(recipient.getId(), encodeFrame(packet));
            log.info("Queued key rotation for channel {} to user {} (requestId: {})",
                    channelId, recipient.getId(), version);
            if (online != null && online.isOpen()) {
                dispatchAfterCommit(() -> {
                    try {
                        keyDeliveryService.sendNextQueuedPacket(recipient.getId(), username, online);
                    } catch (Exception e) {
                        log.warn("Queued rotation for channel {} retained for retry", channelId, e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Failed to schedule key rotation for channel {}", channelId, e);
            throw new IllegalStateException("Could not schedule channel key rotation", e);
        }
    }

    private byte[] encodeFrame(Packet packet) {
        ByteBuffer encoded = packetCodec.encode(packet);
        byte[] frame = new byte[encoded.remaining()];
        encoded.get(frame);
        return frame;
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

        Set<String> otherMembers = findChannelMemberUsernames(channelId).stream()
                .filter(username -> !username.equals(senderUsername))
                .collect(Collectors.toSet());
        if (!otherMembers.isEmpty()) {
            solicitateNewKeyRotation(channelId, otherMembers);
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
        userQueue.enqueueForUser(recipientUserId, encodeFrame(packet));
        WebSocketSession session = sessionRegistry.getSession(recipient.getUsername());
        if (session != null && session.isOpen()) {
            Runnable dispatch = () -> {
                try {
                    keyDeliveryService.sendNextQueuedPacket(recipientUserId, recipient.getUsername(), session);
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

        keyDeliveryService.sendNextQueuedPacket(user.getId(), username, session);
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