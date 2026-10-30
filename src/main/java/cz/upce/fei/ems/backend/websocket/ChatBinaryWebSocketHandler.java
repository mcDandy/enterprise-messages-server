package cz.upce.fei.ems.backend.websocket;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.router.PacketRouter;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBinaryWebSocketHandler extends BinaryWebSocketHandler {

    private final PacketCodec codec;
    private final PacketRouter router;
    private final SessionRegistry sessionRegistry;

    @Override
    protected void handleBinaryMessage(@NonNull WebSocketSession session, BinaryMessage message) throws Exception {
        ByteBuffer payloadBuffer = message.getPayload();
        int payloadSize = payloadBuffer.remaining();

        log.info("Handling binary message -> Session ID: {}, Payload size: {} bytes", session.getId(), payloadSize);

        Packet packet = codec.decode(payloadBuffer);
        log.info("Decoded packet -> Type: 0x{}, Session ID: {}",
                String.format("%02X", packet.getType()), session.getId()
        );

        router.route(session, packet);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessionRegistry.unregisterSession(session);
        log.info("WebSocket disconnected: {}", session.getId());
    }
}