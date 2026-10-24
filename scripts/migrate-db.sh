#!/bin/sh
set -eu

# Applies migration 003 only. Older databases must first run 001/002.
state=$(psql -X -A -t -v ON_ERROR_STOP=1 -c "
SELECT CASE
  WHEN to_regclass('public.message_channel_key') IS NULL THEN 'older'
  WHEN EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'message_channel_key'
      AND column_name = 'key_hash'
  ) THEN 'pending'
  WHEN EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'message_channel_key'
      AND column_name = 'version' AND data_type = 'uuid'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_user'
      AND column_name = 'fcm_token'
  ) THEN 'done'
  ELSE 'unexpected'
END")

case "$state" in
  pending) psql -X -v ON_ERROR_STOP=1 -f /migrations/003_cleanup_keys_and_firebase.sql ;;
  done) echo 'Migration 003: already applied (or fresh schema).' ;;
  older) echo 'Old schema: apply migrations 001 and 002 first.' >&2; exit 1 ;;
  *) echo 'Unexpected schema: inspect database before migrating.' >&2; exit 1 ;;
esac
