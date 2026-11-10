package cz.upce.fei.ems.backend.net.handler;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.service.QueuedKeyDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class AcknowledgePacketHandler implements PacketHandler {
    private final PacketDispatcher dispatcher;
    private final AppUserRepository appUserRepository;
    private final QueuedKeyDeliveryService queuedKeyDeliveryService;

    @Override
    public PacketType getSupportedType() {
        return PacketType.ACKNOWLEDGE;
    }

    @Override
    public void handle(WebSocketSession session, Packet packet) throws Exception {
        if (!(session.getAttributes().get("username") instanceof String username)) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }
        if (packet.getPayload() != null && packet.getPayload().length != 0) {
            dispatcher.sendError(session, "Invalid acknowledgment packet.");
            return;
        }

        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            dispatcher.sendError(session, "Unauthorized connection.");
            return;
        }

        queuedKeyDeliveryService.acknowledgeAndSendNext(user.getId(), username, session);
    }
}
