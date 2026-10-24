package cz.upce.fei.ems.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

import java.time.OffsetDateTime;

@Entity
@Table(name = "channel_key_pending")
@Getter
@Setter
@NoArgsConstructor
public class ChannelKeyPending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "channel_key_id", nullable = false)
    private Long channelKeyId;

    @Column(name = "ciphertext", nullable = false, columnDefinition = "bytea")
    private byte[] ciphertext;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}