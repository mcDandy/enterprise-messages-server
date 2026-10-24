# Enterprise Messages – backend

Spring Boot (Java 21), PostgreSQL 17, Docker Compose, HTTPS/WSS.

## Start

Requires Docker with Compose. Java, Gradle and PostgreSQL are included in containers.

Copy `.env.example` to `.env`, then set `DB_PASSWORD`, `JWT_SECRET` and
`TLS_KEYSTORE_PASSWORD` (Windows: `Copy-Item .env.example .env`; Linux: `cp .env.example .env`).

```sh
docker compose up -d --build
```

This starts PostgreSQL, creates a development TLS certificate on first run,
and starts the backend at `https://localhost:8443`.
Use `docker compose logs -f backend` to inspect logs.

## Tests

```sh
docker compose -p nnpro-tests --profile test run --rm --build tests
```

The test database is separate from the application database. To clean it up:
`docker compose -p nnpro-tests --profile test down`.

## Database upgrade

New databases initialize automatically. For an existing database already at
schema version 002, **back up the database**, then run:

```sh
docker compose stop backend
docker compose --profile migrate run --rm migrate
docker compose up -d --build
```

The migration command applies 003, or skips it if already applied.
Older databases may require migrations 001/002 first; see [DATABASE_README.md](DATABASE_README.md).
Never run `docker compose down -v` unless you intend to delete database data.

## Configuration

`.env` controls the external port (`HTTPS_PORT`), certificate hostname/IP
(`TLS_HOST`) and certificate directory (`TLS_CERTS_DIR`). A physical Android
device must connect using an address included in the certificate SAN.
The self-signed certificate is for development; Android must explicitly trust
`dev_server.crt`. Use a trusted certificate for company deployment.

Multiple independent installations on one machine use different Compose project
names, HTTPS ports, certificate directories and credentials:

```sh
docker compose -p firma_a --env-file .env.firma_a up -d --build
docker compose -p firma_b --env-file .env.firma_b up -d --build
```

The two installations have independent PostgreSQL volumes and Docker networks.
For optional Gradle `bootRun` development use `compose.dev.yaml`.
Firebase push notifications are not implemented.
