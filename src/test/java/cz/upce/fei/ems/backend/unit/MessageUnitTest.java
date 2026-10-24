package cz.upce.fei.ems.backend.unit;

import cz.upce.fei.ems.backend.domain.Message;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class MessageUnitTest {

    @Test
    void testGettersAndSetters() {
        Message message = new Message();
        Long id = 100L;
        Long channelId = 1L;
        Long senderId = 2L;
        byte[] ciphertext = "encrypted_data".getBytes();
        OffsetDateTime now = OffsetDateTime.now();
        Long channelKeyId = 5L;

        message.setId(id);
        message.setChannelId(channelId);
        message.setSenderId(senderId);
        message.setCiphertext(ciphertext);
        message.setSentAt(now);
        message.setChannelKeyId(channelKeyId);

        assertThat(message.getId()).isEqualTo(id);
        assertThat(message.getChannelId()).isEqualTo(channelId);
        assertThat(message.getSenderId()).isEqualTo(senderId);
        assertThat(message.getCiphertext()).isEqualTo(ciphertext);
        assertThat(message.getSentAt()).isEqualTo(now);
        assertThat(message.getChannelKeyId()).isEqualTo(channelKeyId);
    }
}