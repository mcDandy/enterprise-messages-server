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
        if (!isValidPayload(session, payload)) {
            return;
        }

        AuthenticatedSender authenticatedSender = findAuthenticatedSender(session);
        if (authenticatedSender == null) {
            return;
        }

        UnicastRotationRequest request = parseRequest(payload);
        if (isSelfRotation(session, authenticatedSender, request.recipientUserId())) {
            return;
        }

        delegateRotation(authenticatedSender, request);
    }

    private void delegateRotation(AuthenticatedSender sender, UnicastRotationRequest request) {
        log.info(
                "Delegating client unicast rotation: sender '{}' (ID: {}) -> recipient ID {} in channel {}",
                sender.username(),
                sender.user().getId(),
                request.recipientUserId(),
                request.channelId()
        );
        keyRotationService.handleClientUnicastRotation(
                request.channelId(),
                sender.user().getId(),
                sender.username(),
                request.recipientUserId(),
                request.ciphertext()
        );
    }

    private boolean isValidPayload(WebSocketSession session, byte[] payload) throws Exception {
        if (payload == null || payload.length < 9) {
            log.warn("Invalid unicast rotation payload length: {}", payload != null ? payload.length : 0);
            dispatcher.sendError(session, "Invalid unicast rotation payload.");
            return false;
        }
        log.info("Payload received successfully, size: {} bytes", payload.length);
        return true;
    }

    private AuthenticatedSender findAuthenticatedSender(WebSocketSession session) throws Exception {
        Object usernameAttribute = session.getAttributes().get("username");
        if (!(usernameAttribute instanceof String username)) {
            log.warn(
                    "Unauthorized connection attempt: " +
                            "'username' session attribute is missing or not a String (found: {})",
                    usernameAttribute
            );
            dispatcher.sendError(session, "Unauthorized connection.");
            return null;
        }

        AppUser sender = appUserRepository.findByUsername(username).orElse(null);
        if (sender == null) {
            log.warn("User '{}' found in session attributes, but does not exist in database.", username);
            dispatcher.sendError(session, "Unauthorized connection.");
            return null;
        }
        log.info("Authenticated sender resolved: {} (ID: {})", username, sender.getId());
        return new AuthenticatedSender(username, sender);
    }

    private UnicastRotationRequest parseRequest(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        long recipientUserId = buffer.getLong();
        long channelId = buffer.getLong();
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        log.info(
                "Buffer parsed -> recipientUserId: {}, ciphertext length: {} bytes",
                recipientUserId,
                ciphertext.length
        );
        return new UnicastRotationRequest(recipientUserId, channelId, ciphertext);
    }

    private boolean isSelfRotation(
            WebSocketSession session, AuthenticatedSender sender, long recipientUserId
    ) throws Exception {
        if (sender.user().getId() != recipientUserId) {
            return false;
        }
        log.warn(
                "User '{}' (ID: {}) attempted to send unicast key rotation to themselves (recipient ID matches sender).",
                sender.username(),
                sender.user().getId()
        );
        dispatcher.sendError(session, "You are sending key to yourself");
        return true;
    }

    private record AuthenticatedSender(String username, AppUser user) {
    }

    private record UnicastRotationRequest(long recipientUserId, long channelId, byte[] ciphertext) {
    }
}