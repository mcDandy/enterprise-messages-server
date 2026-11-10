-- Switch from custom pending tables to PGMQ (PostgreSQL 17).
-- IMPORTANT: Existing pending rows cannot be converted losslessly: old unicast
-- rows contain ciphertext only, not the complete recipient packet header.
-- Drain/resolve the old queues before applying, and BACK UP first.
BEGIN;

DO $$
BEGIN
    IF to_regclass('public.channel_key_pending') IS NOT NULL
       AND EXISTS (SELECT 1 FROM public.channel_key_pending) THEN
        RAISE EXCEPTION 'Migration 005 blocked: channel_key_pending has undelivered data; drain/back up it first';
    END IF;
    IF to_regclass('public.channel_rotation_pending') IS NOT NULL
       AND EXISTS (SELECT 1 FROM public.channel_rotation_pending) THEN
        RAISE EXCEPTION 'Migration 005 blocked: channel_rotation_pending has unprocessed triggers; drain/back up them first';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS pgmq;
SELECT pgmq.create('nnpro_key_delivery');
SELECT pgmq.create('nnpro_key_rotation');

DROP TABLE IF EXISTS public.channel_key_pending;
DROP TABLE IF EXISTS public.channel_rotation_pending;
COMMIT;
