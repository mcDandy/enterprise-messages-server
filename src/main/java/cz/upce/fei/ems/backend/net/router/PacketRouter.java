package cz.upce.fei.ems.backend.net.router;

import cz.upce.fei.ems.backend.net.handler.PacketHandler;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PacketRouter {

    private final Map<PacketType, PacketHandler> handlers;

    public PacketRouter(List<PacketHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(PacketHandler::getSupportedType, Function.identity()));

        log.info("PacketRouter initialized with {} registered handlers: {}",
                handlers.size(), handlers.keySet());
    }

    public void route(WebSocketSession session, Packet packet) throws Exception {

        if (packet == null) {
            log.warn("Received null packet from session ID: {}", session != null ? session.getId() : "unknown");
            return;
        }

        byte[] payload = packet.getPayload();
        int payloadSize = payload != null ? payload.length : 0;
        String typeHex = String.format("0x%02X", packet.getType());


        log.info("Incoming packet -> Type: {} (code: {}), Session ID: {}, Payload size: {} bytes",
                PacketType.fromCode(packet.getType()),
                typeHex,
                session != null ? session.getId() : "unknown",
                payloadSize);

        PacketType typeEnum = PacketType.fromCode(packet.getType());
        PacketHandler handler = handlers.get(typeEnum);

        if (handler != null && session != null) {
            log.info("Routing packet type {} to handler class: {}", typeEnum, handler.getClass().getSimpleName());
            handler.handle(session, packet);
            log.info("Packet type {} successfully handled for session ID: {}", typeEnum, session.getId());
        } else {
            log.error("Handler not found for this packet type: {} (code: {})", typeEnum, typeHex);
        }
    }
}