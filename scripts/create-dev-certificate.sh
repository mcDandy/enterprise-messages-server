#!/usr/bin/env bash
set -euo pipefail

if (( $# > 1 )); then
  echo "Usage: $0 [LAN_IP]" >&2
  exit 2
fi

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cert_dir="$root/certs"
keystore="$cert_dir/keystore.p12"
public_cert="$cert_dir/certificate.pem"

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then
  keytool="$JAVA_HOME/bin/keytool"
elif command -v keytool >/dev/null 2>&1; then
  keytool="$(command -v keytool)"
else
  echo "Java keytool not found. Install a JDK or add keytool to PATH." >&2
  exit 1
fi

mkdir -p "$cert_dir"
if [[ -e "$keystore" ]]; then
  echo "Certificate already exists: $keystore. Not overwriting it." >&2
  exit 1
fi

san="SAN=DNS:localhost,IP:127.0.0.1,IP:10.0.2.2,IP:172.18.0.1"
if [[ -n "${1:-}" ]]; then
  san+=",IP:$1"
fi

# Make the private key file accessible only to its owner.
umask 077

echo "Creating a development-only self-signed TLS certificate with $san"
echo "Choose a keystore password when prompted (at least 6 characters)."
echo "Put exactly that password into TLS_KEYSTORE_PASSWORD in your .env."
"$keytool" -genkeypair -alias ems-backend -keyalg RSA -keysize 3072 \
  -storetype PKCS12 -keystore "$keystore" -validity 90 \
  -dname "CN=localhost, OU=EMS, O=UPCE, C=CZ" -ext "$san"

echo "Certificate created at $keystore"
echo "Exporting the public certificate (without private key)..."
"$keytool" -exportcert -rfc -alias ems-backend -keystore "$keystore" \
  -file "$public_cert"
chmod 644 "$public_cert"
echo "Public certificate: $public_cert"
echo "NOTE: Android clients must explicitly trust this certificate for development."
