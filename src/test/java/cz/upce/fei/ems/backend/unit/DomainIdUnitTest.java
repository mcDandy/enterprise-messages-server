package cz.upce.fei.ems.backend.unit;

import cz.upce.fei.ems.backend.domain.ChannelMemberId;
import cz.upce.fei.ems.backend.domain.MessageReceiptId;
import cz.upce.fei.ems.backend.domain.MessageServerMemberId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainIdUnitTest {

    @Test
    void testChannelMemberId() {
        ChannelMemberId id1 = new ChannelMemberId(1L, 2L);
        ChannelMemberId id2 = new ChannelMemberId(1L, 2L);
        ChannelMemberId id3 = new ChannelMemberId(2L, 2L);

        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void testMessageReceiptId() {
        MessageReceiptId id1 = new MessageReceiptId(10L, 20L);
        MessageReceiptId id2 = new MessageReceiptId(10L, 20L);

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void testMessageServerMemberId() {
        MessageServerMemberId id1 = new MessageServerMemberId(100L, 200L);
        MessageServerMemberId id2 = new MessageServerMemberId(100L, 200L);

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }
}
