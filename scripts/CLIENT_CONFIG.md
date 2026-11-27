# Export a server's public Android connection configuration

Every installation chooses its own external `TLS_HOST` and `HTTPS_PORT` in
`.env`. Set them before starting the server for the first time.

## Automatic export with Docker Compose

```sh
docker compose up -d --build
```

The `client-config` service runs after `cert-init` and exports the public
connection resources into `client-config/android/res/` in the repository root.
No host JDK or Gradle installation is needed for this Docker workflow.
To export the resources again without starting the backend:

```sh
docker compose run --rm client-config
```

Use the same Compose project and environment file as the installation. Multiple
installations in the same checkout share the output directory; keep each
installation's exported resources separately.

## Manual export with Gradle

After `cert-init` has created `TLS_CERTS_DIR/certificate.pem`, run from the
repository root (Gradle wrapper and JDK 21, no Python or OpenSSL):

```powershell
.\gradlew.bat exportClientConfig
```

On Linux/macOS use `./gradlew exportClientConfig`. For a named installation:

```powershell
.\gradlew.bat exportClientConfig -PclientConfigEnv=.env.firma_a -PclientConfigOutput=client-config/firma_a
```

## Output

Both methods create two Android resource files (relative to the output directory):

- `android/res/values/server_config.xml` – server host, port,
  HTTPS URL and WSS URL.
- `android/res/raw/devserver.crt` – the public PEM certificate,
  never a PKCS#12 keystore or a private key.

These files are intended for the Android client build. The client must consume
the generated server configuration and public certificate without conflicting
resource definitions.
