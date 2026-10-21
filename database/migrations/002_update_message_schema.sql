-- NNPRO / Enterprise Messages
-- Run ONCE on an existing database that still has the old table names and
-- per-message receipts. Run 001_sync_app_user.sql first when applicable.
-- Back up the database before migrating. All operations are transactional.
-- The old per-message receipts are retained in message_receipt_legacy.
-- Fresh databases use database/schema.sql instead of running this migration.

BEGIN;

-- Preserve all existing channel, key, member and message rows.
-- PostgreSQL updates foreign-key references when referenced tables are renamed.
ALTER TABLE channel RENAME TO message_channel;
ALTER TABLE channel_key RENAME TO message_channel_key;
ALTER TABLE channel_member RENAME TO message_channel_member;

-- Archive the old per-message receipt records; do not destroy their history.
ALTER TABLE message_receipt RENAME TO message_receipt_legacy;
-- Primary keys own a schema-wide index name; free it for the new table.
ALTER TABLE message_receipt_legacy
    RENAME CONSTRAINT "PK_message_receipt" TO "PK_message_receipt_legacy";

-- Required by composite foreign keys below: a receipt's referenced message
-- must belong to the same channel as the receipt itself.
ALTER TABLE message
    ADD CONSTRAINT "UQ_message_id_channel" UNIQUE (id, channel_id);

CREATE TABLE message_receipt
(
    message_channel_id bigint NOT NULL,
    user_id bigint NOT NULL,
    last_delivered_message_id bigint NULL,
    last_read_message_id bigint NULL,
    CONSTRAINT "PK_message_receipt" PRIMARY KEY (message_channel_id, user_id),
    CONSTRAINT "FK_message_receipt_channel"
        FOREIGN KEY (message_channel_id) REFERENCES message_channel (id)
        ON DELETE CASCADE,
    CONSTRAINT "FK_message_receipt_user"
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON DELETE NO ACTION,
    CONSTRAINT "FK_receipt_delivered_message"
        FOREIGN KEY (last_delivered_message_id, message_channel_id)
        REFERENCES message (id, channel_id),
    CONSTRAINT "FK_receipt_read_message"
        FOREIGN KEY (last_read_message_id, message_channel_id)
        REFERENCES message (id, channel_id)
);

CREATE INDEX "IX_message_receipt_user_last_read"
    ON message_receipt (user_id ASC, last_read_message_id ASC);

-- Convert old receipts into one row per user and channel.
-- Treat READ as implicitly DELIVERED. MAX(id) follows the simplified
-- prototype's assumption that greater IDs mean later messages in a channel.
INSERT INTO message_receipt
    (message_channel_id, user_id, last_delivered_message_id, last_read_message_id)
SELECT
    m.channel_id,
    r.user_id,
    MAX(r.message_id) FILTER (
        WHERE r.delivered_at IS NOT NULL OR r.read_at IS NOT NULL
    ),
    MAX(r.message_id) FILTER (WHERE r.read_at IS NOT NULL)
FROM message_receipt_legacy AS r
JOIN message AS m ON m.id = r.message_id
GROUP BY m.channel_id, r.user_id;

COMMIT;
