package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class ChatMessageHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;
    private final MessageService messageService;

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

        Long channelKeyId = (Long) session.getAttributes().get("currentChannelKeyId");
        byte[] payload = packet.getPayload();

        try {
            messageService.processAndSaveMessage(senderUsername, channelId, channelKeyId, payload);

            dispatcher.broadcastToChannel(channelId, packet);

        } catch (IllegalStateException | IllegalArgumentException e) {
            dispatcher.sendError(session, e.getMessage());
        }
    }
}