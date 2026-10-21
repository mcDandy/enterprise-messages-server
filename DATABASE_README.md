# Enterprise Messages: Docker, PostgreSQL, TLS and tests

The default Docker Compose configuration runs one Java 21 /
Spring Boot JAR per backend container.

For local development, compose.dev.yaml provides an alternative
Gradle bootRun mode without rebuilding the Docker image after
ordinary Java source changes.

## Initial setup

1. Copy `.env.example` to `.env`. Set `DB_PASSWORD`, a random `JWT_SECRET`
   (32+ bytes), and `TLS_KEYSTORE_PASSWORD` (choose your own secrets, do not commit them).

2. Generate a local development certificate **before starting Compose**.
   Java JDK (`keytool`) is required.

   **Windows (PowerShell):**

   ```powershell
   .\scripts\create-dev-certificate.ps1
   ```

   With a LAN IP for connecting from an Android device:

   ```powershell
   .\scripts\create-dev-certificate.ps1 -LanIp 192.168.1.115
   ```

   **Linux / macOS (Bash):**

   ```bash
   ./scripts/create-dev-certificate.sh
   ```

   With a LAN IP:

   ```bash
   ./scripts/create-dev-certificate.sh 192.168.1.115
   ```

   Replace the sample IP with your computer's LAN IP.

   Both scripts generate `certs/keystore.p12` and export
   `certs/dev-server.crt`. Neither overwrites an existing keystore.

   Enter a password when prompted by `keytool` and set the same
   value as `TLS_KEYSTORE_PASSWORD` in `.env`.

   Certificates and private keys in `certs/` are ignored by Git.
   The exported `.crt` contains only the public certificate and
   can be used by the Android developer for debug-only trust.

   Self-signed certificates are not trusted automatically.
   Android clients must explicitly trust the certificate and
   connect using a hostname or IP included in its SAN.


3. Start the services:

   ```powershell
   docker compose up -d --build
   docker compose ps
   docker compose port backend 8443
   ```

   The last command prints the actual host port. Use `https://localhost:<PORT>`
   for local clients (or `https://YOUR_PC_IP:<PORT>` for a phone). The backend
   listens on HTTPS/WSS port **8443 inside the container**. Docker assigns a
   free host port for each backend copy; there is no port collision.

   **Development certificate warning:** A self-signed certificate is *not trusted*
   by Android/normal browsers automatically. The Android debug client must trust
   the certificate explicitly and must connect via a hostname/IP included in SAN.
   Do not disable TLS certificate/hostname verification in production. Use a
   certificate signed by a trusted CA for an actual deployment.

## More than one backend

On one host, the simplest way to run separate backend containers connected to
one PostgreSQL is:

```powershell
docker compose up -d --scale backend=3
docker compose ps
docker compose port --index 1 backend 8443
docker compose port --index 2 backend 8443
```

This is optional and does **not** imply Kubernetes or automatic scaling. Each
instance receives a separate container writable layer. There is no `.:/app`
read-write source mount and thus no shared Gradle build directory to corrupt.

You can also launch separate Compose **projects** using `-p projectA` and
`-p projectB`; Compose keeps their internal networks and named database volumes
separate. Such projects do **not** share a database by default. For multiple
physical hosts sharing one database, specify a secure reachable `DB_URL` and
network-level restrictions/VPN; do not expose PostgreSQL publicly.

**Important:** Multiple live WebSocket backend instances require the app to
coordinate connected clients (e.g. a broker) if messages must cross instances.
Docker cannot solve this application-level requirement.

## PostgreSQL and Adminer

Postgres is reachable as `db:5432` from the Compose network only; it has no
host-published port. To enable Adminer when needed:

```powershell
docker compose --profile tools up -d adminer
```

Adminer: http://localhost:8081 (login server `db`, user `nnpro`, DB `nnpro`).
Or access `psql` via `docker compose exec db psql -U nnpro -d nnpro`.

`database/schema.sql` initializes **only an empty database volume**.
Changing it later does NOT change an existing DB. For existing volumes from
older schema versions, run migration script `database/migrations/001_sync_app_user.sql`
after backup, e.g.:

```powershell
Get-Content .\database\migrations\001_sync_app_user.sql -Raw | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U nnpro -d nnpro
```

### Migration 002: Channel tables and message receipts

Existing databases must also run
`database/migrations/002_update_message_schema.sql`
after migration 001.

Stop the backend and back up the database before migrating.

```powershell
Get-Content -Raw .\database\migrations\002_update_message_schema.sql |
    docker compose exec -T db psql -v ON_ERROR_STOP=1 -U nnpro -d nnpro
```

This migration renames the channel tables and changes message receipts
to store the last delivered and read message per user and channel.

Original per-message receipts are preserved in `message_receipt_legacy`.

Migrations are intended to run only once. Do not run either migration
on a fresh database initialized from the `database/schema.sql`.

Do **not** use `docker compose down -v` on a database containing data you want.
Hibernate is set to `ddl-auto=validate` and does not silently rewrite the schema.


## Development mode (without rebuilding the Docker image)

For everyday backend development, use `compose.dev.yaml` together with
the regular `compose.yaml`. This starts Spring Boot through Gradle `bootRun`
instead of running the prebuilt JAR.

First startup:

```powershell
docker compose -p nnpro-dev -f compose.yaml -f compose.dev.yaml up -d --build backend
```

After modifying Java source files, restart the backend:

```powershell
docker compose -p nnpro-dev -f compose.yaml -f compose.dev.yaml restart backend
```

The source directory is bind-mounted read-only into the container.
Gradle recompiles the modified source files when `bootRun` starts.
A new Docker image is NOT required for ordinary Java changes.

View backend logs:

```powershell
docker compose -p nnpro-dev -f compose.yaml -f compose.dev.yaml logs --tail=50 backend
```

Find the dynamically assigned HTTPS port:

```powershell
docker compose -p nnpro-dev -f compose.yaml -f compose.dev.yaml port backend 8443
```

The development environment has its own PostgreSQL volume and Docker
network because it uses the separate Compose project name `nnpro-dev`.

The regular `compose.yaml` remains the deployment configuration:
it builds a JAR and supports running multiple independent backend instances.

Note: If DB_URL, DB_USERNAME or DB_PASSWORD were previously configured
in the terminal for local testing, clear or correct them before running
Docker Compose. Shell environment variables take precedence over `.env`.

## Running integration tests locally (Windows / IntelliJ)

The isolated test database can also be used by tests running directly
on Windows, including IntelliJ IDEA.

Start the test PostgreSQL:

```powershell
docker compose -p nnpro-tests --profile test up -d db-test
```

The test database is accessible at `127.0.0.1:55432` on the host.

Configure these environment variables in the IntelliJ test/Gradle
run configuration:

- DB_URL=jdbc:postgresql://127.0.0.1:55432/nnpro_test
- DB_USERNAME=nnpro
- DB_PASSWORD=nnpro_local_test

Use the actual TEST_DB_PASSWORD value if it was overridden in `.env`.

To run tests from Windows PowerShell, set the same environment
variables and execute:

```powershell
.\gradlew.bat test --rerun-tasks --console=plain
```

The database must remain running while IntelliJ tests execute.

After testing:

```powershell
docker compose -p nnpro-tests --profile test down
```

The test database uses disposable storage and is completely separate
from the regular application database.


## Isolated PostgreSQL integration tests in Docker

The test profile uses a separate disposable PostgreSQL service (`db-test`) with
an in-memory data directory; it never touches the normal `postgres_data` volume.

```powershell
docker compose -p nnpro-tests --profile test run --rm --build tests
docker compose -p nnpro-tests --profile test down
```

The `tests` service runs JUnit integration tests against
`jdbc:postgresql://db-test:5432/nnpro_test`. The build image includes JDK 21,
Gradle and test sources; the runtime backend image only contains the JRE and JAR.

## Optional Firebase credentials

Without Firebase credentials, the app can start but push notifications are not
configured. If you have the real `firebase-service-account.json`, keep it out
of Git and use the optional Compose override:

```powershell
docker compose -f compose.yaml -f compose.firebase.yaml up -d --build
```

This binds the credentials **read-only**, and sets `FIREBASE_CONFIG_PATH` that
`FirebaseConfig` reads as the `firebase.config.path` property.

## Security and known limits

- `JWT_SECRET` must be distinct from test/example values; never commit `.env`.
- The server currently has a fallback JWT secret in `JwtService`; production
  deployments should remove that fallback in a coordinated backend change.
- TLS provides transport security, not end-to-end encryption. The Android
  client needs a correct HTTPS/WSS URL and trusted certificate.
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`: schema changes require migrations.
- Certificate contents and key must remain private; rotating a certificate
  requires new certificates to be trusted by the appropriate clients.
