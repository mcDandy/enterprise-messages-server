package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class PingPongHandler implements PacketHandler {

    private final PacketDispatcher dispatcher;

    @Override
    public PacketType getSupportedType() {
        return PacketType.PING;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        Packet pong = new Packet(PacketType.PONG.getCode(), new byte[0]);
        dispatcher.sendTo(session, pong);
    }
}