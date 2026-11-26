package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.springframework.web.socket.WebSocketSession;

/**
 * Defines the contract implemented by handlers for individual WebSocket packet types.
 */

public interface PacketHandler {
    /**
     * Identifies the packet type routed to this handler.
     *
     * @return packet type this handler accepts
     */
    PacketType getSupportedType();

    /**
     * Processes one decoded packet for the authenticated session.
     *
     * @param session authenticated sender session
     * @param packet decoded packet routed to this handler
     * @throws Exception when packet handling or an outbound WebSocket write fails
     */
    void handle(WebSocketSession session, Packet packet) throws Exception;
}
