package cz.upce.fei.ems.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "message_receipt")
@Getter
@Setter
@NoArgsConstructor
@IdClass(MessageReceiptId.class)
public class MessageReceipt {

    @Id
    @Column(name = "message_channel_id", nullable = false)
    private Long messageChannelId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_delivered_message_id")
    private Long lastDeliveredMessageId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;
}