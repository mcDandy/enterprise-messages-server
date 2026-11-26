package cz.upce.fei.ems.backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChannelMemberReceiptIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ChannelMemberRepository members;

    private Long userId;
    private Long channelId;
    private Long messageId;
    private Long laterMessageId;
    private Long otherMessageId;

    @BeforeEach
    void setUp() {
        userId = insertId("INSERT INTO app_user (username, password_hash) VALUES (?, ?) RETURNING id",
                "receipt_" + UUID.randomUUID(), "test_hash");
        Long serverId = insertId("INSERT INTO message_server (name, type) VALUES (?, ?) RETURNING id",
                "Receipt test", "ORGANIZATION");
        channelId = insertId("INSERT INTO message_channel (server_id, name) VALUES (?, ?) RETURNING id",
                serverId, "general");
        Long otherChannelId = insertId("INSERT INTO message_channel (server_id, name) VALUES (?, ?) RETURNING id",
                serverId, "other");
        Long keyId = insertKey(channelId);
        messageId = insertMessage(channelId, keyId);
        laterMessageId = insertMessage(channelId, keyId);
        otherMessageId = insertMessage(otherChannelId, insertKey(otherChannelId));
        join();
    }

    @Test
    void shouldStartEmptyAndResetOnRejoin() {
        assertNull(progress("last_delivered_message_id"));
        assertNull(progress("last_read_message_id"));

        assertEquals(1, members.advanceRead(channelId, userId, messageId));
        assertEquals(messageId, progress("last_delivered_message_id"));
        assertEquals(messageId, progress("last_read_message_id"));

        jdbc.update("DELETE FROM message_channel_member WHERE channel_id = ? AND user_id = ?", channelId, userId);
        assertEquals(0, members.advanceDelivered(channelId, userId, messageId));
        assertEquals(0, members.advanceRead(channelId, userId, messageId));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM message_channel_member WHERE channel_id = ? AND user_id = ?",
                Long.class, channelId, userId));

        join();
        assertNull(progress("last_delivered_message_id"));
        assertNull(progress("last_read_message_id"));
    }

    @Test
    void shouldNotMoveProgressBackwards() {
        assertEquals(1, members.advanceDelivered(channelId, userId, laterMessageId));
        assertEquals(1, members.advanceRead(channelId, userId, messageId));
        assertEquals(laterMessageId, progress("last_delivered_message_id"));
        assertEquals(messageId, progress("last_read_message_id"));
        assertEquals(1, members.advanceRead(channelId, userId, laterMessageId));
        assertEquals(1, members.advanceRead(channelId, userId, messageId));
        assertEquals(laterMessageId, progress("last_delivered_message_id"));
        assertEquals(laterMessageId, progress("last_read_message_id"));
    }

    @Test
    void shouldRejectDeliveredMessageFromAnotherChannel() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE message_channel_member SET last_delivered_message_id = ? WHERE channel_id = ? AND user_id = ?",
                otherMessageId, channelId, userId));
    }

    @Test
    void shouldRejectReadMessageFromAnotherChannel() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE message_channel_member SET last_read_message_id = ? WHERE channel_id = ? AND user_id = ?",
                otherMessageId, channelId, userId));
    }

    @Test
    void shouldRejectMissingMessage() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE message_channel_member SET last_read_message_id = ? WHERE channel_id = ? AND user_id = ?",
                -1L, channelId, userId));
    }

    private void join() {
        jdbc.update("INSERT INTO message_channel_member (channel_id, user_id) VALUES (?, ?)", channelId, userId);
    }

    private Long progress(String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM message_channel_member WHERE channel_id = ? AND user_id = ?",
                Long.class, channelId, userId);
    }

    private Long insertKey(Long channel) {
        return insertId("INSERT INTO message_channel_key (channel_id, version) VALUES (?, ?) RETURNING id",
                channel, UUID.randomUUID());
    }

    private Long insertMessage(Long channel, Long key) {
        return insertId("INSERT INTO message (channel_id, sender_id, ciphertext, channel_key_id) VALUES (?, ?, ?, ?) RETURNING id",
                channel, userId, new byte[]{42}, key);
    }

    private Long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
