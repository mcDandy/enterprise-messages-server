package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.springframework.web.socket.WebSocketSession;

// Každý PacketHandler zpracovává jen své konkrétní DTO.
// Přidání nového příkazu znamená pouze vytvořit nový PacketHandler a přidat kód do PacketType.
public interface PacketHandler {
    PacketType getSupportedType();
    void handle(WebSocketSession session, Packet packet) throws Exception;
}