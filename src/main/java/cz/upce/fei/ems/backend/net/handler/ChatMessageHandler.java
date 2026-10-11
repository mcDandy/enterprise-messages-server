package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import cz.upce.fei.ems.backend.service.KeyRotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

@Component
@RequiredArgsConstructor
public class ChatMessageHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;
    private final KeyRotationService keyRotationService;
    private final MessageRepository messageRepository;
    private final AppUserRepository appUserRepository;

    @Override
    public PacketType getSupportedType() {
        return PacketType.CHAT_MESSAGE_ENC;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        String senderUsername = (String) session.getAttributes().get("username");
        if (senderUsername == null) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        Long channelId = (Long) session.getAttributes().get("currentChannelId");
        if (channelId == null) {
            dispatcher.sendError(session, "User is not in an active channel.");
            return;
        }

        if (keyRotationService.isChannelLocked(channelId)) {
            dispatcher.sendError(session, "Channel is temporarily locked due to ongoing key rotation.");
            return;
        }

        AppUser sender = appUserRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database: " + senderUsername));

        byte[] payload = packet.getPayload();
        if (payload == null || payload.length == 0) {
            dispatcher.sendError(session, "Empty message payload.");
            return;
        }
        Long channelKeyId = (Long) session.getAttributes().get("currentChannelKeyId");
        byte[] ciphertext = payload;

        Message message = new Message();
        message.setChannelId(channelId);
        message.setSenderId(sender.getId());
        message.setCiphertext(ciphertext);
        message.setChannelKeyId(channelKeyId);

        messageRepository.save(message);

        dispatcher.broadcastToChannel(channelId, packet);
    }
}