# Export a server's public Android connection configuration

Every installation chooses its own external `TLS_HOST` and `HTTPS_PORT` in
`.env`. After `cert-init` has created `TLS_CERTS_DIR/certificate.pem`, run from
the repository root (Gradle wrapper and JDK 21, no Python or OpenSSL):

```powershell
.\gradlew.bat exportClientConfig
```

On Linux/macOS use `./gradlew exportClientConfig`. For a named installation:

```powershell
.\gradlew.bat exportClientConfig -PclientConfigEnv=.env.firma_a -PclientConfigOutput=client-config/firma_a
```

The task creates two Android resource files:

- `android/res/values/server_config.xml` – server host, port,
  HTTPS URL and WSS URL.
- `android/res/raw/devserver.crt` – the public PEM certificate,
  never a PKCS#12 keystore or a private key.

Copy the contents of `client-config/android/res/` into
the Android `app/src/main/res/` directory.
