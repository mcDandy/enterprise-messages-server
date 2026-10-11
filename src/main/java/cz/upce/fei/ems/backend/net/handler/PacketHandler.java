package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.springframework.web.socket.WebSocketSession;

public interface PacketHandler {
    PacketType getSupportedType();
    void handle(WebSocketSession session, Packet packet) throws Exception;
}