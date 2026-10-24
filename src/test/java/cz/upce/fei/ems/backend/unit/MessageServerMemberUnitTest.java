package cz.upce.fei.ems.backend.unit;

import cz.upce.fei.ems.backend.domain.MessageServerMember;
import cz.upce.fei.ems.backend.domain.MessageServerMemberId;
import cz.upce.fei.ems.backend.domain.MessageServerRole;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class MessageServerMemberUnitTest {

    @Test
    void testGettersAndSetters() {
        MessageServerMember member = new MessageServerMember();
        Long serverId = 10L;
        Long userId = 5L;
        MessageServerRole role = MessageServerRole.ADMINISTRATOR;
        OffsetDateTime now = OffsetDateTime.now();

        member.setServerId(serverId);
        member.setUserId(userId);
        member.setRole(role);
        member.setJoinedAt(now);

        assertThat(member.getServerId()).isEqualTo(serverId);
        assertThat(member.getUserId()).isEqualTo(userId);
        assertThat(member.getRole()).isEqualTo(role);
        assertThat(member.getJoinedAt()).isEqualTo(now);
    }

    @Test
    void testCompositeIdEqualsAndHashCode() {
        MessageServerMemberId id1 = new MessageServerMemberId(1L, 2L);
        MessageServerMemberId id2 = new MessageServerMemberId(1L, 2L);
        MessageServerMemberId id3 = new MessageServerMemberId(2L, 1L);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        assertThat(id1).isNotEqualTo(id3);
    }
}