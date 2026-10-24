package cz.upce.fei.ems.backend.unit;

import cz.upce.fei.ems.backend.domain.ChannelKey;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ChannelKeyUnitTest {

    @Test
    void testGettersAndSetters() {
        ChannelKey key = new ChannelKey();
        Long id = 1L;
        Long channelId = 5L;
        UUID version = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime invalidated = now.plusDays(1);

        key.setId(id);
        key.setChannelId(channelId);
        key.setVersion(version);
        key.setCreatedAt(now);
        key.setInvalidatedAt(invalidated);

        assertThat(key.getId()).isEqualTo(id);
        assertThat(key.getChannelId()).isEqualTo(channelId);
        assertThat(key.getVersion()).isEqualTo(version);
        assertThat(key.getCreatedAt()).isEqualTo(now);
        assertThat(key.getInvalidatedAt()).isEqualTo(invalidated);
    }
}