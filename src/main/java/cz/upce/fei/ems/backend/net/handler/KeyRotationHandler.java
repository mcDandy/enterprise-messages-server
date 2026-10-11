package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.service.KeyRotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;

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
        String senderUsername = (String) session.getAttributes().get("username");
        if (senderUsername == null) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        byte[] payload = packet.getPayload();
        if (payload == null || payload.length < 36) {
            dispatcher.sendError(session, "Invalid rotation response payload.");
            return;
        }

        // Extract the 36-byte UUID Request ID from the beginning of the payload
        String requestId = new String(payload, 0, 36, StandardCharsets.UTF_8);

        // Forward the response packet to the service awaiting this request
        keyRotationService.handleClientRotationResponse(requestId, packet);
    }
}