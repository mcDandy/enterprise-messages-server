package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketValidation;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.router.PacketRouter;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.service.KeyRotationService;
import cz.upce.fei.ems.backend.config.TrafficGuard;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

@Component
@RequiredArgsConstructor
public class PacketWebSocketHandler extends BinaryWebSocketHandler {

    private final PacketCodec packetCodec;
    private final PacketDispatcher packetDispatcher;
    private final PacketRouter packetRouter;
    private final SessionRegistry sessionRegistry;
    private final KeyRotationService keyRotationService;
    private final TrafficGuard trafficGuard;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.setBinaryMessageSizeLimit(PacketValidation.MAX_PAYLOAD_BYTES + 1);
        if (!(session.getAttributes().get("username") instanceof String username)) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        if (!trafficGuard.tryOpenWebSocket()) {
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }
        session.getAttributes().put("trafficGuard.websocketPermit", Boolean.TRUE);
        try {
            sessionRegistry.registerSession(username, session);
            keyRotationService.processPendingOnConnect(username, session);
        } catch (Exception exception) {
            releaseConnectionPermit(session);
            if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
            throw exception;
        }
    }

    @Override
    protected void handleBinaryMessage(@NonNull WebSocketSession session, BinaryMessage message) throws Exception {
        String client = session.getRemoteAddress() == null ? "unknown"
                : session.getRemoteAddress().getAddress() == null
                ? session.getRemoteAddress().getHostString()
                : session.getRemoteAddress().getAddress().getHostAddress();
        if (!trafficGuard.allowWebSocketMessage(client) || !trafficGuard.tryEnterWebSocketWork()) {
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }
        try {
            if (!message.getPayload().hasRemaining()) {
                packetDispatcher.sendError(session, "Invalid packet: missing packet type.");
                return;
            }
            Packet packet;
            try {
                packet = packetCodec.decode(message.getPayload());
            } catch (IllegalArgumentException exception) {
                packetDispatcher.sendError(session, "Invalid packet frame.");
                return;
            }
            if (PacketType.fromCode(packet.getType()) == PacketType.UNKNOWN) {
                packetDispatcher.sendError(session, "Unknown packet type.");
                return;
            }
            packetRouter.route(session, packet);
        } finally {
            trafficGuard.leaveWebSocketWork();
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessionRegistry.unregisterSession(session);
        releaseConnectionPermit(session);
    }

    private void releaseConnectionPermit(WebSocketSession session) {
        if (Boolean.TRUE.equals(session.getAttributes().remove("trafficGuard.websocketPermit"))) {
            trafficGuard.closeWebSocket();
        }
    }
}
