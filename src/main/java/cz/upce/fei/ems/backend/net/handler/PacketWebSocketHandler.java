package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.router.PacketRouter;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

@Component
@RequiredArgsConstructor
public class PacketWebSocketHandler extends BinaryWebSocketHandler {

    private final PacketCodec packetCodec;
    private final PacketRouter packetRouter;
    private final SessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String username = (String) session.getAttributes().get("username");
        if (username != null) {
            sessionRegistry.registerSession(username, session);
        } else {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        Packet packet = packetCodec.decode(message.getPayload());

        packetRouter.route(session, packet);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionRegistry.unregisterSession(session);
    }
}