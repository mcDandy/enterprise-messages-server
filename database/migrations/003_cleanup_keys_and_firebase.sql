-- Run ONCE on an existing database after migrations 001 and 002, with the backend stopped.
-- Back up the database FIRST. Fresh databases instead use database/schema.sql.
-- Existing message.channel_key_id values and message_channel_key.id values do NOT change.
-- WARNING: clients that remember the former numeric version must migrate their local
-- key-version mapping. New UUID versions cannot be derived from those integers.
BEGIN;

ALTER TABLE app_user DROP COLUMN IF EXISTS fcm_token;

-- Assign a unique public version identifier to every historic generation.
-- gen_random_uuid() is built into supported PostgreSQL 17.
ALTER TABLE message_channel_key ADD COLUMN version_uuid uuid;
UPDATE message_channel_key SET version_uuid = gen_random_uuid();
ALTER TABLE message_channel_key ALTER COLUMN version_uuid SET NOT NULL;
ALTER TABLE message_channel_key DROP CONSTRAINT "CK_channel_key_version";
ALTER TABLE message_channel_key DROP CONSTRAINT "UQ_channel_key_version";
ALTER TABLE message_channel_key DROP COLUMN version;
ALTER TABLE message_channel_key RENAME COLUMN version_uuid TO version;
ALTER TABLE message_channel_key ADD CONSTRAINT "UQ_channel_key_version" UNIQUE (version);
ALTER TABLE message_channel_key DROP COLUMN key_hash;

COMMIT;
