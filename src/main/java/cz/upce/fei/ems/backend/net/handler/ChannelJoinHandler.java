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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

/**
 * Switches the session to the channel all subsequent {@code CHAT_MESSAGE_ENC} and
 * {@code ROTATE_KEY} packets are attributed to.
 * <p>
 * Payload (big-endian): {@code int64 channelId}, optionally followed by {@code int64 channelKeyId}
 * selecting the key version the messages should be stamped with. When the key id is omitted the
 * channel's currently valid key is used.
 * <p>
 * On success nothing is echoed back; failures are reported as an {@code UNKNOWN} packet carrying
 * the reason.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelJoinHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;
    private final SessionRegistry sessionRegistry;
    private final AppUserRepository appUserRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelKeyRepository channelKeyRepository;

    @Override
    public PacketType getSupportedType() {
        return PacketType.CHANNEL_JOIN;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        if (!(session.getAttributes().get("username") instanceof String username)) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        byte[] payload = packet.getPayload();
        if (payload == null || payload.length < Long.BYTES) {
            dispatcher.sendError(session, "Invalid channel join payload.");
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        Long channelId = buffer.getLong();

        Long channelKeyId = null;
        if (payload.length >= 2 * Long.BYTES) {
            channelKeyId = buffer.getLong();
        }

        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, user.getId())) {
            dispatcher.sendError(session, "Access denied: You are not a member of this channel.");
            return;
        }

        if (channelKeyId == null) {
            channelKeyId = channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId)
                    .map(key -> key.getId())
                    .orElse(null);
        }

        Long previousChannelId = session.getAttributes().get("currentChannelId") instanceof Long previous
                ? previous
                : null;
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

        log.info("User {} joined channel {} (key version {})", username, channelId, channelKeyId);
    }
}
