package cz.upce.fei.ems.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persists a versioned channel encryption key and its invalidation state.
 */

@Entity
@Table(name = "message_channel_key")
@Getter
@Setter
@NoArgsConstructor
public class ChannelKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    /** Client-visible UUID identifying the key version; key material is not stored here. */
    @Column(name = "version", nullable = false)
    private UUID version;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Null while this key version is active. */
    @Column(name = "invalidated_at")
    private OffsetDateTime invalidatedAt;
}
