package cz.upce.fei.ems.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import java.time.OffsetDateTime;

/**
 * Persists a user membership in a channel, including delivered and read progress.
 */

@Entity
@Table(name = "message_channel_member")
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

    /** Highest message ID whose queued delivery the client has acknowledged. */
    @Column(name = "last_delivered_message_id")
    private Long lastDeliveredMessageId;

    /** Highest message ID the user has explicitly marked as read. */
    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;
}
