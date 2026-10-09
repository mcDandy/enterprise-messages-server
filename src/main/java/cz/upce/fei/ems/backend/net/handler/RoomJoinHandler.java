package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

@Component
@RequiredArgsConstructor
public class RoomJoinHandler implements PacketHandler {

    private final SessionRegistry sessionRegistry;

    @Override
    public PacketType getSupportedType() {
        return PacketType.ROOM_JOIN;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        String username = (String) session.getAttributes().get("username");

        // Předpokládáme, že v payloadu posílá klient channelId jako Long (8 bytů)
        if (packet.getPayload().length >= 8) {
            Long channelId = ByteBuffer.wrap(packet.getPayload()).getLong();
            sessionRegistry.joinChannel(channelId, username);
            System.out.println("Uživatel " + username + " vstoupil do kanálu: " + channelId);
        }
    }
}