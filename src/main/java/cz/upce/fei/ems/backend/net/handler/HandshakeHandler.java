package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class HandshakeHandler implements PacketHandler {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final SessionRegistry sessionRegistry;
    private final PacketDispatcher dispatcher;

    @Override
    public PacketType getSupportedType() {
        return PacketType.HANDSHAKE;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        String token = new String(packet.getPayload(), StandardCharsets.UTF_8);

        try {
            String username = jwtService.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (!jwtService.isTokenValid(token, userDetails)) {
                dispatcher.sendError(session, "JWT Token vypršel nebo je neplatný.");
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            sessionRegistry.registerSession(username, session);
            Packet ackPacket = new Packet(PacketType.HANDSHAKE.getCode(), "CONNECTED".getBytes(StandardCharsets.UTF_8));
            dispatcher.sendTo(session, ackPacket);

            System.out.println("WebSocket Handshake úspěšný pro uživatele: " + username);

        } catch (Exception e) {
            dispatcher.sendError(session, "Autentizace selhala: " + e.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }
}