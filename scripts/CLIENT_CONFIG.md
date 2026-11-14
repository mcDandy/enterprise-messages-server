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

The task creates `server.json` (host, port, HTTPS URL, WSS URL and certificate
SHA-256 fingerprint) plus `server.crt` (the **public PEM certificate**, never a
PKCS#12 keystore or a private key). The output directory is ignored by Git.
