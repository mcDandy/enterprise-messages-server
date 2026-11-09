# Enterprise Messages – backend

Spring Boot (Java 21), PostgreSQL 17 + PGMQ, Docker Compose, HTTPS/WSS.

## Start

Install Docker with Compose. Copy `.env.example` to `.env` (`Copy-Item .env.example .env`
on Windows PowerShell, `cp .env.example .env` on Linux) and provide unique,
strong values for `DB_PASSWORD`, `JWT_SECRET`, `SWAGGER_PASSWORD` and
`TLS_KEYSTORE_PASSWORD`. The template leaves these required secrets empty so the
stack cannot start accidentally with shared placeholder credentials.

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

Start the isolated test database and its migration service explicitly;
ordinary `docker compose up` does not enable the `test` profile:

```powershell
docker compose --profile test up -d db-test migrate-test
docker compose --profile test ps -a
```

Confirm that `migrate-test` completed successfully before running tests.
In IntelliJ, run tests with the Gradle test runner; test configuration defaults
to `jdbc:postgresql://localhost:55432/nnpro_test`, user `nnpro`, password
`nnpro_local_test`. Override these with `TEST_DB_URL`, `TEST_DB_USERNAME`, and
`TEST_DB_PASSWORD` if needed. The test database is separate from the backend's
`nnpro` database and must only contain disposable test data.

The test database uses PostgreSQL 17 + PGMQ. The test migration runner brings
older schemas up to migration 006; the Gradle suite includes
`PostgresUserQueueIntegrationTest`, which uses the real database to verify
per-recipient queues, opaque frames, visibility timeouts, and acknowledgments. The test
refuses to run against database names other than `nnpro_test`.

WebSocket integration tests verify key-rotation delivery through authenticated sessions.
To run the unicast rotation test only:

```powershell
.\gradlew.bat test --tests "*UnicastKeyRotationWssIntegrationTest"
```

### Run tests with Docker Compose

```sh
docker compose --profile test run --rm --build tests
```

Docker Compose starts `db-test`, applies required migrations through
`migrate-test`, and then runs the Gradle suite. Tests insert database fixtures;
reusing the same test volume across full suite runs can cause unique-constraint
failures. For an isolated test project, use `-p nnpro-gradle-check`, optionally
set a free `TEST_DB_HOST_PORT`, and remove that project's containers and volumes
after testing:

```powershell
$env:TEST_DB_HOST_PORT = "55434"
docker compose -p nnpro-gradle-check --profile test run --build --rm tests
docker compose -p nnpro-gradle-check --profile test down --volumes
```

Do not run `down --volumes` on your ordinary development Compose project:
that could also delete its persistent application database.

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
required migrations **001 → 002 → 003 → 004 → 005 → 006** in order. It skips migrations
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

By default the containers are named `ems-server`, `db` and `cert-init`. Set
`BACKEND_CONTAINER_NAME`, `DB_CONTAINER_NAME` and `CERT_INIT_CONTAINER_NAME`
in `.env` to rename them. Docker container names must be unique across all
Compose projects on the same host. The Compose project name can also be set
using `COMPOSE_PROJECT_NAME`.
Do not change the project name on an existing installation without accounting
for its PostgreSQL volume.

For independent installations on one host, use separate Compose project names,
HTTPS ports, all three container names, certificate directories and credentials.
For example, set `BACKEND_CONTAINER_NAME=firma_a-ems-server`,
`DB_CONTAINER_NAME=firma_a-db`, `CERT_INIT_CONTAINER_NAME=firma_a-cert-init`,
`HTTPS_PORT=18443` and `TLS_CERTS_DIR=./certs/firma_a` in `.env.firma_a`.
Use different values in `.env.firma_b`:

```sh
docker compose -p firma_a --env-file .env.firma_a up -d --build
docker compose -p firma_b --env-file .env.firma_b up -d --build
```

Each installation has its own PostgreSQL volume and Docker network.
The optional `compose.dev.yaml` provides a Gradle `bootRun` development setup.

The Compose commands are cross-platform. On Linux with SELinux (e.g. Fedora),
bind-mount permissions may require additional configuration.

## On-premise server address and TLS

Each organization deploys its own backend and PostgreSQL database. The Android
client connects to the **address of that organization's server**, for example
`https://ems.example.org`; it must not rely on a developer's fixed address.
The binary WebSocket endpoint is `wss://ems.example.org/wss` when HTTPS is
exposed at the standard port. A deployment behind a reverse proxy must forward
both HTTPS requests and WebSocket upgrades. Docker's service name `db` is only
used internally by containers, not by Android clients.

For an installation reachable on an internal network or over VPN, administrators
must provide a DNS name (preferred) or reachable IPv4 address and ensure that
firewall and routing rules allow clients to reach the configured HTTPS port.
`HTTPS_BIND_IP` selects the host interface on which Docker publishes that port;
`HTTPS_PORT` selects the published host port. Neither setting automatically
creates a DNS name or configures a firewall.

The `cert-init` service generates a **development-only**, 90-day self-signed
certificate if no keystore exists. `TLS_HOST` adds the selected DNS name or IPv4
address to the Subject Alternative Name when a new development certificate is
created. Changing `TLS_HOST` does **not** replace an existing certificate.
Android devices must explicitly trust a development certificate before using it.

For a managed installation, provision a trusted certificate for the chosen
hostname in a PKCS#12 keystore named `keystore.p12` within `TLS_CERTS_DIR`.
The keystore must use the alias `ems-backend` and the password configured in
`TLS_KEYSTORE_PASSWORD`. The existing-keystore path in `cert-init` checks these
values and reuses the certificate. Verify the certificate chain and SAN, and
arrange renewal and a backend restart before expiry. Protect the private key,
`.env` file and PostgreSQL backups. Do not distribute one shared private key or
JWT signing secret to multiple organizations.

The server provides the address and TLS endpoint, but configuring the Android
client's server URL and certificate trust is a separate client-side requirement.

## Persistent user packet queues (PGMQ)

The database image is `ghcr.io/pgmq/pg17-pgmq:v1.10.0` (PostgreSQL 17).
One queue is allocated per user, named `ems_user_<id>`. The database trigger
creates a queue when a user is registered; migration 006 provisions queues for
existing users and transfers frames from the legacy shared delivery queue.
It refuses to drop legacy rotation triggers or archived messages if any remain;
resolve them before upgrading.

`PostgresUserQueue` stores each complete WebSocket frame as Base64 in JSONB,
with optional channel-scope metadata. The frame's multiplex byte is opaque to
PGMQ. Reads are isolated by queue, not JSON filtering. Message IDs are local to
each user's queue, so acknowledgments require both user ID and message ID.

The queue provides durable transport. Application services control which
packets are enqueued, dispatched, retried, and acknowledged. A successful
WebSocket write alone is not a client-level delivery receipt.