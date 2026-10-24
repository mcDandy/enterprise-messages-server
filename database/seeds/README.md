# Development seed

`dev_seed.sql` populates a **disposable PostgreSQL database** with three demo users, one organizational server, three channels, and memberships. It can be run more than once without duplicating records.

Accounts: `demo_alice`, `demo_bob`, `demo_carol` (password `DevTest123!`). Passwords are BCrypt hashes. **Do not run this seed against a production database.** It does not create `user_key` entries or usable E2EE key pairs; clients must register their own keys for actual encrypted messaging.

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
