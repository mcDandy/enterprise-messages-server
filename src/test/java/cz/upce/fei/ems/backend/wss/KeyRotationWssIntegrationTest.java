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
        String username1 = "rot_user1_" + UUID.randomUUID().toString().replace("-", "");
        String username2 = "rot_user2_" + UUID.randomUUID().toString().replace("-", "");
        String token1 = loginAndGetToken(username1, "Password123!");
        String token2 = loginAndGetToken(username2, "Password123!");

        Long userId1 = userRepository.findByUsername(username1).orElseThrow().getId();
        Long userId2 = userRepository.findByUsername(username2).orElseThrow().getId();

        Channel channel = new Channel();
        channel.setName("Rotation Point Test " + UUID.randomUUID().toString().replace("-", ""));
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

        setSessionAttributes(username1, channel.getId(), key.getId());
        setSessionAttributes(username2, channel.getId(), key.getId());

        // Client 1 sends Rotate Key Request
        client1.sendPacket(new Packet(PacketType.ROTATE_KEY.getCode(), new byte[0]));

        // Server should solicit another channel member, not the requester.
        Packet solicitation1 = client1.awaitPacket(2, TimeUnit.SECONDS);
        Packet solicitation2 = client2.awaitPacket(2, TimeUnit.SECONDS);

        assertNull(solicitation1, "The requester must not receive their own rotation solicitation");
        assertNotNull(solicitation2, "Another channel member should receive the rotation solicitation");

        Packet received = solicitation2;
        assertEquals(PacketType.ROTATE_KEY.getCode(), received.getType());

        client1.getSession().close();
        client2.getSession().close();
    }
}
