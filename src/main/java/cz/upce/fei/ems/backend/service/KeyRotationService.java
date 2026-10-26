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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<Long, Boolean> channelLockedFlags = new ConcurrentHashMap<>();

    @Transactional
    public void solicitateNewKeyRotation(Long channelId, Set<String> currentChannelMembers) {
        if (channelLockedFlags.putIfAbsent(channelId, true) != null) {
            log.warn("Rotation is already in progress for channel {}", channelId);
            return;
        }
        try {
            WebSocketSession selected = selectOnlineClient(currentChannelMembers);
            if (selected == null) {
                log.warn("No online client available for channel {}. Storing pending rotation trigger in DB.", channelId);

                if (channelRotationPendingRepository.findByChannelId(channelId).isEmpty()) {
                    ChannelRotationPending pending = new ChannelRotationPending();
                    pending.setChannelId(channelId);
                    channelRotationPendingRepository.save(pending);
                }

                channelLockedFlags.remove(channelId);
                return;
            }

            channelRotationPendingRepository.deleteByChannelId(channelId);

            UUID requestId = UUID.randomUUID();

            ByteBuffer buffer = ByteBuffer.allocate(24);
            buffer.putLong(requestId.getMostSignificantBits());
            buffer.putLong(requestId.getLeastSignificantBits());
            buffer.putLong(channelId);

            log.info("Initiating key rotation for channel {} using client: {} (requestId: {})",
                    channelId, selected.getAttributes().get("username"), requestId);

            dispatcher.sendTo(selected, new Packet(
                    PacketType.ROTATE_KEY.getCode(),
                    buffer.array()
            ));
        } catch (Exception e) {
            log.error("Failed to send rotation solicitation for channel {}: {}", channelId, e.getMessage(), e);
            channelLockedFlags.remove(channelId);
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
            Long channelId, String senderUsername, Long recipientUserId, byte[] ciphertext, Packet originalPacket
    ) {
        UUID newVersionUuid = UUID.randomUUID();
        ChannelKey channelKey = new ChannelKey();
        channelKey.setChannelId(channelId);
        channelKey.setVersion(newVersionUuid);
        channelKeyRepository.save(channelKey);

        log.info("User {} distributed new channel key version {} for channel {} (ciphertext size: {} bytes)",
                senderUsername, newVersionUuid, channelId, ciphertext.length);

        try {
            AppUser recipientUser = appUserRepository.findById(recipientUserId).orElse(null);
            if (recipientUser != null) {
                WebSocketSession recipientSession = sessionRegistry.getSession(recipientUser.getUsername());
                if (recipientSession != null && recipientSession.isOpen()) {
                    dispatcher.sendTo(recipientSession, originalPacket);
                    log.debug("Forwarded unicast key rotation ciphertext to online user ID {}", recipientUserId);
                } else {
                    ChannelKeyPending pending = new ChannelKeyPending();
                    pending.setChannelId(channelId);
                    pending.setRecipientUserId(recipientUserId);
                    pending.setChannelKeyId(channelKey.getId());
                    pending.setCiphertext(ciphertext);

                    channelKeyPendingRepository.save(pending);

                    log.warn("Recipient user ID {} is offline. Ciphertext stored in DB queue for channel {}.",
                            recipientUserId, channelId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process unicast key rotation ciphertext from user {} to user {}: {}",
                    senderUsername, recipientUserId, e.getMessage(), e);
        } finally {
            channelLockedFlags.remove(channelId);
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