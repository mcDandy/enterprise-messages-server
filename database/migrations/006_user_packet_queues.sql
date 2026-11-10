-- Replace shared PGMQ queues with one opaque packet queue per user.
-- Apply with the backend stopped after a backup. Reentrant, including for earlier local versions of 006.
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgmq;

-- Earlier local installs of 006 used nnpro_user_<id>. Replace that trigger
-- before installing the Enterprise Messages names; this is safe to rerun.
DROP TRIGGER IF EXISTS nnpro_user_queue_on_insert ON public.app_user;
DROP FUNCTION IF EXISTS public.nnpro_create_user_queue();

CREATE OR REPLACE FUNCTION public.ems_create_user_queue() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pgmq.create('ems_user_' || NEW.id::text);
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS ems_user_queue_on_insert ON public.app_user;
CREATE TRIGGER ems_user_queue_on_insert
AFTER INSERT ON public.app_user
FOR EACH ROW EXECUTE FUNCTION public.ems_create_user_queue();

DO $$
DECLARE
    user_row record;
    old_row record;
    recipient_id bigint;
    target_queue text;
    legacy_user_queue record;
    legacy_row record;
    existing_user_queue record;
    target_has_messages boolean;
    archive_has_messages boolean;
    user_id bigint;
    has_delivery boolean := to_regclass('pgmq.q_nnpro_key_delivery') IS NOT NULL;
    has_rotation boolean := to_regclass('pgmq.q_nnpro_key_rotation') IS NOT NULL;
BEGIN
    IF has_delivery <> has_rotation THEN
        RAISE EXCEPTION 'Migration 006 blocked: old PGMQ queues are partially initialized';
    END IF;
    IF has_rotation THEN
        -- Old triggers only store channelId. Converting to a WebSocket frame
        -- would require creating a key version; leave that to backend logic.
        IF EXISTS (SELECT 1 FROM pgmq.q_nnpro_key_rotation) THEN
            RAISE EXCEPTION 'Migration 006 blocked: undelivered rotation triggers must be resolved first';
        END IF;
        IF to_regclass('pgmq.a_nnpro_key_rotation') IS NOT NULL THEN
            IF EXISTS (SELECT 1 FROM pgmq.a_nnpro_key_rotation) THEN
                RAISE EXCEPTION 'Migration 006 blocked: archive for old rotation queue is not empty';
            END IF;
        END IF;
        IF to_regclass('pgmq.a_nnpro_key_delivery') IS NOT NULL THEN
            IF EXISTS (SELECT 1 FROM pgmq.a_nnpro_key_delivery) THEN
                RAISE EXCEPTION 'Migration 006 blocked: archive for old delivery queue is not empty';
            END IF;
        END IF;
    END IF;

    -- Migrate an earlier local 006 installation, retaining only the complete frame.
    -- Never discard an archive or mix two non-empty queues without review.
    FOR legacy_user_queue IN
        SELECT queue_name FROM pgmq.meta
        WHERE queue_name ~ '^nnpro_user_[0-9]+$'
        ORDER BY queue_name
    LOOP
        user_id := substring(legacy_user_queue.queue_name FROM length('nnpro_user_') + 1)::bigint;
        IF NOT EXISTS (SELECT 1 FROM public.app_user WHERE id = user_id) THEN
            RAISE EXCEPTION 'Migration 006 blocked: legacy queue % has no user', legacy_user_queue.queue_name;
        END IF;
        IF to_regclass('pgmq.q_' || legacy_user_queue.queue_name) IS NULL THEN
            RAISE EXCEPTION 'Migration 006 blocked: missing table for %', legacy_user_queue.queue_name;
        END IF;
        IF to_regclass('pgmq.a_' || legacy_user_queue.queue_name) IS NOT NULL THEN
            EXECUTE format('SELECT EXISTS (SELECT 1 FROM pgmq.%I)',
                           'a_' || legacy_user_queue.queue_name)
                INTO archive_has_messages;
            IF archive_has_messages THEN
                RAISE EXCEPTION 'Migration 006 blocked: archived packets in %', legacy_user_queue.queue_name;
            END IF;
        END IF;
        target_queue := 'ems_user_' || user_id::text;
        IF to_regclass('pgmq.q_' || target_queue) IS NOT NULL THEN
            EXECUTE format('SELECT EXISTS (SELECT 1 FROM pgmq.%I)',
                           'q_' || target_queue)
                INTO target_has_messages;
            IF target_has_messages THEN
                RAISE EXCEPTION 'Migration 006 blocked: destination queue % is not empty', target_queue;
            END IF;
        ELSE
            PERFORM pgmq.create(target_queue);
        END IF;
        FOR legacy_row IN EXECUTE format('SELECT message FROM pgmq.%I ORDER BY msg_id',
                                         'q_' || legacy_user_queue.queue_name)
        LOOP
            IF legacy_row.message->>'frame' IS NULL THEN
                RAISE EXCEPTION 'Migration 006 blocked: missing frame in %', legacy_user_queue.queue_name;
            END IF;
            PERFORM decode(legacy_row.message->>'frame', 'base64');
            PERFORM pgmq.send(target_queue,
                jsonb_build_object('frame', legacy_row.message->>'frame'));
        END LOOP;
        PERFORM pgmq.drop_queue(legacy_user_queue.queue_name);
    END LOOP;

    -- Backfill users registered before installing the trigger.
    FOR user_row IN SELECT id FROM public.app_user ORDER BY id LOOP
        target_queue := 'ems_user_' || user_row.id::text;
        IF to_regclass('pgmq.q_' || target_queue) IS NULL THEN
            PERFORM pgmq.create(target_queue);
        END IF;
    END LOOP;

    IF has_delivery THEN
        -- Move complete opaque frames. Channel authorization is a backend concern.
        -- Both send and drop happen in this transaction; any failure rolls back.
        FOR old_row IN SELECT message FROM pgmq.q_nnpro_key_delivery ORDER BY msg_id LOOP
            IF old_row.message->>'recipientUserId' IS NULL OR
                    old_row.message->>'frame' IS NULL THEN
                RAISE EXCEPTION 'Migration 006 blocked: malformed legacy delivery packet';
            END IF;
            recipient_id := (old_row.message->>'recipientUserId')::bigint;
            IF NOT EXISTS (SELECT 1 FROM public.app_user WHERE id = recipient_id) THEN
                RAISE EXCEPTION 'Migration 006 blocked: recipient user % not found', recipient_id;
            END IF;
            PERFORM decode(old_row.message->>'frame', 'base64');
            PERFORM pgmq.send('ems_user_' || recipient_id::text,
                jsonb_build_object('frame', old_row.message->>'frame'));
        END LOOP;
        PERFORM pgmq.drop_queue('nnpro_key_delivery');
        PERFORM pgmq.drop_queue('nnpro_key_rotation');
    END IF;

    -- Earlier deployments of 006 stored optional channelId in JSONB. Strip
    -- that field from outstanding/archived messages without touching frame,
    -- message IDs or visibility metadata. Safe when 006 is rerun.
    FOR existing_user_queue IN
        SELECT queue_name FROM pgmq.meta
        WHERE queue_name ~ '^ems_user_[0-9]+$'
    LOOP
        IF to_regclass('pgmq.q_' || existing_user_queue.queue_name) IS NOT NULL THEN
            EXECUTE format('UPDATE pgmq.%I SET message = message - %L WHERE message ? %L',
                           'q_' || existing_user_queue.queue_name, 'channelId', 'channelId');
        END IF;
        IF to_regclass('pgmq.a_' || existing_user_queue.queue_name) IS NOT NULL THEN
            EXECUTE format('UPDATE pgmq.%I SET message = message - %L WHERE message ? %L',
                           'a_' || existing_user_queue.queue_name, 'channelId', 'channelId');
        END IF;
    END LOOP;
END $$;
COMMIT;
