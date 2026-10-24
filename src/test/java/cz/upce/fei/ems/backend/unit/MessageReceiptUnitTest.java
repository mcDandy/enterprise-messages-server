package cz.upce.fei.ems.backend.unit;

import cz.upce.fei.ems.backend.domain.MessageReceipt;
import cz.upce.fei.ems.backend.domain.MessageReceiptId;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MessageReceiptUnitTest {

    @Test
    void testGettersAndSetters() {
        MessageReceipt receipt = new MessageReceipt();
        Long channelId = 1L;
        Long userId = 2L;
        Long deliveredId = 50L;
        Long readId = 40L;

        receipt.setMessageChannelId(channelId);
        receipt.setUserId(userId);
        receipt.setLastDeliveredMessageId(deliveredId);
        receipt.setLastReadMessageId(readId);

        assertThat(receipt.getMessageChannelId()).isEqualTo(channelId);
        assertThat(receipt.getUserId()).isEqualTo(userId);
        assertThat(receipt.getLastDeliveredMessageId()).isEqualTo(deliveredId);
        assertThat(receipt.getLastReadMessageId()).isEqualTo(readId);
    }

    @Test
    void testCompositeIdEqualsAndHashCode() {
        MessageReceiptId id1 = new MessageReceiptId(1L, 2L);
        MessageReceiptId id2 = new MessageReceiptId(1L, 2L);
        MessageReceiptId id3 = new MessageReceiptId(2L, 1L);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        assertThat(id1).isNotEqualTo(id3);
    }
}