
package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class DatabaseRelationsIntegrationTest {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private MessageServerRepository serverRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ServerMemberRepository serverMemberRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Test
    void shouldCreateServerChannelAndMemberships() {

        // 1. Vytvoříme uživatele
        AppUser user = new AppUser();
        user.setUsername("test_" + UUID.randomUUID());
        user.setHashedPassword("test_hash");
        user = userRepository.saveAndFlush(user);

        // 2. Vytvoříme server
        MessageServer server = new MessageServer();
        server.setName("Test Organization");
        server.setType(ServerType.ORGANIZATION);
        server = serverRepository.saveAndFlush(server);

        // 3. Vytvoříme kanál na serveru
        Channel channel = new Channel();
        channel.setServerId(server.getId());
        channel.setName("general");
        channel = channelRepository.saveAndFlush(channel);

        // 4. Přidáme uživatele na server
        ServerMember serverMember = new ServerMember();
        serverMember.setServerId(server.getId());
        serverMember.setUserId(user.getId());
        serverMember.setRole(ServerRole.MEMBER);
        serverMemberRepository.saveAndFlush(serverMember);

        // 5. Přidáme uživatele do kanálu
        ChannelMember channelMember = new ChannelMember();
        channelMember.setChannelId(channel.getId());
        channelMember.setUserId(user.getId());
        channelMemberRepository.saveAndFlush(channelMember);

        // 6. Ověříme výsledky přes repositories
        assertTrue(serverMemberRepository
                .existsByServerIdAndUserId(
                        server.getId(), user.getId()
                ));

        assertTrue(channelMemberRepository
                .existsByChannelIdAndUserId(
                        channel.getId(), user.getId()
                ));

        assertEquals(1, serverMemberRepository
                .findByServerId(server.getId()).size());

        assertEquals(1, channelMemberRepository
                .findByChannelId(channel.getId()).size());
    }
}
