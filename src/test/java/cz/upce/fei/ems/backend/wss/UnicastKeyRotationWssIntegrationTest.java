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

public class UnicastKeyRotationWssIntegrationTest extends BaseWssIntegrationTest {

    @Test
    void shouldPropagateUnicastKeyRotationStrictlyPointToPoint() throws Exception {
        // Setup: Three users, one channel, one channel key
        String token1 = loginAndGetToken("unicast_user1", "Password123!");
        String token2 = loginAndGetToken("unicast_user2", "Password123!");
        String token3 = loginAndGetToken("unicast_user3", "Password123!");

        Long userId1 = userRepository.findByUsername("unicast_user1").orElseThrow().getId();
        Long userId2 = userRepository.findByUsername("unicast_user2").orElseThrow().getId();
        Long userId3 = userRepository.findByUsername("unicast_user3").orElseThrow().getId();

        Channel channel = new Channel();
        channel.setName("Unicast Strict Test");
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
        channelKeyRepository.save(key);

        TestWebSocketClient client1 = createConnectedClient(token1);
        TestWebSocketClient client2 = createConnectedClient(token2);
        TestWebSocketClient client3 = createConnectedClient(token3);

        byte[] ciphertext = "p2p-secret-payload".getBytes();
        ByteBuffer payload = ByteBuffer.allocate(2 * Long.BYTES + ciphertext.length);
        payload.putLong(userId2);
        payload.putLong(channel.getId());
        payload.put(ciphertext);

        client1.sendPacket(new Packet(PacketType.UNICAST_KEY_ROTATION.getCode(), payload.array()));

        // Intended recipient receives and verifies content
        Packet received = client2.awaitPacket(5, TimeUnit.SECONDS);
        assertNotNull(received, "Client 2 should have received the packet");
        assertEquals(PacketType.UNICAST_KEY_ROTATION.getCode(), received.getType());

        ByteBuffer receivedBuffer = ByteBuffer.wrap(received.getPayload());
        assertEquals(userId1, receivedBuffer.getLong(), "Sender ID mismatch");
        assertEquals(key.getVersion().getMostSignificantBits(), receivedBuffer.getLong());
        assertEquals(key.getVersion().getLeastSignificantBits(), receivedBuffer.getLong());
        byte[] receivedCiphertext = new byte[receivedBuffer.remaining()];
        receivedBuffer.get(receivedCiphertext);
        assertArrayEquals(ciphertext, receivedCiphertext);

        // Unintended recipient should NOT receive it
        Packet unintended = client3.awaitPacket(2, TimeUnit.SECONDS);
        assertNull(unintended, "Client 3 should NOT have received the unicast packet");

        client1.getSession().close();
        client2.getSession().close();
        client3.getSession().close();
    }
}
