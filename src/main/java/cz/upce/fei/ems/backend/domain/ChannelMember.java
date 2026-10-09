package cz.upce.fei.ems.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import java.time.OffsetDateTime;

@Entity
@Table(name = "channel_member")
@Getter
@Setter
@NoArgsConstructor
@IdClass(ChannelMemberId.class)
public class ChannelMember {
    @Id
    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;
}
