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
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("ems_user_8"), any(byte[].class), eq(2L)))
                .thenReturn(41L);
        PostgresUserQueue queue = new PostgresUserQueue(jdbc);

        assertEquals(41L, queue.enqueueForUser(8L, 2L, frame));
        verify(jdbc).queryForObject(contains("pgmq.send"), eq(Long.class), eq("ems_user_8"),
                aryEq(frame), eq(2L));
    }

    @Test
    void supportsPacketsWithoutChannelMetadataAndOpaqueMultiplexBytes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("ems_user_9"), any(byte[].class), isNull()))
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
}
