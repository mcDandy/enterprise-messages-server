package cz.upce.fei.ems.backend.websocket;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.router.PacketRouter;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;

@Component
public class ChatBinaryWebSocketHandler extends BinaryWebSocketHandler {

    private final PacketCodec codec;
    private final PacketRouter router;

    public ChatBinaryWebSocketHandler(PacketCodec codec, PacketRouter router) {
        this.codec = codec;
        this.router = router;
    }

    @Override
    protected void handleBinaryMessage(
            @NonNull WebSocketSession session,
            BinaryMessage message
    ) throws Exception {
        ByteBuffer payloadBuffer = message.getPayload();
        Packet packet = codec.decode(payloadBuffer);

        router.route(session, packet);
    }

    public void sendPacket(WebSocketSession session, Packet packet) throws Exception {
        if (session.isOpen()) {
            ByteBuffer buffer = codec.encode(packet);
            session.sendMessage(new BinaryMessage(buffer));
        }
    }
}