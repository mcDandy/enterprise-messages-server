
package cz.upce.fei.ems.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MessageReceiptServiceIntegrationTest {

    @Autowired
    private MessageReceiptService receiptService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldTrackLatestReceiptsAndRejectNonMembers() {
        String suffix = UUID.randomUUID().toString();
        String readerName = "reader_" + suffix;
        String outsiderName = "outsider_" + suffix;

        Long senderId = insertId(
                "INSERT INTO app_user (username, password_hash) VALUES (?, ?) RETURNING id",
                "sender_" + suffix, "test_hash");

        Long readerId = insertId(
                "INSERT INTO app_user (username, password_hash) VALUES (?, ?) RETURNING id",
                readerName, "test_hash");

        Long outsiderId = insertId(
                "INSERT INTO app_user (username, password_hash) VALUES (?, ?) RETURNING id",
                outsiderName, "test_hash");

        Long serverId = insertId(
                "INSERT INTO message_server (name, type) VALUES (?, ?) RETURNING id",
                "Test server", "ORGANIZATION");

        Long channelId = insertId(
                "INSERT INTO message_channel (server_id, name) VALUES (?, ?) RETURNING id",
                serverId, "general");

        Long keyId = insertId(
                """
                INSERT INTO message_channel_key
                    (channel_id, version, key_hash)
                VALUES (?, ?, ?) RETURNING id
                """,
                channelId, 1, new byte[]{1, 2, 3});

        jdbc.update(
                "INSERT INTO message_channel_member (channel_id, user_id) VALUES (?, ?)",
                channelId, readerId);

        Long m1 = insertMessage(channelId, senderId, keyId);
        Long m2 = insertMessage(channelId, senderId, keyId);
        Long m3 = insertMessage(channelId, senderId, keyId);

        // Confirm the newest delivered message.
        receiptService.markAsDelivered(m3, readerName);

        // Read an older message.
        receiptService.markAsRead(m2, readerName);

        // Late receipts must not move progress backwards.
        receiptService.markAsRead(m1, readerName);
        receiptService.markAsDelivered(m1, readerName);

        assertEquals(m3, getProgress(channelId, readerId,
                "last_delivered_message_id"));
        assertEquals(m2, getProgress(channelId, readerId,
                "last_read_message_id"));

        // Reading m3 also means it has been delivered.
        receiptService.markAsRead(m3, readerName);

        assertEquals(m3, getProgress(channelId, readerId,
                "last_read_message_id"));

        // Only one receipt row for this user and channel.
        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM message_receipt
                WHERE message_channel_id = ? AND user_id = ?
                """,
                Long.class, channelId, readerId);

        assertEquals(1L, count);

        // Someone outside the channel cannot confirm its messages.
        assertThrows(SecurityException.class,
                () -> receiptService.markAsRead(m3, outsiderName));

        Long outsiderCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM message_receipt WHERE user_id = ?",
                Long.class, outsiderId);

        assertEquals(0L, outsiderCount);
    }

    private Long insertMessage(Long channelId, Long senderId, Long keyId) {
        return insertId(
                """
                INSERT INTO message
                    (channel_id, sender_id, ciphertext, channel_key_id)
                VALUES (?, ?, ?, ?) RETURNING id
                """,
                channelId, senderId, new byte[]{42}, keyId);
    }

    private Long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private Long getProgress(Long channelId, Long userId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM message_receipt " +
                        "WHERE message_channel_id = ? AND user_id = ?",
                Long.class, channelId, userId);
    }
}
