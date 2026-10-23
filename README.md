# Enterprise Messages (server)

Java 21 / Spring Boot 4 backend, PostgreSQL 17, Docker Compose, HTTPS/WSS.
This repository is the backend of the Android end-to-end encrypted messenger.

## First run (Docker only)

1. Install and start Docker Desktop (Windows) or Docker Engine + Compose (Linux).
   **No local Java, Gradle, PostgreSQL or `keytool` required.**
2. In the repository root copy `.env.example` to `.env` and set your own
   `DB_PASSWORD`, `JWT_SECRET` and `TLS_KEYSTORE_PASSWORD` values.
   Windows PowerShell: `Copy-Item .env.example .env`.
   Linux shell: `cp .env.example .env && mkdir -p certs`.
3. For Android on a *physical device*, set `TLS_HOST` in `.env` to the
   **Docker host's reachable LAN IPv4 address** (find it with `ipconfig` on Windows).
   For local Windows testing leave `TLS_HOST=localhost`. Android emulators
   generally use `10.0.2.2` to reach the host, which is included in the dev cert.
4. Start everything:

   ```powershell
   docker compose up -d --build
   docker compose ps
   docker compose logs --tail=60 backend
   ```

The `cert-init` container generates a **development-only** certificate using its
own JDK and stores the private `certs/keystore.p12` *outside* Docker images.
The backend mounts the directory read-only. A subsequent startup **reuses** the
same keystore instead of silently replacing it. PostgreSQL retains data in the
named `postgres_data` volume. The database schema initializes automatically
**only for a new, empty database**.

The default HTTPS endpoint is `https://localhost:8443`. `HTTPS_PORT` in `.env`
changes the host port without changing the internal backend port `8443`.
To run several installations on the same host, each needs a unique Compose
project name, HTTPS_PORT, and TLS_CERTS_DIR. See the section below.

Test with the exported **public** development certificate.

Windows PowerShell:

```powershell
curl.exe -sS -o NUL -w "HTTP %{http_code}, TLS verify: %{ssl_verify_result}\n" --cacert .\certs\dev_server.crt https://localhost:8443/v3/api-docs
```

Linux shell:

```bash
curl -sS -o /dev/null -w 'HTTP %{http_code}, TLS verify: %{ssl_verify_result}\n' --cacert ./certs/dev_server.crt https://localhost:8443/v3/api-docs
```

Expected: HTTP 200 and TLS verify 0 (as verified on the current backend).
The `/actuator/health` endpoint currently returns HTTP 403 because Spring
Security restricts anonymous access; that is not a TLS error. Replace the
URL hostname and port if you configured other values. The certificate validates
only hostnames/IPs included in its Subject Alternative Name (SAN), **not a port**.

On Linux with SELinux enforcing (notably Fedora/RHEL), host-directory bind mounts
may require additional SELinux labeling for container access. If you encounter
`Permission denied` on `certs/` or the mounted script, diagnose the SELinux
labels rather than disabling SELinux or changing private-key permissions to 777.

### Android TLS/WSS

- Android debug builds must explicitly trust `certs/dev_server.crt` (for example
  with `network_security_config.xml` / debug trust configuration).
- For physical devices, PC and Android must be on a reachable network and the
  PC firewall must allow the configured `HTTPS_PORT`.
- If the address changes, the existing certificate's SAN does **not** change.
  Back up and explicitly rotate the dev keystore, then distribute/trust the new
  public certificate. Never delete it casually or ship a private key to clients.
- `dev_server.crt` uses underscores so Android's `res/raw` resource filename
  conventions are satisfied. The Java keystore alias `ems-backend` is unrelated
  to Android resource naming and does not invalidate TLS.
- For a real company deployment, **provide a trusted company/public-CA certificate**
  rather than relying on an automatically generated self-signed development cert.

## Multiple independent company installations on one machine

Each installation is its own Compose project with its own PostgreSQL volume,
Docker network and TLS private key. Use a separate configuration file per
company, each with unique DB_PASSWORD, JWT_SECRET and TLS_KEYSTORE_PASSWORD.
For example, add these values to separate copies of `.env.example`:

```dotenv
# .env.firma_a (additional settings)
TLS_HOST=chat-a.example.internal
HTTPS_PORT=8443
TLS_CERTS_DIR=./certs/firma_a
```

```dotenv
# .env.firma_b (additional settings)
TLS_HOST=chat-b.example.internal
HTTPS_PORT=9443
TLS_CERTS_DIR=./certs/firma_b
```

Start the installations independently:

```sh
docker compose -p ems_firma_a --env-file .env.firma_a up -d --build
docker compose -p ems_firma_b --env-file .env.firma_b up -d --build
```

Reuse the same `-p` and `--env-file` arguments when administering either
installation. A different Compose project name isolates database storage;
a different TLS_CERTS_DIR isolates the TLS private key. On a shared host,
HTTPS_PORT must differ if the bind IP is the same. Hostnames must resolve
and be present in the TLS certificate SAN; replace examples with real names.
Existing keys are not regenerated when TLS_HOST changes. For production,
use a trusted public/company CA certificate instead of a development cert.
Never run `docker compose down -v` on a project whose data you want to keep.

This supports *independent installations*, not multiple Spring Boot replicas
sharing one company database. A replicated backend also needs reverse proxy
load balancing and cross-instance WebSocket coordination.

## Database tests (isolated Docker environment)

```powershell
docker compose -p nnpro-tests --profile test run --rm --build tests
docker compose -p nnpro-tests --profile test down
```

The `tests` service runs Gradle inside Docker against an independent PostgreSQL
container `db-test` with temporary storage. It does not need local IntelliJ or
an exposed application database. **Do not use `down -v` on your actual project.**

## Useful commands

| Purpose | Command |
| --- | --- |
| Start/build | `docker compose up -d --build` |
| Status | `docker compose ps` |
| Backend logs | `docker compose logs -f backend` |
| Stop without deleting DB | `docker compose stop` |
| Optional Adminer | `docker compose --profile tools up -d adminer` |
| Inspect DB tables | `docker compose exec db psql -U nnpro -d nnpro -c "\\dt"` |

The optional Adminer interface is available only at `http://localhost:8081`.

### Existing databases and advanced development

`database/schema.sql` is **not a migration runner**. Old databases require
explicit migration after backup; see [DATABASE_README.md](DATABASE_README.md).
A dedicated `compose.dev.yaml` permits Gradle `bootRun` inside Docker.

One default backend instance uses a **fixed external port** for Android. To run
multiple backend instances on one host, introduce a reverse proxy/load balancer
with one stable public HTTPS/WSS endpoint. Cross-instance WebSocket delivery
also needs application-level coordination; scaling containers alone is insufficient.
