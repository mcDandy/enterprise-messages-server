package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.service.ChannelMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

/**
 * Handles read receipts and updates the authenticated members read position.
 */

@Component
@RequiredArgsConstructor
public class ReadPacketHandler implements PacketHandler {
    private final PacketDispatcher dispatcher;
    private final AppUserRepository appUserRepository;
    private final ChannelMembershipService channelMembershipService;

    @Override
    public PacketType getSupportedType() {
        return PacketType.READ;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        if (!(session.getAttributes().get("username") instanceof String username)) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }
        byte[] payload = packet.getPayload();
        if (payload == null || payload.length != Long.BYTES) {
            dispatcher.sendError(session, "Invalid read packet. Expected one message ID.");
            return;
        }

        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        long messageId = ByteBuffer.wrap(payload).getLong();
        if (messageId <= 0) {
            dispatcher.sendError(session, "Invalid read packet. Message ID must be positive.");
            return;
        }
        try {
            channelMembershipService.markAsRead(messageId, user.getId());
        } catch (IllegalArgumentException | SecurityException e) {
            dispatcher.sendError(session, e.getMessage());
        }
    }
}
