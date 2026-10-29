package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.service.KeyRotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

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

    public void handle(WebSocketSession session, Packet packet) throws Exception {
        byte[] payload = packet.getPayload();
        if (payload == null || payload.length < 9) {
            dispatcher.sendError(session, "Invalid unicast rotation payload.");
            return;
        }

        String senderUsername = (String) session.getAttributes().get("username");
        if (senderUsername == null || appUserRepository.findByUsername(senderUsername).isEmpty()) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }
        AppUser appUser = appUserRepository.findByUsername(senderUsername).get();

        Long channelId = (Long) session.getAttributes().get("currentChannelId");
        if (channelId == null) {
            dispatcher.sendError(session, "User is not in an active channel.");
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        long recipientUserId = buffer.getLong();

        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        if (appUser.getId() == recipientUserId) {
            dispatcher.sendError(session, "You are sending key to yourself");
            return;
        }

        keyRotationService.handleClientUnicastRotation(
                channelId, appUser.getId(), senderUsername, recipientUserId, ciphertext
        );
    }
}