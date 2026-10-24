package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class KeyRotationService {
    private final SessionRegistry sessionRegistry;
    private final PacketDispatcher dispatcher;
    private final ChannelService channelService;

    private final Map<Long, Boolean> channelLockedFlags = new ConcurrentHashMap<>();
    private final Map<String, PendingRotation> pendingResponses = new ConcurrentHashMap<>();

    private record PendingRotation(String username, CompletableFuture<Packet> response) {}

    public boolean isChannelLocked(Long channelId) {
        return channelLockedFlags.getOrDefault(channelId, false);
    }

    /**
     * Metadata-only rotation handshake. No raw symmetric key, key hash or private material
     * is accepted by the server. The Android/backend teams must finalize the client-side
     * group key exchange and authorization before enabling this handshake in production.
     *
     * Request: ROTATE_KEY payload = 36 ASCII bytes request UUID.
     * Reply:   ROTATE_KEY payload = request UUID (36 ASCII) + version UUID (36 ASCII).
     * ACK:     MULTICAST_ROTATED_KEY payload = version UUID (36 ASCII), NOT a secret key.
     */
    public void solicitateNewKeyRotation(Long channelId, Set<String> currentChannelMembers) {
        if (channelLockedFlags.putIfAbsent(channelId, true) != null) {
            throw new IllegalStateException("Rotation is already in progress for channel " + channelId);
        }
        String requestId = UUID.randomUUID().toString();
        try {
            WebSocketSession selected = selectOnlineClient(currentChannelMembers);
            if (selected == null) {
                throw new IllegalStateException("No online client available for key rotation.");
            }
            String selectedUsername = (String) selected.getAttributes().get("username");
            CompletableFuture<Packet> future = new CompletableFuture<>();
            pendingResponses.put(requestId, new PendingRotation(selectedUsername, future));

            dispatcher.sendTo(selected, new Packet(PacketType.ROTATE_KEY.getCode(),
                    requestId.getBytes(StandardCharsets.US_ASCII)));
            Packet reply = future.get(5, TimeUnit.SECONDS);
            byte[] payload = reply.getPayload();
            if (payload == null || payload.length != 72) {
                throw new IllegalArgumentException("Rotation reply must contain request and version UUIDs.");
            }
            UUID version = UUID.fromString(new String(payload, 36, 36, StandardCharsets.US_ASCII));
            channelService.rotateChannelKey(channelId, version);

            // Only public version metadata is acknowledged. Clients distribute secrets E2EE.
            dispatcher.sendTo(selected, new Packet(PacketType.MULTICAST_ROTATED_KEY.getCode(),
                    version.toString().getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            System.err.println("Rotation metadata handshake failed for channel " + channelId + ": " + e.getMessage());
        } finally {
            pendingResponses.remove(requestId);
            channelLockedFlags.remove(channelId);
        }
    }

    public void handleClientRotationResponse(String requestId, String senderUsername, Packet responsePacket) {
        PendingRotation pending = pendingResponses.get(requestId);
        if (pending != null && pending.username().equals(senderUsername)) {
            pending.response().complete(responsePacket);
        }
    }

    private WebSocketSession selectOnlineClient(Set<String> members) {
        for (String username : members) {
            WebSocketSession session = sessionRegistry.getSession(username);
            if (session != null && session.isOpen()) {
                return session;
            }
        }
        return null;
    }
}
