# Database

PostgreSQL 17 runs as Compose service `db` on the internal Docker network.
Data is stored in the `postgres_data` volume; no database port is exposed to
the host. `database/schema.sql` initializes only new databases. Hibernate uses
`ddl-auto=validate`, so existing schemas require SQL migrations.

## Migrations

`database/schema.sql` initializes only a **new** PostgreSQL volume. Existing
databases are upgraded by `scripts/migrate-db.sh` in the Compose `migrate`
profile. **Back up PostgreSQL and stop the backend before applying migrations.**

PowerShell and Linux/macOS (from the repository root):

```sh
docker compose stop backend
docker compose --profile migrate run --rm migrate
docker compose up -d --build
```

The runner detects the schema version from its tables/columns and applies the
missing migrations in order:

- **001** – normalizes `app_user` columns (rerunnable; only used pre-003).
- **002** – renames channel tables and converts message receipts; archives the
  old per-message receipts as `message_receipt_legacy`.
- **003** – removes `fcm_token` and `key_hash`, switches key versions to UUID.
- **004** – creates persistent E2EE pending key delivery and rotation queues.

The runner skips migrations reflected in the schema, including for a newly
initialized database. It stops on unexpected/partially upgraded schemas rather
than applying potentially destructive DDL. Do not run migrations 002–004 by hand
more than once.

**Compatibility warning:** 003 assigns new UUID versions to historical channel
keys. Clients that previously stored numeric key versions must migrate their
local mapping; the UUIDs cannot be derived from old integers.

## Tools

- PostgreSQL console: `docker compose exec db psql -U nnpro -d nnpro`
- Optional Adminer: `docker compose --profile tools up -d adminer` (`localhost:8081`)
