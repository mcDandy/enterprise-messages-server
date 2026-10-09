package cz.upce.fei.ems.backend.net.protocol;

import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class PacketDispatcher {

    private final PacketCodec codec;
    private final SessionRegistry sessionRegistry;

    public void sendTo(WebSocketSession session, Packet packet) throws Exception {
        if (session != null && session.isOpen()) {
            ByteBuffer buffer = codec.encode(packet);
            session.sendMessage(new BinaryMessage(buffer));
        }
    }

    public void sendToUser(String username, Packet packet) throws Exception {
        WebSocketSession session = sessionRegistry.getSession(username);
        sendTo(session, packet);
    }

    public void sendError(WebSocketSession session, String errorMessage) throws Exception {
        byte[] payload = errorMessage.getBytes(StandardCharsets.UTF_8);
        Packet errorPacket = new Packet(
                PacketType.UNKNOWN.getCode(),
                Packet.FLAG_NONE, System.currentTimeMillis(),
                payload
        );
        sendTo(session, errorPacket);
    }

    public void broadcastToChannel(Long channelId, Packet packet) throws Exception {
        for (WebSocketSession session : sessionRegistry.getSessionsInChannel(channelId)) {
            sendTo(session, packet);
        }
    }
}