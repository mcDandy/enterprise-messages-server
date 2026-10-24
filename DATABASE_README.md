# Database

PostgreSQL 17 runs as Compose service `db`. Data persists in the
`postgres_data` volume; the database is not exposed on a host port.

- `database/schema.sql` initializes a **new** database only once.
- `database/migrations/` holds SQL updates for existing databases.
- Hibernate uses `ddl-auto=validate`, not automatic schema changes.

## Existing databases

Before upgrading, back up PostgreSQL and stop the backend. On a database
already updated through migration 002, run migration 003 in one command:

```sh
docker compose --profile migrate run --rm migrate
```

The command runs in Docker (Windows and Linux). It skips 003 if `key_hash`
is already absent. Migration 003 removes `fcm_token` and `key_hash` and
changes channel key versions from integer to UUID while retaining message FKs.
Do not repeat migrations manually.

**Older databases:** migrations 001 and 002 must be applied in order if the
schema predates them. Their SQL files are not automatically applied by the
003 runner. For example, from PowerShell:

```powershell
Get-Content -Raw database/migrations/001_sync_app_user.sql | docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro
Get-Content -Raw database/migrations/002_update_message_schema.sql | docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro
```

Linux can use `< database/migrations/001_sync_app_user.sql` and similarly for 002.
Skip migrations that have already been applied. New databases need none.

If clients have stored old numeric key versions, coordinate their mapping to
new UUIDs before updating; the database migration cannot update client storage.

## Tools

- Connect inside Docker: `docker compose exec db psql -U nnpro -d nnpro`
- Optional Adminer: `docker compose --profile tools up -d adminer` (localhost:8081)
- Tests: `docker compose -p nnpro-tests --profile test run --rm --build tests`

The old Gradle `bootRun` development configuration is available via
`compose.dev.yaml`. Real certificates and `.env` files must never be committed.
