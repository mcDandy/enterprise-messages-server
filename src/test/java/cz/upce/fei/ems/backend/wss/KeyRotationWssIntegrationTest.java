package cz.upce.fei.ems.backend.wss;

import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class KeyRotationWssIntegrationTest extends BaseWssIntegrationTest {

    @Test
    void shouldRequestKeyRotationAndSolicitAnotherPoint() throws Exception {
        // Setup: Two users, one channel, one channel key
        String token1 = loginAndGetToken("rot_user1", "Password123!");
        String token2 = loginAndGetToken("rot_user2", "Password123!");

        Long userId1 = userRepository.findByUsername("rot_user1").orElseThrow().getId();
        Long userId2 = userRepository.findByUsername("rot_user2").orElseThrow().getId();

        Channel channel = new Channel();
        channel.setName("Rotation Point Test");
        channel.setServerId(1L);
        channel = channelRepository.save(channel);

        for (Long uid : new Long[]{userId1, userId2}) {
            ChannelMember m = new ChannelMember();
            m.setChannelId(channel.getId());
            m.setUserId(uid);
            channelMemberRepository.save(m);
        }

        ChannelKey key = new ChannelKey();
        key.setChannelId(channel.getId());
        key.setVersion(UUID.randomUUID());
        channelKeyRepository.save(key);

        // Connect both users
        TestWebSocketClient client1 = createConnectedClient(token1);
        TestWebSocketClient client2 = createConnectedClient(token2);

        setSessionAttributes("rot_user1", channel.getId(), key.getId());
        setSessionAttributes("rot_user2", channel.getId(), key.getId());

        // Client 1 sends Rotate Key Request
        client1.sendPacket(new Packet(PacketType.ROTATE_KEY.getCode(), new byte[0]));

        // Server should respond with a ROTATE_KEY solicitation to SOMEONE in the channel
        Packet solicitation1 = client1.awaitPacket(2, TimeUnit.SECONDS);
        Packet solicitation2 = client2.awaitPacket(2, TimeUnit.SECONDS);

        assertTrue(solicitation1 != null || solicitation2 != null, 
            "One of the points should have received a rotation solicitation");

        Packet received = (solicitation1 != null) ? solicitation1 : solicitation2;
        assertEquals(PacketType.ROTATE_KEY.getCode(), received.getType());

        client1.getSession().close();
        client2.getSession().close();
    }
}
