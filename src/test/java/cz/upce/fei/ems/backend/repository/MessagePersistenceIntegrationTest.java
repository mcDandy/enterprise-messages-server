
package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class MessagePersistenceIntegrationTest {

    @Autowired private AppUserRepository userRepository;
    @Autowired private MessageServerRepository serverRepository;
    @Autowired private ChannelRepository channelRepository;
    @Autowired private ChannelKeyRepository keyRepository;
    @Autowired private MessageRepository messageRepository;

    @Test
    void shouldStoreMessagesWithDifferentKeyVersions() {

        // 1. Uživatel
        AppUser user = new AppUser();
        user.setUsername("message_test_" + UUID.randomUUID());
        user.setHashedPassword("test_hash");
        user = userRepository.saveAndFlush(user);

        // 2. Server
        MessageServer server = new MessageServer();
        server.setName("Test Server");
        server.setType(ServerType.ORGANIZATION);
        server = serverRepository.saveAndFlush(server);

        // 3. Kanál
        Channel channel = new Channel();
        channel.setServerId(server.getId());
        channel.setName("general");
        channel = channelRepository.saveAndFlush(channel);

        // 4. První verze šifrovacího klíče
        ChannelKey key1 = new ChannelKey();
        key1.setChannelId(channel.getId());
        key1.setVersion(1);
        key1.setKeyHash(new byte[]{1, 2, 3});
        key1 = keyRepository.saveAndFlush(key1);

        assertNotNull(key1.getId());

        // 5. První zpráva
        Message message1 = new Message();
        message1.setChannelId(channel.getId());
        message1.setSenderId(user.getId());
        message1.setChannelKeyId(key1.getId());
        message1.setCiphertext(new byte[]{10, 20, 30});
        message1 = messageRepository.saveAndFlush(message1);

        // 6. Zneplatnění prvního klíče
        key1.setInvalidatedAt(OffsetDateTime.now());
        keyRepository.saveAndFlush(key1);

        // 7. Druhá verze klíče
        ChannelKey key2 = new ChannelKey();
        key2.setChannelId(channel.getId());
        key2.setVersion(2);
        key2.setKeyHash(new byte[]{4, 5, 6});
        key2 = keyRepository.saveAndFlush(key2);

        // 8. Druhá zpráva používá nový klíč
        Message message2 = new Message();
        message2.setChannelId(channel.getId());
        message2.setSenderId(user.getId());
        message2.setChannelKeyId(key2.getId());
        message2.setCiphertext(new byte[]{40, 50, 60});
        message2 = messageRepository.saveAndFlush(message2);

        // 9. Ověření uložených dat
        assertArrayEquals(
                new byte[]{10, 20, 30},
                messageRepository.findById(message1.getId())
                        .orElseThrow().getCiphertext()
        );

        assertEquals(
                key1.getId(),
                messageRepository.findById(message1.getId())
                        .orElseThrow().getChannelKeyId()
        );

        // 10. Ověření verzování klíčů
        assertEquals(
                key2.getId(),
                keyRepository
                        .findByChannelIdAndInvalidatedAtIsNull(
                                channel.getId()
                        )
                        .orElseThrow().getId()
        );

        assertEquals(
                key1.getId(),
                keyRepository.findByChannelIdAndVersion(
                        channel.getId(), 1
                ).orElseThrow().getId()
        );

        // 11. Ověření stránkování
        PageRequest pageable = PageRequest.of(
                0, 1,
                Sort.by(
                        Sort.Order.desc("sentAt"),
                        Sort.Order.desc("id")
                )
        );

        Slice<Message> messages = messageRepository
                .findByChannelId(channel.getId(), pageable);

        assertEquals(1, messages.getContent().size());
        assertEquals(
                message2.getId(),
                messages.getContent().get(0).getId()
        );
        assertTrue(messages.hasNext());
    }


    @Test
    void shouldRejectTwoActiveKeysInSameChannel() {

        // 1. Vytvoříme server
        MessageServer server = new MessageServer();
        server.setName("Key Constraint Test");
        server.setType(ServerType.ORGANIZATION);
        server = serverRepository.saveAndFlush(server);

        // 2. Vytvoříme kanál
        Channel channel = new Channel();
        channel.setServerId(server.getId());
        channel.setName("general");
        channel = channelRepository.saveAndFlush(channel);

        // 3. První aktivní klíč
        ChannelKey key1 = new ChannelKey();
        key1.setChannelId(channel.getId());
        key1.setVersion(1);
        key1.setKeyHash(new byte[]{1, 2, 3});
        keyRepository.saveAndFlush(key1);

        // 4. Druhý aktivní klíč stejného kanálu
        ChannelKey key2 = new ChannelKey();
        key2.setChannelId(channel.getId());
        key2.setVersion(2);
        key2.setKeyHash(new byte[]{4, 5, 6});

        // Databáze musí druhý aktivní klíč odmítnout
        assertThrows(
                DataIntegrityViolationException.class,
                () -> keyRepository.saveAndFlush(key2)
        );
    }


    @Test
    void shouldRejectMessageWithKeyFromAnotherChannel() {

        // 1. Vytvoříme uživatele
        AppUser user = new AppUser();
        user.setUsername("fk_test_" + UUID.randomUUID());
        user.setHashedPassword("test_hash");
        user = userRepository.saveAndFlush(user);

        // 2. Vytvoříme server
        MessageServer server = new MessageServer();
        server.setName("Foreign Key Test");
        server.setType(ServerType.ORGANIZATION);
        server = serverRepository.saveAndFlush(server);

        // 3. Dva různé kanály
        Channel hr = new Channel();
        hr.setServerId(server.getId());
        hr.setName("HR");
        hr = channelRepository.saveAndFlush(hr);

        Channel it = new Channel();
        it.setServerId(server.getId());
        it.setName("IT");
        it = channelRepository.saveAndFlush(it);

        // 4. Klíč patřící kanálu IT
        ChannelKey itKey = new ChannelKey();
        itKey.setChannelId(it.getId());
        itKey.setVersion(1);
        itKey.setKeyHash(new byte[]{1, 2, 3});
        itKey = keyRepository.saveAndFlush(itKey);

        // 5. Zpráva patří HR, ale používá IT klíč
        Message message = new Message();
        message.setChannelId(hr.getId());
        message.setSenderId(user.getId());
        message.setChannelKeyId(itKey.getId());
        message.setCiphertext(new byte[]{10, 20, 30});

        // 6. Databáze musí vložení odmítnout
        assertThrows(
                DataIntegrityViolationException.class,
                () -> messageRepository.saveAndFlush(message)
        );
    }
}
