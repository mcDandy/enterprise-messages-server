# Database, development and migration notes

See [README.md](README.md) for the normal Docker-only startup and test commands.

## PostgreSQL schema and migration policy

- Application PostgreSQL is accessible at `db:5432` **only within Docker's network**.
- Its persistent named volume is `postgres_data`; never use `docker compose down -v`
  unless you intentionally want to destroy database storage.
- `database/schema.sql` initializes **new empty databases once**. It is not rerun
  against an existing volume on subsequent starts.
- JPA uses `ddl-auto=validate`: schema changes must be performed via deliberate
  SQL migrations, not implicit Hibernate mutations.

Before applying any migration, **back up** the existing database and stop the
backend. For the older database schema, run migrations in order exactly once:

```powershell
Get-Content -Raw -Encoding UTF8 .\database\migrations\001_sync_app_user.sql |
  docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro
Get-Content -Raw -Encoding UTF8 .\database\migrations\002_update_message_schema.sql |
  docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro
```

Linux shell equivalent for an **existing older database** (only missing migrations):

```bash
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro < database/migrations/001_sync_app_user.sql
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro < database/migrations/002_update_message_schema.sql
```

Do **not** apply 001/002 to fresh databases initialized from current schema.sql,
or repeat already applied migrations. Migration 002 renames `channel*` tables and
converts receipts to one user/channel state; old receipts are archived in
`message_receipt_legacy`. Existing databases do not update automatically when
files in `database/` change.

## Development mode

Use the existing `compose.dev.yaml` to compile and run through Gradle **in a
container** instead of rebuilding a production-style JAR on every source change:

```powershell
docker compose -p nnpro-dev -f compose.yaml -f compose.dev.yaml up -d --build
docker compose -p nnpro-dev -f compose.yaml -f compose.dev.yaml logs -f backend
```

This separate Compose project has its own database volume. To run it
concurrently with another installation, set a distinct `HTTPS_PORT` and
`TLS_CERTS_DIR`. Only the backend
source and build configuration are bind-mounted; certs are still initialized
in Docker. Be aware that two concurrent projects cannot bind the same host
`HTTPS_PORT`; use a different `.env`/environment port for a second project.

## Native IntelliJ integration tests (optional)

To run tests directly from IntelliJ/Windows rather than in the `tests` Docker
container, start a disposable database:

```powershell
docker compose -p nnpro-tests --profile test up -d db-test
```

Configure IntelliJ's environment for the Gradle test run:

```text
DB_URL=jdbc:postgresql://127.0.0.1:55432/nnpro_test
DB_USERNAME=nnpro
DB_PASSWORD=nnpro_local_test
```

If you changed `TEST_DB_PASSWORD` or `TEST_DB_HOST_PORT`, use your configured
values instead. Clean up with:

```powershell
docker compose -p nnpro-tests --profile test down
```

Prefer Docker-only tests for reproducibility; native runs require an installed JDK.

## TLS notes

`cert-init` creates the development keystore once from `TLS_HOST` and
`TLS_KEYSTORE_PASSWORD`, and exports the public `certs/dev_server.crt` file.
It does not overwrite existing keys on subsequent startups. To inspect SAN:

```powershell
docker compose run --rm --no-deps --entrypoint keytool cert-init -printcert -file /certs/dev_server.crt
```

The keystore and `.env` are ignored by Git. Never commit/distribute them.
When an address changes, back up the old key first and perform a deliberate
certificate rotation with the Android developer, who must trust the replacement.
