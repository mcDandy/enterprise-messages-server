# Database

PostgreSQL 17 runs as Compose service `db` on the internal Docker network.
Data is stored in the `postgres_data` volume; no database port is exposed to
the host. `database/schema.sql` initializes only new databases. Hibernate uses
`ddl-auto=validate`, so existing schemas require SQL migrations.

## Migrations

For migration 003, use the upgrade commands in [README.md](README.md).
`scripts/migrate-db.sh` runs only 003 and detects whether it is already applied.
It removes `fcm_token` and `key_hash` and changes key versions from integer
to UUID, retaining message foreign keys.

Databases older than migration 002 must first run any missing migrations 001
and 002, in that order. **Back up PostgreSQL and stop the backend first.**
Do not apply these to a fresh database or repeat migration 002.

PowerShell:

```powershell
Get-Content -Raw database/migrations/001_sync_app_user.sql | docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro
Get-Content -Raw database/migrations/002_update_message_schema.sql | docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro
```

Linux shell:

```sh
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro < database/migrations/001_sync_app_user.sql
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro < database/migrations/002_update_message_schema.sql
```

Run only the missing migrations. Migration 003 generates new UUIDs for any
existing key versions; clients storing numeric versions need their own mapping.

## Tools

- PostgreSQL console: `docker compose exec db psql -U nnpro -d nnpro`
- Optional Adminer: `docker compose --profile tools up -d adminer` (`localhost:8081`)
