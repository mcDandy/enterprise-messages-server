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

## Start the server and hand the certificate to the client in one step

`start-and-export-cert` runs those services in order (`cert-init` → `client-config`
→ `backend`), reads the certificate the server actually offers, and copies
`devserver.crt` into the Android checkout next to this repository. It never
rotates a certificate by itself.

```powershell
.\scripts\start-and-export-cert.ps1 -LanIp 192.168.1.115 -RotateCert
```

```bash
bash scripts/start-and-export-cert.sh --lan-ip 192.168.1.115 --rotate-cert
```

`-LanIp` / `--lan-ip` overrides `TLS_HOST` for that run only; write it into
`.env` to keep it. Both variants stop and name the reason when the offered
certificate does not cover `TLS_HOST`, which is exactly what makes a phone time
out against a running server: Android verifies Subject Alternative Names, and
`TLS_HOST` only enters the SAN when a new development certificate is created.
`-RotateCert` / `--rotate-cert` archives the current `keystore.p12` and
`certificate.pem` under `certs/archive/<timestamp>/` and lets `cert-init` issue a
new one, so the previous identity stops working.

The PowerShell variant reads `TLS_CERTS_DIR/certificate.pem` **before** Docker is
called and compares its SAN with the address it is about to ask for. A certificate
that cannot cover it is therefore reported immediately, with the names it does
cover, instead of surfacing as `service "client-config" didn't complete
successfully: exit 1` after the image has been built. With `-RotateCert` the old
key material is archived at that same point, so one run finishes the whole job.
If Compose still fails, the logs of `cert-init` and `client-config` are printed
before the script gives up.

What the client still needs is the certificate as a debug-only trust anchor -
`app/src/main/res/xml/network_security_config.xml` refers to it as
`@raw/devserver`, and the script adds that line when it copies the file - plus
`ACCESS_LOCAL_NETWORK` on Android 17, without which every connection to a LAN
address is dropped without an error.
