package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.service.MessageReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

@Component
@RequiredArgsConstructor
public class MessageReceiptHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;
    private final MessageReceiptService messageReceiptService;

    @Override
    public PacketType getSupportedType() {
        return PacketType.MESSAGE_RECEIPT;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        String username = (String) session.getAttributes().get("username");
        if (username == null) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        byte[] payload = packet.getPayload();
        if (payload == null || payload.length < 9) { // 8 bytes Long (ID) + 1 byte (action type)
            dispatcher.sendError(session, "Invalid receipt payload.");
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        Long messageId = buffer.getLong();
        byte actionType = buffer.get(); // 1 = DELIVERED, 2 = READ

        try {
            if (actionType == 1) {
                messageReceiptService.markAsDelivered(messageId, username);
            } else if (actionType == 2) {
                messageReceiptService.markAsRead(messageId, username);
            } else {
                dispatcher.sendError(session, "Unknown receipt action type.");
                return;
            }

            Long channelId = (Long) session.getAttributes().get("currentChannelId");
            if (channelId != null) {
                dispatcher.broadcastToChannel(channelId, packet);
            }

        } catch (Exception e) {
            dispatcher.sendError(session, "Failed to process receipt: " + e.getMessage());
        }
    }
}