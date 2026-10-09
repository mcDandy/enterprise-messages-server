package cz.upce.fei.ems.backend.net.router;

import cz.upce.fei.ems.backend.net.handler.PacketHandler;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PacketRouter {

    private final Map<PacketType, PacketHandler> handlers;

    public PacketRouter(List<PacketHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(PacketHandler::getSupportedType, Function.identity()));
    }

    public void route(WebSocketSession session, Packet packet) throws Exception {
        PacketType typeEnum = PacketType.fromCode(packet.getType());
        PacketHandler handler = handlers.get(typeEnum);

        if (handler != null) {
            handler.handle(session, packet);
        } else {
            System.err.println("Nenalezen handler pro typ paketu: " + packet.getType());
        }
    }
}