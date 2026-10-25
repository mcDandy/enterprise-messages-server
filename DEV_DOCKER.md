# Docker development: automatic Java recompilation and restart

This is a **local developer workflow only**. `compose.yaml` (normal deployment)
remains unchanged. `compose.dev.yaml` runs the Java backend through Gradle
`bootRun` with DevTools and starts a separate continuous Gradle compiler.
Docker Compose Watch syncs `src/` into the compiler; a successful recompile
updates the shared `dev_build` volume. DevTools restarts the running Spring Boot
application (not the Docker container and not the Docker image).

## Default local development quick start

After configuring `.env` as described in the main README, run:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml --profile dev up --build --watch
```

This starts the development stack, including `dev-seed`, on the **same Compose
project/database volume** that normal `docker compose up` would use in this
checkout. Use it only for a local development database. See the next section
for running a second independent installation alongside another stack.

## Isolated second instance (Windows PowerShell)

Update your local `main` and use a separate development branch first. From
project root, with an existing `.env` configured for secrets:

```powershell
# Required if another installation exists: isolate Docker resource names,
# database volume, HTTPS port, and development TLS certificate directory.
$env:DB_CONTAINER_NAME = "nnpro-hotdev-db"
$env:BACKEND_CONTAINER_NAME = "nnpro-hotdev-backend"
$env:CERT_INIT_CONTAINER_NAME = "nnpro-hotdev-cert-init"
$env:HTTPS_PORT = "19443"
$env:TLS_CERTS_DIR = "./certs/hotdev"

docker compose -p nnpro-hotdev -f compose.yaml -f compose.dev.yaml --profile dev up --build --watch
```

Keep this terminal open. Once the backend is running, edit and save a `.java`
file. In the terminal, look for the `dev-compiler` recompilation messages
followed by a DevTools restart of the backend. Check the changed behavior via
the HTTPS endpoint on the configured port. Do **not** expect the first startup
to be fast; it still builds the development Docker images.

View the service logs from another terminal (using the same Compose options):

```powershell
docker compose -p nnpro-hotdev -f compose.yaml -f compose.dev.yaml --profile dev logs -f backend dev-compiler
```

Stop with `Ctrl+C`; explicitly shut down this isolated project using:

```powershell
docker compose -p nnpro-hotdev -f compose.yaml -f compose.dev.yaml --profile dev down
```

Never use `down -v` on a project whose PostgreSQL data you want to keep.

## Limitations

- Java **must be compiled**: syncing `.java` files alone is not sufficient.
- This uses Gradle continuous compilation; it must **not** run with
  `--no-daemon` (the backend's `bootRun --no-daemon` is fine).
- A compilation error means DevTools cannot load that change; fix the error.
- Changing `build.gradle`, other build configuration, dependencies, Dockerfile
  or Compose settings still needs a deliberate rebuild/restart.
- Use Docker Compose 2.22+ (Compose Watch). This solution must be smoke-tested
  with Docker Desktop on the contributors' Windows machines; YAML parsing alone
  cannot prove reliable live reload.
- The compiler is activated only with `--profile dev`; do not use the dev setup
  for production or as a substitute for integration tests.
