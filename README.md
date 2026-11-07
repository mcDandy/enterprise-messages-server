# Enterprise Messages – backend

Spring Boot (Java 21), PostgreSQL 17 + PGMQ, Docker Compose, HTTPS/WSS.

## Start

Install Docker with Compose. Copy `.env.example` to `.env` (`Copy-Item .env.example .env`
on Windows PowerShell, `cp .env.example .env` on Linux) and set `DB_PASSWORD`,
`JWT_SECRET` and `TLS_KEYSTORE_PASSWORD`.

```sh
docker compose up -d --build
```

Starts PostgreSQL, creates a development TLS certificate on first run and
starts the backend at `https://localhost:8443`. Logs:
`docker compose logs -f backend`.

## Development mode: hot reload and demo data

Use the same `.env` as for normal Docker startup. To enable Java live reload
and automatic demo data, run this from the repository root:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml --profile dev up --build --watch
```

The development override keeps the same backend, HTTPS/WSS and PostgreSQL
configuration. It runs `bootRun`, automatically recompiles changed Java files
using `dev-compiler`, and restarts Spring Boot through DevTools without rebuilding
the Docker image. Keep the terminal open while editing code.

The one-shot `dev-seed` service automatically creates `demo_alice`, `demo_bob`
and `demo_carol` (password `DevTest123!`), one server and three channels with
memberships. It is safe to rerun without duplicates, but it does not create
E2EE keys; clients register their own keys. **Never use this dev override for
production.** Normal `docker compose up` does not insert demo accounts.

Without `-p`, development and normal Compose in the same checkout share the
same persistent database volume. Do not point this at a database you need to
keep free of demo data. For a second isolated installation with its own DB,
container names, HTTPS port and certificates, see [DEV_DOCKER.md](DEV_DOCKER.md).

Automated tests continue to use the isolated `db-test` service and do not
require the dev override.

## Tests

```sh
docker compose -p nnpro-tests --profile test run --rm --build tests
```

Tests use a separate PGMQ-enabled database (`nnpro_test`). The Gradle suite also
runs `PostgresKeyQueueIntegrationTest` against the real database (enqueue, claim,
recipient/channel filtering, visibility timeout and acknowledgment). It rejects
other database names to avoid writing test packets into application data.
Remove the test containers **and the isolated test volume** afterwards with
`docker compose -p nnpro-tests --profile test down --volumes`.

## Existing database upgrade

Fresh databases initialize automatically from `database/schema.sql`. Existing
PostgreSQL volumes need migrations when backend/database versions change.
**Back up PostgreSQL and stop the backend first** (also when using dev mode):

```sh
docker compose stop backend
docker compose up -d db
docker compose --profile migrate run --rm migrate
docker compose up -d --build backend
```

The migration runner recognizes the legacy table layout and applies any
required migrations **001 → 002 → 003 → 004 → 005** in order. It skips migrations
already reflected in the schema, including on a fresh database. Unknown or
partially upgraded schema states cause an error rather than destructive guesses.
Migration 005 requires the PostgreSQL 17 PGMQ image to be running and refuses
to delete pending tables that still contain unprocessed entries. Before the
upgrade, check and resolve any old `channel_key_pending` or
`channel_rotation_pending` rows; the old ciphertext-only
rows cannot be reconstructed into complete WebSocket packets automatically.

## Configuration

Set `HTTPS_PORT`, `TLS_HOST` and `TLS_CERTS_DIR` in `.env` if needed. For a
physical Android device, set `TLS_HOST` to the PC's reachable IP **before the
first start**; changing it later does not regenerate an existing certificate.
The self-signed `certificate.pem` must be trusted by Android. Use a trusted
certificate outside development.

By default the containers are named `ems-server` and `db`. Set
`BACKEND_CONTAINER_NAME` and `DB_CONTAINER_NAME` in `.env` to rename them.
The Compose project name can also be set using `COMPOSE_PROJECT_NAME`.
Do not change the project name on an existing installation without accounting
for its PostgreSQL volume.

For independent installations use separate Compose project names, HTTPS ports,
container names, certificate directories and credentials. For example, set
`BACKEND_CONTAINER_NAME=firma_a-ems-server` and `DB_CONTAINER_NAME=firma_a-db`
in `.env.firma_a`, and use corresponding unique names in `.env.firma_b`:

```sh
docker compose -p firma_a --env-file .env.firma_a up -d --build
docker compose -p firma_b --env-file .env.firma_b up -d --build
```

Each installation has its own PostgreSQL volume and Docker network.
The optional `compose.dev.yaml` provides a Gradle `bootRun` development setup.
Firebase push notifications are not available.

The Compose commands are cross-platform. On Linux with SELinux (e.g. Fedora),
bind-mount permissions may require additional configuration.

## Persistent key queue (PGMQ)

The database image is `ghcr.io/pgmq/pg17-pgmq:v1.10.0` (PostgreSQL 17).
`schema.sql` initializes PGMQ on new databases; migration 005 does the
same on existing databases and removes the old pending tables only when
empty. The migrator also rejects a partially migrated state with both
PGMQ queues and old pending tables still present.

Two queues are used: `nnpro_key_delivery` stores complete encoded key packets
for individual recipients, and `nnpro_key_rotation` stores rotation triggers.
`PostgresKeyQueue` is the Java SQL adapter. Its filtered `pgmq.read` call is
an experimental PGMQ API; keep the extension version pinned and test upgrades.