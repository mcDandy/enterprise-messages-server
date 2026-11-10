#!/usr/bin/env python3
# Export one installation's public server connection details for Android.

import argparse
import hashlib
import ipaddress
import json
import re
import shutil
import ssl
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HOST_RE = re.compile(r"^(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$")


def env_values(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_no, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            continue
        key, raw = line.split("=", 1)
        key = key.strip()
        if key not in ("TLS_HOST", "HTTPS_PORT", "TLS_CERTS_DIR"):
            continue
        raw = raw.strip()
        if raw.startswith(('"', "'")):
            quote = raw[0]
            end = raw.find(quote, 1)
            if end == -1 or raw[end + 1:].strip() not in ("",) and not raw[end + 1:].lstrip().startswith("#"):
                raise ValueError(f"Invalid quoted {key} at {path}:{line_no}")
            values[key] = raw[1:end]
        else:
            # Compose .env treats a space followed by # as a comment.
            values[key] = re.split(r"\s+#", raw, maxsplit=1)[0].strip()
    return values


def validate_host(host: str) -> tuple[str, bool]:
    if not host:
        raise ValueError("TLS_HOST is empty")
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        if not HOST_RE.fullmatch(host) or ".." in host or any(not label or len(label) > 63 or label.startswith("-") or label.endswith("-") for label in host.split(".")):
            raise ValueError("TLS_HOST must be a DNS name or IPv4 address, without scheme or port")
        if re.fullmatch(r"[0-9.]+", host):
            raise ValueError("Invalid IPv4 address in TLS_HOST")
        return host, False
    if address.version != 4:
        raise ValueError("IPv6 is not supported by the existing certificate generator")
    return str(address), True


def check_certificate_with_openssl(cert: Path, host: str, is_ip: bool) -> bool:
    exe = shutil.which("openssl")
    if not exe:
        print("WARNING: OpenSSL not installed; certificate hostname/expiry was NOT verified.", file=sys.stderr)
        return False
    check_name = "-checkip" if is_ip else "-checkhost"
    result = subprocess.run([exe, "x509", "-in", str(cert), "-noout", check_name, host], capture_output=True, text=True)
    if result.returncode != 0 or "does match" not in result.stdout:
        raise ValueError(f"Public certificate does not match TLS_HOST={host}: {result.stdout.strip()} {result.stderr.strip()}")
    expiry = subprocess.run([exe, "x509", "-in", str(cert), "-noout", "-checkend", "0"], capture_output=True, text=True)
    if expiry.returncode != 0:
        raise ValueError("Public certificate is expired")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="Export Android server URL and public TLS certificate (never the private key).")
    parser.add_argument("--env", type=Path, default=ROOT / ".env", help="Server installation .env (default: .env in repo root)")
    parser.add_argument("--output", type=Path, default=ROOT / "client-config", help="Output directory (default: client-config/)")
    args = parser.parse_args()
    env_path = args.env.resolve()
    if not env_path.is_file():
        parser.error(f"Configuration file not found: {env_path}")
    conf = env_values(env_path)
    host, is_ip = validate_host(conf.get("TLS_HOST", "localhost"))
    port_raw = conf.get("HTTPS_PORT", "8443")
    try:
        port = int(port_raw)
    except ValueError as exc:
        raise ValueError(f"Invalid HTTPS_PORT: {port_raw!r}") from exc
    if not 1 <= port <= 65535:
        raise ValueError("HTTPS_PORT must be 1–65535")

    cert_dir = Path(conf.get("TLS_CERTS_DIR", "./certs"))
    if not cert_dir.is_absolute():
        cert_dir = ROOT / cert_dir  # Compose interprets bind paths relative to project root.
    public_cert = cert_dir.resolve() / "certificate.pem"
    if not public_cert.is_file():
        raise ValueError(f"Public certificate missing: {public_cert}. Start cert-init first; never export keystore.p12 to Android.")

    pem = public_cert.read_text(encoding="ascii")
    if pem.count("-----BEGIN CERTIFICATE-----") != 1 or pem.count("-----END CERTIFICATE-----") != 1:
        raise ValueError("Expected exactly one PEM certificate in certificate.pem")
    try:
        der = ssl.PEM_cert_to_DER_cert(pem)
    except ValueError as exc:
        raise ValueError(f"Malformed public certificate: {exc}") from exc
    checked = check_certificate_with_openssl(public_cert, host, is_ip)

    origin = f"{host}:{port}"
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    manifest = {
        "version": 1,
        "host": host,
        "port": port,
        "httpsBaseUrl": f"https://{origin}",
        "webSocketUrl": f"wss://{origin}/wss",
        "certificateFile": "server.crt",
        "certificateSha256": hashlib.sha256(der).hexdigest(),
    }
    (output / "server.crt").write_text(pem, encoding="ascii")
    (output / "server.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {output / 'server.json'} and {output / 'server.crt'}")
    print(f"URL: {manifest['httpsBaseUrl']}  SHA-256: {manifest['certificateSha256']}")
    if host in ("localhost", "127.0.0.1", "10.0.2.2"):
        print("WARNING: This address may not be reachable from a physical Android device.", file=sys.stderr)
    if not checked:
        print("Verify the certificate SAN and expiry before distributing this configuration.", file=sys.stderr)
    print("IMPORTANT: Deliver this bundle through a trusted channel; it does NOT establish trust by itself.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
