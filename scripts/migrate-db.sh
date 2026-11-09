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

# Older installations pass through 004/005 before converting to per-user queues.
# Fresh schemas already have PGMQ but no legacy shared queues.
pgmq_state=$(psql_query "
SELECT CASE
  WHEN to_regclass('pgmq.q_nnpro_key_delivery') IS NOT NULL
       AND to_regclass('pgmq.q_nnpro_key_rotation') IS NOT NULL THEN 'old_queues'
  WHEN to_regclass('pgmq.q_nnpro_key_delivery') IS NULL
       AND to_regclass('pgmq.q_nnpro_key_rotation') IS NULL
       AND EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pgmq') THEN 'user_queues'
  WHEN to_regclass('pgmq.q_nnpro_key_delivery') IS NULL
       AND to_regclass('pgmq.q_nnpro_key_rotation') IS NULL THEN 'pending'
  ELSE 'partial'
END;")

case "$pgmq_state" in
  pending)
    legacy_queue_state=$(psql_query "
SELECT CASE
  WHEN to_regclass('public.channel_key_pending') IS NOT NULL
       AND to_regclass('public.channel_rotation_pending') IS NOT NULL THEN 'done'
  WHEN to_regclass('public.channel_key_pending') IS NULL
       AND to_regclass('public.channel_rotation_pending') IS NULL THEN 'pending'
  ELSE 'partial'
END;")
    case "$legacy_queue_state" in
      pending) apply_migration 004_pending_key_rotation_queues.sql ;;
      done) echo 'Migration 004: already applied.' ;;
      *) echo 'ERROR: legacy pending tables have an inconsistent schema.' >&2; exit 1 ;;
    esac
    apply_migration 005_pgmq_key_queues.sql
    ;;
  old_queues|user_queues)
    remaining_tables=$(psql_query "
SELECT concat_ws(', ',
  CASE WHEN to_regclass('public.channel_key_pending') IS NOT NULL
       THEN 'public.channel_key_pending' END,
  CASE WHEN to_regclass('public.channel_rotation_pending') IS NOT NULL
       THEN 'public.channel_rotation_pending' END);")
    if [ -n "$remaining_tables" ]; then
      echo "ERROR: legacy pending tables remain: $remaining_tables" >&2
      echo 'Inspect and resolve the pending data before migration 006.' >&2
      exit 1
    fi
    ;;
  *)
    echo 'ERROR: PGMQ shared queues are partially initialized; inspect before migrating.' >&2
    exit 1
    ;;
esac

# Migration 006 is idempotent and also backfills queues for pre-existing users.
apply_migration 006_user_packet_queues.sql

echo 'Database migrations 001-006: up to date.'
