package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

@Component
@RequiredArgsConstructor
public class ChannelJoinHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;
    private final ChannelMemberRepository channelMemberRepository;
    private final AppUserRepository appUserRepository;
    private final ChannelKeyRepository channelKeyRepository;
    private final SessionRegistry sessionRegistry;

    @Override
    public PacketType getSupportedType() {
        return PacketType.CHANNEL_JOIN;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        String username = (String) session.getAttributes().get("username");
        if (username == null) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        byte[] payload = packet.getPayload();
        if (payload == null || payload.length < 8) {
            dispatcher.sendError(session, "Invalid channel join payload.");
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        Long channelId = buffer.getLong();

        Long channelKeyId = null;
        if (payload.length >= 16) {
            channelKeyId = buffer.getLong();
        }

        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));

        boolean isMember = channelMemberRepository.existsByChannelIdAndUserId(channelId, user.getId());
        if (!isMember) {
            dispatcher.sendError(session, "Access denied: You are not a member of this channel.");
            return;
        }

        if (channelKeyId == null) {
            channelKeyId = channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId)
                    .map(key -> key.getId())
                    .orElse(null);
        }

        Long previousChannelId = (Long) session.getAttributes().get("currentChannelId");
        if (previousChannelId != null && !previousChannelId.equals(channelId)) {
            sessionRegistry.leaveChannel(previousChannelId, username);
        }
        sessionRegistry.joinChannel(channelId, username);

        session.getAttributes().put("currentChannelId", channelId);
        if (channelKeyId != null) {
            session.getAttributes().put("currentChannelKeyId", channelKeyId);
        } else {
            session.getAttributes().remove("currentChannelKeyId");
        }

    }
}