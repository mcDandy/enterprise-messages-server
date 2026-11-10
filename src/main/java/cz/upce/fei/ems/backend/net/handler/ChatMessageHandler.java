package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

@Component
@RequiredArgsConstructor
public class ChatMessageHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;
    private final MessageService messageService;
    private final AppUserRepository appUserRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelKeyRepository channelKeyRepository;

    @Override
    public PacketType getSupportedType() {
        return PacketType.CHAT_MESSAGE_ENC;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        if (!(session.getAttributes().get("username") instanceof String senderUsername)) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        byte[] payload = packet.getPayload();
        if (payload == null || payload.length < Integer.BYTES) {
            dispatcher.sendError(session, "Invalid chat message payload: missing channel ID.");
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        Long channelId = Integer.toUnsignedLong(buffer.getInt());
        if (channelId == 0) {
            dispatcher.sendError(session, "Invalid channel ID.");
            return;
        }

        AppUser sender = appUserRepository.findByUsername(senderUsername).orElse(null);
        if (sender == null) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, sender.getId())) {
            dispatcher.sendError(session, "Access denied: You are not a member of this channel.");
            return;
        }

        Long channelKeyId = channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId)
                .map(ChannelKey::getId)
                .orElse(null);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        try {
            messageService.processAndQueueMessage(senderUsername, channelId, channelKeyId, ciphertext, packet);
        } catch (IllegalStateException | IllegalArgumentException e) {
            dispatcher.sendError(session, e.getMessage());
        }
    }
}