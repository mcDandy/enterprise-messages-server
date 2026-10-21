package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.MessageReceipt;
import cz.upce.fei.ems.backend.domain.MessageReceiptId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, MessageReceiptId> {

    @Modifying
    @Query(value = """
        INSERT INTO message_receipt
            (message_channel_id, user_id, last_delivered_message_id, last_read_message_id)
        VALUES
            (:channelId, :userId, :messageId, NULL)
        ON CONFLICT (message_channel_id, user_id)
        DO UPDATE SET
            last_delivered_message_id = GREATEST(
                message_receipt.last_delivered_message_id,
                EXCLUDED.last_delivered_message_id
            )
        """, nativeQuery = true)
    void upsertDelivered(
            @Param("channelId") Long channelId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId
    );

    @Modifying
    @Query(value = """
        INSERT INTO message_receipt
            (message_channel_id, user_id, last_delivered_message_id, last_read_message_id)
        VALUES
            (:channelId, :userId, :messageId, :messageId)
        ON CONFLICT (message_channel_id, user_id)
        DO UPDATE SET
            last_delivered_message_id = GREATEST(
                message_receipt.last_delivered_message_id,
                EXCLUDED.last_delivered_message_id
            ),
            last_read_message_id = GREATEST(
                message_receipt.last_read_message_id,
                EXCLUDED.last_read_message_id
            )
        """, nativeQuery = true)
    void upsertRead(
            @Param("channelId") Long channelId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId
    );
}