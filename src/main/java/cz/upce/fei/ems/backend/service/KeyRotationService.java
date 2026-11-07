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

            Optional<ChannelKey> channelKey = channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId);

            if (channelKey.isPresent()) {
                channelKey.get().setInvalidatedAt(OffsetDateTime.now());
                channelKeyRepository.save(channelKey.get());
                channelKeyRepository.flush();
            }

            ChannelKey channelKeyNew = new ChannelKey();
            channelKeyNew.setChannelId(channelId);
            UUID uuid = UUID.randomUUID();
            channelKeyNew.setVersion(uuid);
            channelKeyRepository.save(channelKeyNew);

            ByteBuffer buffer = ByteBuffer.allocate(24);
            buffer.putLong(uuid.getMostSignificantBits());
            buffer.putLong(uuid.getLeastSignificantBits());
            buffer.putLong(channelId);

            log.info("Initiating key rotation for channel {} using client: {} (requestId: {})",
                    channelId, selected.getAttributes().get("username"), uuid);

            Packet rotationPacket = new Packet(PacketType.ROTATE_KEY.getCode(), buffer.array());
            Runnable dispatch = () -> {
                try {
                    dispatcher.sendTo(selected, rotationPacket);
                } catch (Exception e) {
                    log.error("Could not send rotation request to online member for channel {}", channelId, e);
                    // Delivery on the next login needs an explicit retry trigger.
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
        } catch (Exception e) {
            log.error("Failed to schedule key rotation for channel {}", channelId, e);
            throw new IllegalStateException("Could not schedule channel key rotation", e);
        }
    }

    @Transactional
    public void handleRotationRequest(Long channelId, String senderUsername) {
        log.info("Received rotation request from user {} for channel {}", senderUsername, channelId);

        Set<String> currentMembers = channelMemberRepository.findByChannelId(channelId).stream()
                .map(member -> appUserRepository.findById(member.getUserId()).orElse(null))
                .filter(Objects::nonNull)
                .map(AppUser::getUsername)
                .collect(Collectors.toSet());

        if (!currentMembers.isEmpty()) {
            solicitateNewKeyRotation(channelId, currentMembers);
        }
    }

    @Transactional
    public void handleClientUnicastRotation(
            Long channelId, Long senderUserId, String senderUsername, Long recipientUserId, byte[] ciphertext
    ) {
        log.info(
                "Handling client unicast rotation for channel {} from user {} to recipient ID {}",
                channelId,
                senderUsername,
                recipientUserId
        );
        if (
                !channelMemberRepository.existsByChannelIdAndUserId(channelId, senderUserId)
                        || !channelMemberRepository.existsByChannelIdAndUserId(channelId, recipientUserId)
        ) {
            throw new SecurityException("Sender or recipient is not a member of channel " + channelId);
        }

        Optional<ChannelKey> channelKey = channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId);

        if (channelKey.isEmpty()) {
            log.error("Channel key does not exists");
            return;
        }

        log.info(
                "User {} distributed new channel key version {} for channel {} (ciphertext size: {} bytes)",
                senderUsername,
                channelKey,
                channelId,
                ciphertext.length
        );

        try {
            AppUser recipientUser = appUserRepository.findById(recipientUserId).orElse(null);

            if (recipientUser != null) {
                ByteBuffer recipientPayload = ByteBuffer.allocate(3 * Long.BYTES + ciphertext.length);
                recipientPayload.putLong(senderUserId);
                recipientPayload.putLong(channelKey.get().getVersion().getMostSignificantBits());
                recipientPayload.putLong(channelKey.get().getVersion().getLeastSignificantBits());
                recipientPayload.put(ciphertext);

                Packet recipientPacket = new Packet(
                        PacketType.UNICAST_KEY_ROTATION.getCode(),
                        recipientPayload.array()
                );

                // Persist first, even if recipient is currently online. Never
                // send a key packet before its enclosing DB transaction commits.
                keyQueue.enqueueForUser(recipientUserId, channelId, recipientPacket);
                WebSocketSession recipientSession = sessionRegistry.getSession(recipientUser.getUsername());
                if (recipientSession != null && recipientSession.isOpen()) {
                    Runnable dispatch = () -> keyDeliveryService.deliverQueuedKeys(recipientUserId, recipientUser.getUsername(), recipientSession);
                    if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    dispatch.run();
                                } catch (Exception e) {
                                    log.warn("Could not dispatch queued key packet to user {} after commit; retained for reconnect",
                                            recipientUserId, e);
                                }
                            }
                        });
                    } else {
                        dispatch.run();
                    }
                } else {
                    log.info("Recipient user ID {} is offline; complete key packet queued for channel {}.",
                            recipientUserId, channelId);
                }
            } else {
                log.warn("Recipient user ID {} not found in database during unicast key rotation.", recipientUserId);
            }
        } catch (Exception e) {
            log.error("Failed to process unicast key rotation ciphertext from user {} to user {}: {}",
                    senderUsername, recipientUserId, e.getMessage(), e);
            throw new IllegalStateException("Failed to store/send key rotation", e);
        }
    }


    @Transactional
    public void processPendingOnConnect(String username, WebSocketSession session) {
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) return;

        keyDeliveryService.deliverQueuedKeys(user.getId(), username, session);

        List<Long> channelIds = channelMemberRepository.findByUserId(user.getId()).stream()
                .map(ChannelMember::getChannelId).toList();
        for (Long channelId : channelIds) {
            List<Long> pendingRotations = keyQueue.claimRotations(
                    channelId, QUEUE_VISIBILITY_SECONDS, QUEUE_BATCH_SIZE);
            if (pendingRotations.isEmpty()) continue;
            try {
                Set<String> members = channelMemberRepository.findByChannelId(channelId).stream()
                        .map(m -> appUserRepository.findById(m.getUserId()).orElse(null))
                        .filter(Objects::nonNull)
                        .map(AppUser::getUsername)
                        .collect(Collectors.toSet());
                if (members.isEmpty()) continue;
                // Coalesce duplicate pending triggers into one rotation request.
                // This triggers a fresh ROTATE_KEY packet, not an obsolete saved UUID.
                solicitateNewKeyRotation(channelId, members);
                for (Long messageId : pendingRotations) keyQueue.acknowledgeRotation(messageId);
            } catch (Exception e) {
                log.warn("Could not dispatch pending key rotation for channel {}", channelId, e);
            }
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