package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;

@Component
public class JoinServerHandler implements PacketHandler {

    @Override
    public PacketType getSupportedType() {
        return PacketType.SERVER_INVITE_ACCEPT;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        String clientInfo = new String(packet.getPayload(), StandardCharsets.UTF_8);
        System.out.println("Klient připojuje na server: " + clientInfo);

        // Zde proběhne autorizace / registrace relace v paměti
    }
}