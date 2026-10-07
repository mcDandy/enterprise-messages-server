package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class KeyExchangeHandler implements PacketHandler {

    @Override
    public PacketType getSupportedType() {
        return PacketType.KEY_EXCHANGE_REQ;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        byte[] clientPublicKey = packet.getPayload();

        System.out.println("Přijat veřejný klíč klienta o délce: " + clientPublicKey.length + " B");
    }
}