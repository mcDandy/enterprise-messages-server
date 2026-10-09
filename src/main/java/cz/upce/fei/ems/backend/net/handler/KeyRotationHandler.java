package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class KeyRotationHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;

    @Override
    public PacketType getSupportedType() {
        return PacketType.KEY_EXCHANGE_REQ;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        String senderUsername = (String) session.getAttributes().get("username");
        if (senderUsername == null) {
            dispatcher.sendError(session, "Neautorizovaný požadavek.");
            return;
        }

        // Server pouze přeposílá payload s novým klíčem/hashem odesílatele ostatním v relaci
        System.out.println("Přijat požadavek na výměnu/rotaci klíče od: " + senderUsername);

        // Zde se z payloadu dá přečíst cílový channelId a udělat broadcast:
        // dispatcher.broadcastToChannel(channelId, packet);
    }
}