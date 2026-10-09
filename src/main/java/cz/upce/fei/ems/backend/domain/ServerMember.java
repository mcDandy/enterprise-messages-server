package cz.upce.fei.ems.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import java.time.OffsetDateTime;

@Entity
@Table(name = "server_member")
@Getter
@Setter
@NoArgsConstructor
@IdClass(ServerMemberId.class)
public class ServerMember {
    @Id
    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ServerRole role;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;
}
