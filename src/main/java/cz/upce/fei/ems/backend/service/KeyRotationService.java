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

            dispatcher.sendTo(selected, new Packet(
                    PacketType.ROTATE_KEY.getCode(),
                    buffer.array()
            ));
        } catch (Exception e) {
            log.error("Failed to send rotation solicitation for channel {}: {}", channelId, e.getMessage(), e);
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
            log.error("User {} and user {} are not together in channel {}", senderUsername, recipientUserId, channelId);
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

                WebSocketSession recipientSession = sessionRegistry.getSession(recipientUser.getUsername());
                if (recipientSession != null && recipientSession.isOpen()) {
                    dispatcher.sendTo(recipientSession, recipientPacket);
                    log.info("Forwarded unicast key rotation ciphertext to online user ID {}", recipientUserId);
                } else {
                    //todo prekopat tabulku
                    ChannelKeyPending pending = new ChannelKeyPending();
                    pending.setChannelId(channelId);
                    pending.setRecipientUserId(recipientUserId);
                    pending.setCiphertext(ciphertext);

                    channelKeyPendingRepository.save(pending);

                    log.warn("Recipient user ID {} is offline. Ciphertext stored in DB queue for channel {}.",
                            recipientUserId, channelId);
                }
            } else {
                log.warn("Recipient user ID {} not found in database during unicast key rotation.", recipientUserId);
            }
        } catch (Exception e) {
            log.error("Failed to process unicast key rotation ciphertext from user {} to user {}: {}",
                    senderUsername, recipientUserId, e.getMessage(), e);
        }
    }


    @Transactional
    public void processPendingOnConnect(String username, WebSocketSession session) {
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) return;

        List<ChannelKeyPending> pendingKeys = channelKeyPendingRepository.findByRecipientUserId(user.getId());
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

        List<Long> channelIds = channelMemberRepository.findByUserId(user.getId()).stream()
                .map(ChannelMember::getChannelId)
                .toList();

        for (Long channelId : channelIds) {
            channelRotationPendingRepository.findByChannelId(channelId).ifPresent(pending -> {
                log.info(
                        "Found pending rotation trigger for channel {} upon user {} connection. Initiating...",
                        channelId,
                        username
                );
                channelRotationPendingRepository.deleteByChannelId(channelId);

                Set<String> members = channelMemberRepository.findByChannelId(channelId).stream()
                        .map(m -> appUserRepository.findById(m.getUserId()).orElse(null))
                        .filter(Objects::nonNull)
                        .map(AppUser::getUsername)
                        .collect(Collectors.toSet());

                solicitateNewKeyRotation(channelId, members);
            });
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