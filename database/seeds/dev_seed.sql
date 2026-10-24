-- NNPRO development/demo data. Never run on a production database.
-- Run manually on a disposable database initialized from database/schema.sql.
-- Accounts have no user_key: they can test login/REST and membership,
-- but cannot participate in E2EE until a client registers its own key pair.
-- Login: demo_alice, demo_bob, demo_carol / password: DevTest123!
-- Safe to run repeatedly (existing accounts/servers/memberships are preserved).

BEGIN;

INSERT INTO app_user (username, password_hash, display_name)
VALUES
  ('demo_alice', '$2y$10$872MQqBZrz2EUkvET1ZlAO18l016VLqD/4tjSSwjkbKrYc91LhiRC', 'Alice (demo)'),
  ('demo_bob',   '$2y$10$L5IuH8uL9Cx9iex8HTvGLu3wbrR6fvcgRU7oogxGpAhSnquSEXQdy', 'Bob (demo)'),
  ('demo_carol', '$2y$10$3Enh0XqGUe1hjy0lh1STVuiVsie/TqrxCYaoWYB1.1lrh4N0ZZXom', 'Carol (demo)')
ON CONFLICT (username) DO NOTHING;

INSERT INTO message_server (name, type)
SELECT 'NNPRO DEV SEED', 'ORGANIZATION'
WHERE NOT EXISTS (
  SELECT 1 FROM message_server
  WHERE name = 'NNPRO DEV SEED' AND type = 'ORGANIZATION'
);

WITH demo_server AS (
  SELECT id FROM message_server
  WHERE name = 'NNPRO DEV SEED' AND type = 'ORGANIZATION'
  ORDER BY id LIMIT 1
)
INSERT INTO server_member (server_id, user_id, role)
SELECT s.id, u.id,
       CASE WHEN u.username = 'demo_alice' THEN 'ADMINISTRATOR' ELSE 'MEMBER' END
FROM demo_server s
JOIN app_user u ON u.username IN ('demo_alice', 'demo_bob', 'demo_carol')
ON CONFLICT (server_id, user_id) DO NOTHING;

WITH demo_server AS (
  SELECT id FROM message_server
  WHERE name = 'NNPRO DEV SEED' AND type = 'ORGANIZATION'
  ORDER BY id LIMIT 1
)
INSERT INTO message_channel (server_id, name)
SELECT s.id, c.name
FROM demo_server s
CROSS JOIN (VALUES ('general'), ('engineering'), ('hr')) AS c(name)
ON CONFLICT (server_id, name) DO NOTHING;

WITH demo_server AS (
  SELECT id FROM message_server
  WHERE name = 'NNPRO DEV SEED' AND type = 'ORGANIZATION'
  ORDER BY id LIMIT 1
)
INSERT INTO message_channel_member (channel_id, user_id)
SELECT c.id, u.id
FROM demo_server s
JOIN message_channel c ON c.server_id = s.id
JOIN app_user u ON u.username IN ('demo_alice', 'demo_bob', 'demo_carol')
WHERE c.name = 'general'
   OR (c.name = 'engineering' AND u.username IN ('demo_alice', 'demo_bob'))
   OR (c.name = 'hr' AND u.username = 'demo_alice')
ON CONFLICT (channel_id, user_id) DO NOTHING;

COMMIT;

-- Verify what was seeded.
SELECT s.name AS server, c.name AS channel, u.username AS member
FROM message_server s
JOIN message_channel c ON c.server_id = s.id
JOIN message_channel_member cm ON cm.channel_id = c.id
JOIN app_user u ON u.id = cm.user_id
WHERE s.name = 'NNPRO DEV SEED' AND s.type = 'ORGANIZATION'
ORDER BY c.name, u.username;
