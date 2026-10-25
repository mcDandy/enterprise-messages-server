# Development seed

`dev_seed.sql` populates a **disposable PostgreSQL database** with three demo users, one organizational server, three channels, and memberships. It can be run more than once without duplicating records.

Accounts: `demo_alice`, `demo_bob`, `demo_carol` (password `DevTest123!`). Passwords are BCrypt hashes. **Do not run this seed against a production database.** It does not create `user_key` entries or usable E2EE key pairs; clients must register their own keys for actual encrypted messaging.

## Automatic development seed

When using `compose.dev.yaml`, a one-shot `dev-seed` service automatically
imports this SQL file into the **development** `db` once PostgreSQL is healthy
and before the backend starts. It also works if the development database
already has a persistent volume. Existing demo records are preserved because
this SQL script can be run repeatedly.

```powershell
docker compose -p nnpro-hotdev -f compose.yaml -f compose.dev.yaml --profile dev up -d dev-seed
```

The command above runs the seed and database without starting the full Java
backend. For the complete dev environment use the documented `compose.dev.yaml`
startup command. **Normal `compose.yaml` deployments are not seeded.** Never
use this dev override against a production database.

## Manual seed on isolated test database

From the project root, with Docker Compose and a configured `.env`, use a separate temporary test database:

```powershell
# PowerShell
$env:TEST_DB_HOST_PORT = '55439'
docker compose -p nnpro-seed --profile test up -d db-test
Get-Content -Raw .\database\seeds\dev_seed.sql | docker compose -p nnpro-seed --profile test exec -T db-test psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro_test
docker compose -p nnpro-seed --profile test down
Remove-Item Env:TEST_DB_HOST_PORT
```

On Linux/macOS replace the PowerShell environment setting with `export TEST_DB_HOST_PORT=55439`, the pipe with:

```sh
docker compose -p nnpro-seed --profile test exec -T db-test psql -X -v ON_ERROR_STOP=1 -U nnpro -d nnpro_test < database/seeds/dev_seed.sql
```

The `db-test` service uses an ephemeral database (`tmpfs`), separate from the regular `db` service. The seed has no effect on automated tests run with another Compose project name.
