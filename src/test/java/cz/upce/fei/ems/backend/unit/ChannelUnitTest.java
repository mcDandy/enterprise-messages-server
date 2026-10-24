package cz.upce.fei.ems.backend.unit;

import cz.upce.fei.ems.backend.domain.Channel;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class ChannelUnitTest {

    @Test
    void testGettersAndSetters() {
        Channel channel = new Channel();
        Long id = 1L;
        Long serverId = 10L;
        String name = "general";
        OffsetDateTime now = OffsetDateTime.now();

        channel.setId(id);
        channel.setServerId(serverId);
        channel.setName(name);
        channel.setCreatedAt(now);

        assertThat(channel.getId()).isEqualTo(id);
        assertThat(channel.getServerId()).isEqualTo(serverId);
        assertThat(channel.getName()).isEqualTo(name);
        assertThat(channel.getCreatedAt()).isEqualTo(now);
    }
}