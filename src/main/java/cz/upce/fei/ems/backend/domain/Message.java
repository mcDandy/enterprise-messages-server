package cz.upce.fei.ems.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import java.time.OffsetDateTime;

/**
 * Persists an encrypted message and its channel, sender, and creation metadata.
 */

@Entity
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** Opaque ciphertext; the server stores and forwards it without decrypting it. */
    @Column(name = "ciphertext", nullable = false)
    private byte[] ciphertext;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    /** Database row for the channel-key version used by the sender. */
    @Column(name = "channel_key_id", nullable = false)
    private Long channelKeyId;
}
