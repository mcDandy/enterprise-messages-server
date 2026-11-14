#!/bin/sh
# Called by docker compose after cert-init. No local Java/Gradle is needed.
set -eu

# Compose resolves these values from the installation's .env. Never mount
# the full .env here: it also contains credentials unrelated to client setup.
printf 'TLS_HOST=%s\nHTTPS_PORT=%s\nTLS_CERTS_DIR=/certs\n' \
  "${TLS_HOST:-localhost}" "${HTTPS_PORT:-8443}" > /tmp/client-config.env

exec gradle --offline --no-daemon --project-dir /workspace exportClientConfig \
  -PclientConfigEnv=/tmp/client-config.env \
  -PclientConfigOutput=/client-config
