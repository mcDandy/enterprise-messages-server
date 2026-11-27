package cz.upce.fei.ems.backend.net.protocol;

import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Sends encoded packets to individual sessions, users, or channel members.
 */

@Component
@RequiredArgsConstructor
public class PacketDispatcher {

    private final PacketCodec codec;
    private final SessionRegistry sessionRegistry;

    /** Sends one encoded binary frame when the supplied session is open. */
    public void sendTo(WebSocketSession session, Packet packet) throws Exception {
        if (session != null && session.isOpen()) {
            ByteBuffer buffer = codec.encode(packet);
            session.sendMessage(new BinaryMessage(buffer));
        }
    }

    /** Looks up a user’s active session and sends the packet when one is available. */
    public void sendToUser(String username, Packet packet) throws Exception {
        WebSocketSession session = sessionRegistry.getSession(username);
        sendTo(session, packet);
    }

    /** Sends a UTF-8 error payload using the protocol’s {@link PacketType#UNKNOWN} type. */
    public void sendError(WebSocketSession session, String errorMessage) throws Exception {
        byte[] payload = errorMessage.getBytes(StandardCharsets.UTF_8);
        Packet errorPacket = new Packet(
                PacketType.UNKNOWN.getCode(),
                payload
        );
        sendTo(session, errorPacket);
    }

    /** Sends a packet to the currently connected sessions subscribed to the channel. */
    public void broadcastToChannel(Long channelId, Packet packet) throws Exception {
        for (WebSocketSession session : sessionRegistry.getSessionsInChannel(channelId)) {
            sendTo(session, packet);
        }
    }

    /** Sends a packet to each named user with an active session. */
    public void broadcastToUsers(Set<String> usernames, Packet packet) throws Exception {
        for (String username : usernames) {
            sendToUser(username, packet);
        }
    }
}
