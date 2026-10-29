#!/bin/sh
set -eu

# Run with the backend stopped and after a database backup.
# Accepts both the legacy schema and newer databases initialized by schema.sql.
# SQL files 002-004 are not themselves idempotent; run them only when needed.

psql_query() {
  psql -X -A -t -v ON_ERROR_STOP=1 -c "$1"
}

apply_migration() {
  file="/migrations/$1"
  if [ ! -f "$file" ]; then
    echo "ERROR: Migration file missing: $file" >&2
    exit 1
  fi
  echo "Applying $1 ..."
  psql -X -v ON_ERROR_STOP=1 -f "$file"
}

# Migration 002 renames channel-related tables. Distinguish old/new layouts.
table_state=$(psql_query "
SELECT CASE
  WHEN to_regclass('public.app_user') IS NULL THEN 'missing_app_user'
  WHEN to_regclass('public.channel') IS NOT NULL
       AND to_regclass('public.message_channel') IS NULL THEN 'legacy'
  WHEN to_regclass('public.channel') IS NULL
       AND to_regclass('public.message_channel') IS NOT NULL THEN 'renamed'
  ELSE 'unexpected'
END;")

case "$table_state" in
  legacy)
    echo 'Legacy channel tables detected.'
    # 001 is rerunnable and prepares app_user for the old schema conversion.
    apply_migration 001_sync_app_user.sql
    apply_migration 002_update_message_schema.sql
    ;;
  renamed)
    echo 'Migration 002: already applied (or fresh schema).'
    ;;
  missing_app_user)
    echo 'ERROR: app_user is missing; initialize database/schema.sql first.' >&2
    exit 1
    ;;
  *)
    echo 'ERROR: Cannot identify channel tables; refusing to migrate an unexpected schema.' >&2
    exit 1
    ;;
esac

# Migration 003 changes channel-key versions from integers to UUIDs and
# removes fcm_token/key_hash. Avoid rerunning it on a modern schema.
key_state=$(psql_query "
SELECT CASE
  WHEN to_regclass('public.message_channel_key') IS NULL THEN 'unexpected'
  WHEN EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'message_channel_key'
      AND column_name = 'key_hash'
  ) AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'message_channel_key'
      AND column_name = 'version' AND data_type <> 'uuid'
  ) THEN 'pending'
  WHEN NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'message_channel_key'
      AND column_name = 'key_hash'
  ) AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'message_channel_key'
      AND column_name = 'version' AND data_type = 'uuid'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_user'
      AND column_name = 'fcm_token'
  ) THEN 'done'
  ELSE 'unexpected'
END;")

case "$key_state" in
  pending)
    # 001 might not have been applied yet on databases whose tables were
    # renamed manually. Running it here is safe *before* 003, not after 003.
    if [ "$table_state" = 'renamed' ]; then
      apply_migration 001_sync_app_user.sql
    fi
    apply_migration 003_cleanup_keys_and_firebase.sql
    ;;
  done)
    echo 'Migration 003: already applied (or fresh schema).'
    ;;
  *)
    echo 'ERROR: Cannot identify key schema/version; refusing an unsafe migration 003.' >&2
    exit 1
    ;;
esac

# Migration 004 adds two E2EE pending-rotation/delivery queue tables.
# Both present = complete, neither present = pending, one present = inconsistent.
queue_state=$(psql_query "
SELECT CASE
  WHEN to_regclass('public.channel_key_pending') IS NOT NULL
       AND to_regclass('public.channel_rotation_pending') IS NOT NULL THEN 'done'
  WHEN to_regclass('public.channel_key_pending') IS NULL
       AND to_regclass('public.channel_rotation_pending') IS NULL THEN 'pending'
  ELSE 'partial'
END;")

case "$queue_state" in
  pending)
    apply_migration 004_pending_key_rotation_queues.sql
    ;;
  done)
    echo 'Migration 004: already applied (or fresh schema).'
    ;;
  partial)
    echo 'ERROR: Only one pending-key queue table exists. Inspect the schema manually.' >&2
    exit 1
    ;;
  *)
    echo "ERROR: Unexpected queue schema state: $queue_state" >&2
    exit 1
    ;;
esac

echo 'Database migrations 001-004: up to date.'
