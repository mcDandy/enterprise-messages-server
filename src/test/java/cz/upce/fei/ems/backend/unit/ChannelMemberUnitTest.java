package cz.upce.fei.ems.backend.unit;

import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.ChannelMemberId;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class ChannelMemberUnitTest {

    @Test
    void testGettersAndSetters() {
        ChannelMember member = new ChannelMember();
        Long channelId = 1L;
        Long userId = 2L;
        OffsetDateTime now = OffsetDateTime.now();

        member.setChannelId(channelId);
        member.setUserId(userId);
        member.setJoinedAt(now);

        assertThat(member.getChannelId()).isEqualTo(channelId);
        assertThat(member.getUserId()).isEqualTo(userId);
        assertThat(member.getJoinedAt()).isEqualTo(now);
    }

    @Test
    void testCompositeIdEqualsAndHashCode() {
        ChannelMemberId id1 = new ChannelMemberId(1L, 2L);
        ChannelMemberId id2 = new ChannelMemberId(1L, 2L);
        ChannelMemberId id3 = new ChannelMemberId(2L, 1L);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        assertThat(id1).isNotEqualTo(id3);
    }
}