package cz.upce.fei.ems.backend.wss;

import cz.upce.fei.ems.backend.integration.BaseRestApiIntegrationTest;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public abstract class BaseWssIntegrationTest extends BaseRestApiIntegrationTest {

    @Autowired
    protected PacketCodec packetCodec;

    @Autowired
    protected SessionRegistry sessionRegistry;

    @Autowired
    protected ChannelRepository channelRepository;

    @Autowired
    protected ChannelMemberRepository channelMemberRepository;

    @Autowired
    protected ChannelKeyRepository channelKeyRepository;

    @Autowired
    protected MessageRepository messageRepository;

    protected String getWssUrl(String token) {
        return "ws://localhost:" + port + "/wss?token=" + token;
    }

    protected TestWebSocketClient createConnectedClient(String token) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        TestWebSocketClient handler = new TestWebSocketClient();
        WebSocketSession session = client.execute(handler, headers, URI.create(getWssUrl(token))).get(5, TimeUnit.SECONDS);
        handler.setSession(session);
        return handler;
    }

    protected void setSessionAttributes(String username, Long channelId, Long channelKeyId) {
        WebSocketSession serverSession = sessionRegistry.getSession(username);
        if (serverSession != null) {
            if (channelId != null) {
                serverSession.getAttributes().put("currentChannelId", channelId);
                // Key-rotation tests still use a selected channel; chat packets carry their own channel ID.
                sessionRegistry.joinChannel(channelId, username);
            }
            if (channelKeyId != null) {
                serverSession.getAttributes().put("currentChannelKeyId", channelKeyId);
            }
        }
    }

    protected class TestWebSocketClient extends BinaryWebSocketHandler {
        private WebSocketSession session;
        private final BlockingQueue<Packet> receivedPackets = new LinkedBlockingQueue<>();

        public void setSession(WebSocketSession session) {
            this.session = session;
        }

        public WebSocketSession getSession() {
            return session;
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            ByteBuffer payload = message.getPayload();
            receivedPackets.add(packetCodec.decode(payload));
        }

        public void sendPacket(Packet packet) throws Exception {
            ByteBuffer buffer = packetCodec.encode(packet);
            session.sendMessage(new BinaryMessage(buffer));
        }

        public Packet awaitPacket(long timeout, TimeUnit unit) throws InterruptedException {
            return receivedPackets.poll(timeout, unit);
        }

        public void clearPackets() {
            receivedPackets.clear();
        }
    }
}
