package cz.upce.fei.ems.backend.unit;

import cz.upce.fei.ems.backend.domain.*;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class MessageServerUnitTest {

    @Test
    void testServerGettersAndSetters() {
        MessageServer server = new MessageServer();
        Long id = 1L;
        String name = "Test Server";
        OffsetDateTime now = OffsetDateTime.now();
        MessageServerType type = MessageServerType.ORGANIZATION;

        server.setId(id);
        server.setName(name);
        server.setCreatedAt(now);
        server.setType(type);

        assertThat(server.getId()).isEqualTo(id);
        assertThat(server.getName()).isEqualTo(name);
        assertThat(server.getCreatedAt()).isEqualTo(now);
        assertThat(server.getType()).isEqualTo(type);
    }

    @Test
    void testServerMemberGettersAndSetters() {
        MessageServerMember member = new MessageServerMember();
        Long serverId = 1L;
        Long userId = 2L;
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
}