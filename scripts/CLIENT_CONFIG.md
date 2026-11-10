# Export a server's public Android connection configuration

Every installation chooses its own external `TLS_HOST` and `HTTPS_PORT` in
`.env`. After `cert-init` has created `TLS_CERTS_DIR/certificate.pem`, run from
the repository root (Python 3, standard library only):

```powershell
py -3 scripts/export-client-config.py
```

On Linux/macOS use `python3 scripts/export-client-config.py`. For a named
installation use `--env .env.firma_a --output client-config/firma_a`.

The script creates `server.json` (host, port, HTTPS URL, WSS URL and certificate
SHA-256 fingerprint) plus `server.crt` (the **public PEM certificate**, never a
PKCS#12 keystore or a private key). The output directory is ignored by Git.
