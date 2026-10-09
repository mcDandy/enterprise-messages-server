package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class ChatMessageHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;

    @Override
    public PacketType getSupportedType() {
        return PacketType.CHAT_MESSAGE;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        String senderUsername = (String) session.getAttributes().get("username");
        if (senderUsername == null) {
            dispatcher.sendError(session, "Neautorizované spojení.");
            return;
        }

        // Zde v budoucnu uložíš šifrovaný payload do DB (přes MessageRepository)
        // A rovnou rozpošleš zprávu příjemcům:
        // dispatcher.broadcastToChannel(channelId, packet);
        System.out.println("Přijata zpráva od " + senderUsername + " o délce " + packet.getPayload().length + " B");
    }
}