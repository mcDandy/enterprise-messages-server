package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

    // (channelId -> locked status)
    private final Map<Long, Boolean> channelLockedFlags = new ConcurrentHashMap<>();

    // (requestId -> CompletableFuture)
    private final Map<String, CompletableFuture<Packet>> pendingResponses = new ConcurrentHashMap<>();

    public boolean isChannelLocked(Long channelId) {
        return channelLockedFlags.getOrDefault(channelId, false);
    }

    public void solicitateNewKeyRotation(Long channelId, Set<String> currentChannelMembers) {
        channelLockedFlags.put(channelId, true);

        try {
            byte[] trustedKeyHashFromDb = channelService.getActiveChannelKeyHash(channelId);

            WebSocketSession chosenClientSession = selectOldestOrRandomClient(currentChannelMembers);
            if (chosenClientSession == null) {
                System.err.println("No online clients available in channel " + channelId + " for key rotation.");
                return;
            }

            String requestId = UUID.randomUUID().toString();
            Packet rotationRequest = new Packet(
                    PacketType.ROTATE_KEY.getCode(),
                    requestId.getBytes(StandardCharsets.UTF_8)
            );

            CompletableFuture<Packet> responseFuture = new CompletableFuture<>();
            pendingResponses.put(requestId, responseFuture);

            dispatcher.sendTo(chosenClientSession, rotationRequest);

            Packet responsePacket = responseFuture.get(5, TimeUnit.SECONDS);

            byte[] clientKeyHashBytes = extractKeyHashFromPayload(responsePacket.getPayload());
            byte[] newKeyData = extractNewKeyFromPayload(responsePacket.getPayload());

            if (!Arrays.equals(clientKeyHashBytes, trustedKeyHashFromDb)) {
                throw new SecurityException(
                        "MiTM attack detected! Client key hash does not match the database record."
                );
            }

            channelService.rotateChannelKey(channelId, newKeyData);

            Packet ackPacket = new Packet(PacketType.MULTICAST_ROTATED_KEY.getCode(), newKeyData);
            dispatcher.sendTo(chosenClientSession, ackPacket);

            System.out.println("Key rotation completed successfully for channel: " + channelId);

        } catch (Exception e) {
            System.err.println("Error during key rotation process: " + e.getMessage());
        } finally {
            channelLockedFlags.put(channelId, false);
        }
    }

    public void handleClientRotationResponse(String requestId, Packet responsePacket) {
        CompletableFuture<Packet> future = pendingResponses.remove(requestId);
        if (future != null) {
            future.complete(responsePacket);
        }
    }

    private WebSocketSession selectOldestOrRandomClient(Set<String> members) {
        for (String username : members) {
            WebSocketSession session = sessionRegistry.getSession(username);
            if (session != null && session.isOpen()) {
                return session;
            }
        }
        return null;
    }

    private byte[] extractKeyHashFromPayload(byte[] payload) {
        byte[] hash = new byte[32];
        System.arraycopy(payload, 0, hash, 0, 32);
        return hash;
    }

    private byte[] extractNewKeyFromPayload(byte[] payload) {
        byte[] key = new byte[payload.length - 32];
        System.arraycopy(payload, 32, key, 0, key.length);
        return key;
    }
}