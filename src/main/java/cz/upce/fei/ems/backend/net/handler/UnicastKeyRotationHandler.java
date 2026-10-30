package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.service.KeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnicastKeyRotationHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;
    private final KeyRotationService keyRotationService;
    private final AppUserRepository appUserRepository;

    @Override
    public PacketType getSupportedType() {
        return PacketType.UNICAST_KEY_ROTATION;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        log.info("Handling UNICAST_KEY_ROTATION packet for session ID: {}", session.getId());

        byte[] payload = packet.getPayload();
        if (payload == null || payload.length < 9) {
            log.warn("Invalid unicast rotation payload length: {}", payload != null ? payload.length : 0);
            dispatcher.sendError(session, "Invalid unicast rotation payload.");
            return;
        }
        log.info("Payload received successfully, size: {} bytes", payload.length);

        Object usernameAttr = session.getAttributes().get("username");
        if (!(usernameAttr instanceof String senderUsername)) {
            log.warn("Unauthorized connection attempt: 'username' session attribute is missing or not a String (found: {})", usernameAttr);
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        if (appUserRepository.findByUsername(senderUsername).isEmpty()) {
            log.warn("User '{}' found in session attributes, but does not exist in database.", senderUsername);
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }
        AppUser appUser = appUserRepository.findByUsername(senderUsername).get();
        log.info("Authenticated sender resolved: {} (ID: {})", senderUsername, appUser.getId());

        Object channelAttr = session.getAttributes().get("currentChannelId");
        if (!(channelAttr instanceof Long channelId)) {
            log.warn("User '{}' tried to rotate key without active channel. 'currentChannelId' attribute: {}", senderUsername, channelAttr);
            dispatcher.sendError(session, "User is not in an active channel.");
            return;
        }
        log.info("Active channel ID resolved: {}", channelId);

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        long recipientUserId = buffer.getLong();

        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        log.info("Buffer parsed -> recipientUserId: {}, ciphertext length: {} bytes", recipientUserId, ciphertext.length);

        if (appUser.getId() == recipientUserId) {
            log.warn("User '{}' (ID: {}) attempted to send unicast key rotation to themselves (recipient ID matches sender).", senderUsername, appUser.getId());
            dispatcher.sendError(session, "You are sending key to yourself");
            return;
        }

        log.info("Delegating client unicast rotation: sender '{}' (ID: {}) -> recipient ID {} in channel {}",
                senderUsername, appUser.getId(), recipientUserId, channelId);

        keyRotationService.handleClientUnicastRotation(
                channelId, appUser.getId(), senderUsername, recipientUserId, ciphertext
        );
    }
}