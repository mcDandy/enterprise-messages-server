package cz.upce.fei.ems.backend.wss;

import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ChatMessageWssIntegrationTest extends BaseWssIntegrationTest {

    @Test
    void shouldSendMessageAndBroadcastToAllChannelMembers() throws Exception {
        // Setup: Three users, one channel, one channel key
        String token1 = loginAndGetToken("chat_u1", "Password123!");
        String token2 = loginAndGetToken("chat_u2", "Password123!");
        String token3 = loginAndGetToken("chat_u3", "Password123!");

        Long userId1 = userRepository.findByUsername("chat_u1").orElseThrow().getId();
        Long userId2 = userRepository.findByUsername("chat_u2").orElseThrow().getId();
        Long userId3 = userRepository.findByUsername("chat_u3").orElseThrow().getId();

        Channel channel = new Channel();
        channel.setName("Chat Multi-Point Test");
        channel.setServerId(1L);
        channel = channelRepository.save(channel);

        for (Long uid : new Long[]{userId1, userId2, userId3}) {
            ChannelMember m = new ChannelMember();
            m.setChannelId(channel.getId());
            m.setUserId(uid);
            channelMemberRepository.save(m);
        }

        ChannelKey key = new ChannelKey();
        key.setChannelId(channel.getId());
        key.setVersion(UUID.randomUUID());
        key = channelKeyRepository.save(key);

        // Connect all users
        TestWebSocketClient client1 = createConnectedClient(token1);
        TestWebSocketClient client2 = createConnectedClient(token2);
        TestWebSocketClient client3 = createConnectedClient(token3);

        setSessionAttributes("chat_u1", channel.getId(), key.getId());
        setSessionAttributes("chat_u2", channel.getId(), key.getId());
        setSessionAttributes("chat_u3", channel.getId(), key.getId());

        // Client 1 sends an encrypted message
        byte[] content = "Hello points!".getBytes();
        client1.sendPacket(new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), content));

        // Both Client 2 and Client 3 should receive it (broadcast)
        Packet p2 = client2.awaitPacket(5, TimeUnit.SECONDS);
        Packet p3 = client3.awaitPacket(5, TimeUnit.SECONDS);
        
        assertNotNull(p2, "Client 2 should receive message");
        assertNotNull(p3, "Client 3 should receive message");
        assertArrayEquals(content, p2.getPayload());
        assertArrayEquals(content, p3.getPayload());

        client1.getSession().close();
        client2.getSession().close();
        client3.getSession().close();
    }
}
