package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.service.KeyRotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class KeyRotationHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;
    private final KeyRotationService keyRotationService;

    @Override
    public PacketType getSupportedType() {
        return PacketType.ROTATE_KEY;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        if (!(session.getAttributes().get("username") instanceof String senderUsername)) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        if (!(session.getAttributes().get("currentChannelId") instanceof Long channelId)) {
            dispatcher.sendError(session, "User is not in an active channel.");
            return;
        }

        keyRotationService.handleRotationRequest(channelId, senderUsername);
    }
}