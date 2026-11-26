-- NNPRO / Enterprise Messages
-- Run once on an existing schema with aggregated message_receipt rows.
-- Back up the database and stop the backend before migrating.
BEGIN;

LOCK TABLE message_channel_member, message_receipt IN ACCESS EXCLUSIVE MODE;

ALTER TABLE message_channel_member
    ADD COLUMN last_delivered_message_id bigint NULL,
    ADD COLUMN last_read_message_id bigint NULL;

-- Preserve progress only for current channel members.
UPDATE message_channel_member AS member
SET last_delivered_message_id = receipt.last_delivered_message_id,
    last_read_message_id = receipt.last_read_message_id
FROM message_receipt AS receipt
WHERE member.channel_id = receipt.message_channel_id
  AND member.user_id = receipt.user_id;

ALTER TABLE message_channel_member
    ADD CONSTRAINT "FK_channel_member_delivered_message"
        FOREIGN KEY (last_delivered_message_id, channel_id)
        REFERENCES message (id, channel_id),
    ADD CONSTRAINT "FK_channel_member_read_message"
        FOREIGN KEY (last_read_message_id, channel_id)
        REFERENCES message (id, channel_id);

CREATE INDEX "IX_channel_member_user_last_read"
    ON message_channel_member (user_id ASC, last_read_message_id ASC);

-- Do not touch message_receipt_legacy archived by migration 002.
DROP TABLE message_receipt;

COMMIT;
