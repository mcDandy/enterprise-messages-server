package cz.upce.fei.ems.backend.queue;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class PostgresUserQueueTest {
    @Test
    void usesEnterpriseMessagesQueuePrefix() {
        assertEquals("ems_user_8", PostgresUserQueue.queueName(8));
    }

    @Test
    void storesCompleteOpaqueFrameInRecipientQueue() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        byte[] frame = {0x0d, 0, 22, (byte) 0xff};
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("ems_user_8"), any(byte[].class)))
                .thenReturn(41L);
        PostgresUserQueue queue = new PostgresUserQueue(jdbc);

        assertEquals(41L, queue.enqueueForUser(8L, frame));
        verify(jdbc).queryForObject(argThat(sql -> sql.contains("pgmq.send") &&
                        sql.contains("jsonb_build_object('frame'") && !sql.contains("channelId")),
                eq(Long.class), eq("ems_user_8"), aryEq(frame));
    }

    @Test
    void supportsOpaqueMultiplexBytesWithoutPacketMetadata() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("ems_user_9"), any(byte[].class)))
                .thenReturn(1L);
        byte[] arbitraryFrame = {(byte) 0xfe, 0x00};
        assertEquals(1L, new PostgresUserQueue(jdbc).enqueueForUser(9L, arbitraryFrame));
    }

    @Test
    void rejectsInvalidArgumentsAndKeepsAcknowledgmentsRecipientScoped() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PostgresUserQueue queue = new PostgresUserQueue(jdbc);
        assertThrows(IllegalArgumentException.class, () -> PostgresUserQueue.queueName(0));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueueForUser(1L, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> queue.claimForUser(1L, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> queue.claimForUser(1L, 60, 0));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("ems_user_8"), eq(7L)))
                .thenReturn(true);
        assertTrue(queue.acknowledgeForUser(8L, 7L));
        verify(jdbc).queryForObject(contains("pgmq.delete"), eq(Boolean.class), eq("ems_user_8"), eq(7L));
    }

    @Test
    void peeksAndAcknowledgesTheQueueHead() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PostgresUserQueue queue = new PostgresUserQueue(jdbc);
        when(jdbc.query(contains("ORDER BY msg_id LIMIT 1"), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(java.util.List.of(new QueuedPacket(12L, new byte[]{1, 2})));
        when(jdbc.queryForObject(contains("ORDER BY msg_id LIMIT 1"), eq(Boolean.class), eq("ems_user_8")))
                .thenReturn(true);

        assertEquals(12L, queue.peekFirstForUser(8L).getFirst().messageId());
        assertTrue(queue.acknowledgeFirstForUser(8L));
        verify(jdbc).query(contains("FROM pgmq.\"q_ems_user_8\" ORDER BY msg_id LIMIT 1"),
                any(org.springframework.jdbc.core.RowMapper.class));
        verify(jdbc).queryForObject(contains("pgmq.delete(?, msg_id)"), eq(Boolean.class), eq("ems_user_8"));
    }
}
