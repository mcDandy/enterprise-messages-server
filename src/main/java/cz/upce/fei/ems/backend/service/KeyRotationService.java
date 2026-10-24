package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

    public void solicitateNewKeyRotation(Long channelId, Set<String> currentChannelMembers) {
        WebSocketSession selected = selectOnlineClient(currentChannelMembers);
        if (selected == null) {
            throw new IllegalStateException("No online client available for key rotation in channel " + channelId);
        }

        try {
            String requestId = UUID.randomUUID().toString();

            log.info("Initiating key rotation for channel {} using client: {}",
                    channelId, selected.getAttributes().get("username"));

            dispatcher.sendTo(selected, new Packet(
                    PacketType.ROTATE_KEY.getCode(),
                    requestId.getBytes(StandardCharsets.US_ASCII)
            ));
        } catch (Exception e) {
            log.error("Failed to send rotation solicitation for channel {}: {}", channelId, e.getMessage(), e);
        }
    }

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
                    // Příjemce je online -> okamžité doručení přes WebSocket
                    dispatcher.sendTo(recipientSession, originalPacket);
                    log.debug("Forwarded unicast key rotation ciphertext to online user ID {}", recipientUserId);
                } else {
                    // Příjemce je offline -> Tady musíme ciphertext reálně uložit do DB pro offline sync!
                    log.warn("Recipient user ID {} is offline. " +
                                    "Storing ciphertext ({} bytes) for offline delivery in channel {}.",
                            recipientUserId, ciphertext.length, channelId);

                    // TODO: Uložení ciphertekstu do DB tabulky pro offline doručení (např. PendingKeyDistribution)
                    // pendingKeyRepository.save(new PendingKey(recipientUserId, channelId, newVersionUuid, ciphertext));
                }
            }
        } catch (Exception e) {
            log.error("Failed to process unicast key rotation ciphertext from user {} to user {}: {}",
                    senderUsername, recipientUserId, e.getMessage(), e);
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