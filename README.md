# Enterprise Messages – backend

Spring Boot (Java 21), PostgreSQL 17, Docker Compose, HTTPS/WSS.

## Start

Install Docker with Compose. Copy `.env.example` to `.env` (`Copy-Item .env.example .env`
on Windows PowerShell, `cp .env.example .env` on Linux) and set `DB_PASSWORD`,
`JWT_SECRET`, `SWAGGER_PASSWORD` and `TLS_KEYSTORE_PASSWORD` to your own values.

```sh
docker compose up -d --build
```

Starts PostgreSQL, creates a development TLS certificate on first run and
starts the backend at `https://localhost:8443`. Logs:
`docker compose logs -f backend`.
Swagger UI is at `https://localhost:8443/swagger-ui/index.html`; sign in with
username `swagger` and the `SWAGGER_PASSWORD` value from `.env`.

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

### Run functional tests from IntelliJ with Docker PostgreSQL

Start the normal Compose stack. It includes a separate, disposable PostgreSQL
test database on `localhost:55432` and runs the existing migration runner
against it before it reports healthy:

```powershell
docker compose up -d --wait
```

In IntelliJ, run tests with the Gradle test runner; test configuration defaults
to `jdbc:postgresql://localhost:55432/nnpro_test`, user `nnpro`, password
`nnpro_local_test`. Override these with `TEST_DB_URL`, `TEST_DB_USERNAME`, and
`TEST_DB_PASSWORD` if needed. The test DB is separate from the backend's
`nnpro` database and is disposable. The migration runner applies missing
migrations 001–004 to it at startup; its health check only passes once the
current schema is ready. `PointToPointWebSocketFunctionalTest` starts Spring on
a random port and verifies that an authenticated client's unicast key-rotation
packet reaches the intended authenticated recipient. It creates uniquely named
records and cleans them up afterwards.

Run only the point-to-point functional test from a terminal with:

```powershell
.\gradlew.bat test --tests "*PointToPointWebSocketFunctionalTest"
```

### Run tests with Docker Compose

```sh
docker compose run --rm --build tests
```

The Compose test runner uses the same isolated database and waits for migrations
to complete. It can reuse the test database started by `docker compose up`.

## Existing database upgrade

Fresh databases initialize automatically from `database/schema.sql`. Existing
PostgreSQL volumes need migrations when backend/database versions change.
**Back up PostgreSQL and stop the backend first** (also when using dev mode):

```sh
docker compose stop backend
docker compose --profile migrate run --rm migrate
docker compose up -d --build
```

The migration runner recognizes the legacy table layout and applies any
required migrations **001 → 002 → 003 → 004** in order. It skips migrations
already reflected in the schema, including on a fresh database. Unknown or
partially upgraded schema states cause an error rather than destructive guesses.

**Important:** Migration 003 replaces numeric channel-key versions with new
UUIDs. Clients remembering old numeric versions need a separate migration of
their local mapping. For details, see [DATABASE_README.md](DATABASE_README.md).
If you use `compose.dev.yaml --watch`, stop that session first and restart it
with the dev command afterwards instead of starting the normal backend. Never
use `docker compose down -v` unless you intend to delete the database volume.

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
