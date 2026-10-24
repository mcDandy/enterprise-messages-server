# Enterprise Messages – backend

Spring Boot (Java 21), PostgreSQL 17, Docker Compose, HTTPS/WSS.

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

## Tests

```sh
docker compose -p nnpro-tests --profile test run --rm --build tests
```

Tests use a separate database. Remove the test containers afterwards with
`docker compose -p nnpro-tests --profile test down`.

## Existing database upgrade

Fresh databases initialize automatically and need no migrations. For a database
that has already applied migrations 001 and 002, **back up PostgreSQL first**:

```sh
docker compose stop backend
docker compose --profile migrate run --rm migrate
docker compose up -d --build
```

The migration runner applies 003 or skips it if already applied. For older
databases, see [DATABASE_README.md](DATABASE_README.md). Do not use
`docker compose down -v` unless you intend to delete the database volume.

## Configuration

Set `HTTPS_PORT`, `TLS_HOST` and `TLS_CERTS_DIR` in `.env` if needed. For a
physical Android device, set `TLS_HOST` to the PC's reachable IP **before the
first start**; changing it later does not regenerate an existing certificate.
The self-signed `dev_server.crt` must be trusted by Android. Use a trusted
certificate outside development.

For independent installations use separate Compose project names, HTTPS ports,
certificate directories and credentials (configured in each environment file):

```sh
docker compose -p firma_a --env-file .env.firma_a up -d --build
docker compose -p firma_b --env-file .env.firma_b up -d --build
```

Each installation has its own PostgreSQL volume and Docker network.
The optional `compose.dev.yaml` provides a Gradle `bootRun` development setup.
Firebase push notifications are not available.

The Compose commands are cross-platform. On Linux with SELinux (e.g. Fedora),
bind-mount permissions may require additional configuration.
