#!/bin/sh
# Executed in the eclipse-temurin:21-jdk Docker service cert-init.
# Do not run this script directly on Windows; no local Java is necessary.
set -eu

cert_dir=/certs
keystore="$cert_dir/keystore.p12"
public_cert="$cert_dir/dev_server.crt"
pass="${TLS_KEYSTORE_PASSWORD:?TLS_KEYSTORE_PASSWORD is required}"
host="${TLS_HOST:-localhost}"

if [ "${#pass}" -lt 6 ]; then
  echo "ERROR: TLS_KEYSTORE_PASSWORD must contain at least 6 characters." >&2
  exit 1
fi

# Avoid malformed SAN values and accidental option injection.
case "$host" in
  ''|*[!a-zA-Z0-9._-]*)
    echo "ERROR: TLS_HOST must be a DNS hostname or IPv4 address." >&2
    exit 1
    ;;
esac

mkdir -p "$cert_dir"
umask 077

if [ -e "$keystore" ]; then
  keytool -list -storetype PKCS12 -keystore "$keystore" \
    -storepass "$pass" -alias ems-backend >/dev/null || {
      echo "ERROR: Existing keystore cannot be opened using TLS_KEYSTORE_PASSWORD or alias ems-backend." >&2
      echo "Keep the private key safe; fix .env rather than deleting it." >&2
      exit 1
    }
  if [ ! -f "$public_cert" ]; then
    keytool -exportcert -rfc -storetype PKCS12 -alias ems-backend \
      -keystore "$keystore" -storepass "$pass" -file "$public_cert"
    chmod 644 "$public_cert"
  fi
  echo "Existing TLS keystore reused; not changing its identity/SAN."
  echo "If the server IP/hostname changed, inspect the certificate and explicitly rotate it."
  exit 0
fi

if [ -e "$public_cert" ]; then
  echo "ERROR: Public certificate exists without its keystore; resolve this inconsistency manually." >&2
  exit 1
fi

san='SAN=DNS:localhost,IP:127.0.0.1,IP:10.0.2.2'
if [ "$host" != localhost ] && [ "$host" != 127.0.0.1 ] && [ "$host" != 10.0.2.2 ]; then
  if printf '%s' "$host" | grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$'; then
    san="$san,IP:$host"
  else
    san="$san,DNS:$host"
  fi
fi

echo "Generating DEV-ONLY self-signed TLS certificate for: $san"
keytool -genkeypair -alias ems-backend -keyalg RSA -keysize 3072 \
  -storetype PKCS12 -keystore "$keystore" -validity 90 \
  -storepass "$pass" -keypass "$pass" \
  -dname 'CN=localhost, OU=EMS, O=UPCE, C=CZ' -ext "$san"
chmod 600 "$keystore"
keytool -exportcert -rfc -alias ems-backend -keystore "$keystore" \
  -storetype PKCS12 -storepass "$pass" -file "$public_cert"
chmod 644 "$public_cert"
echo "Created private keystore and public dev_server.crt in certs/."
echo "Android debug clients must explicitly trust dev_server.crt and use a name/IP in SAN."
