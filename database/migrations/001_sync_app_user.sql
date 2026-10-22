-- Run ONCE on an existing developer database after taking a backup.
-- On a FRESH database the corresponding columns are already in schema.sql.
-- This script is designed to be safely rerunnable.
BEGIN;

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS fcm_token varchar(255);
ALTER TABLE app_user ALTER COLUMN fcm_token DROP NOT NULL;

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS is_banned boolean DEFAULT false;
DO $$
DECLARE
    banned_type text;
BEGIN
    SELECT data_type INTO banned_type
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'app_user'
      AND column_name = 'is_banned';

    IF banned_type = 'bit' THEN
        EXECUTE 'ALTER TABLE app_user ALTER COLUMN is_banned TYPE boolean USING (is_banned::text = ''1'')';
    ELSIF banned_type <> 'boolean' THEN
        RAISE EXCEPTION 'Unexpected type of app_user.is_banned: %', banned_type;
    END IF;
END $$;
UPDATE app_user SET is_banned = false WHERE is_banned IS NULL;
ALTER TABLE app_user ALTER COLUMN is_banned SET DEFAULT false;
ALTER TABLE app_user ALTER COLUMN is_banned SET NOT NULL;

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS is_main_admin boolean NOT NULL DEFAULT false;
COMMIT;
