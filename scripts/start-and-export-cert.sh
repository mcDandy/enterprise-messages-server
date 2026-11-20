#!/usr/bin/env bash
# Starts this installation's Compose stack and hands the resulting public TLS certificate
# to the Android client, in one step. Linux/macOS counterpart of start-and-export-cert.ps1;
# no local Java, Python or OpenSSL is required (the SAN check belongs to exportClientConfig,
# which parses the certificate with Java and refuses a mismatch).
#
# Usage:
#   bash scripts/start-and-export-cert.sh [--lan-ip 192.168.1.115] [--env .env]
#                                         [--client-repo ../enterprise-messages-client]
#                                         [--rotate-cert] [--no-start] [--wait 180]
#
# TLS_HOST only reaches the certificate's SAN when a NEW certificate is created, so
# changing it on an existing installation needs --rotate-cert. That invalidates the old
# identity, which is why it is never done silently; the previous key material is archived,
# never deleted.
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$root"

env_file=".env"
lan_ip=""
client_repo=""
rotate_cert=0
no_start=0
wait_seconds=180

while [[ $# -gt 0 ]]; do
  case "$1" in
    --lan-ip)      lan_ip="${2:?--lan-ip needs a value}"; shift 2 ;;
    --env)         env_file="${2:?--env needs a value}"; shift 2 ;;
    --client-repo) client_repo="${2:?--client-repo needs a value}"; shift 2 ;;
    --rotate-cert) rotate_cert=1; shift ;;
    --no-start)    no_start=1; shift ;;
    --wait)        wait_seconds="${2:?--wait needs a value}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -f "$env_file" ]] || { echo "Missing $env_file. Copy .env.example to .env and fill in the secrets first." >&2; exit 1; }

# Only the three keys the services and exportClientConfig read. Comments and blanks never
# answer, so a file ending in a comment still reports the real value; the last definition wins.
read_env() {
  awk -v key="$1" '
    {
      line = $0
      sub(/\r$/, "", line)
      sub(/^[ \t]*export[ \t]+/, "", line)
    }
    line ~ /^[ \t]*#/ { next }
    {
      eq = index(line, "=")
      if (eq < 2) next
      name = substr(line, 1, eq - 1)
      gsub(/[ \t]/, "", name)
      if (name != key) next
      value = substr(line, eq + 1)
      sub(/[ \t]*#.*$/, "", value)
      gsub(/^[ \t]+/, "", value)
      gsub(/[ \t]+$/, "", value)
      gsub(/^"/, "", value)
      gsub(/"$/, "", value)
      gsub(/^\047/, "", value)
      gsub(/\047$/, "", value)
      out = value
    }
    END { print out }
  ' "$env_file"
}

config_host="$(read_env TLS_HOST)"
config_port="$(read_env HTTPS_PORT)"
config_certs="$(read_env TLS_CERTS_DIR)"

host="${lan_ip:-$config_host}"
host="${host:-localhost}"
port="${config_port:-8443}"
certs_dir="${config_certs:-./certs}"

if [[ "$host" == *"://"* || "$host" == *:* ]]; then
  echo "TLS_HOST must be a bare hostname or IPv4 address without scheme or port, got '$host'." >&2
  exit 1
fi

if [[ -n "$lan_ip" ]]; then
  # Compose prefers process variables over the .env file, so this holds for this run only.
  export TLS_HOST="$lan_ip"
  echo "WARNING: TLS_HOST overridden to $lan_ip for this run only; write it into $env_file to keep it."
fi

echo "Installation: ${host}:${port}  certificates: ${certs_dir}"

if [[ "$rotate_cert" -eq 1 ]]; then
  archive="${certs_dir}/archive/$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$archive"
  moved=0
  for name in keystore.p12 certificate.pem; do
    if [[ -e "${certs_dir}/${name}" ]]; then
      mv "${certs_dir}/${name}" "${archive}/${name}"
      echo "Archived ${name} to ${archive}/${name}"
      moved=1
    fi
  done
  if [[ "$moved" -eq 0 ]]; then
    echo "Nothing to rotate under ${certs_dir}; cert-init will create a certificate for ${host}."
  fi
fi

# --- 1. start (cert-init -> client-config -> backend) -----------------------------------------
if [[ "$no_start" -eq 1 ]]; then
  echo "--no-start given: expecting a stack that is already running."
else
  docker compose --env-file "$env_file" up -d --build
fi

# --- 2. wait for HTTPS to answer -------------------------------------------------------------
probe_host="$host"
[[ "$host" == "localhost" || "$host" == "127.0.0.1" ]] && probe_host="127.0.0.1"

deadline=$((SECONDS + wait_seconds))
while (( SECONDS < deadline )); do
  # The subshell closes its own descriptor; nothing to clean up here.
  if (exec 3<>"/dev/tcp/${probe_host}/${port}") 2>/dev/null; then
    echo "HTTPS is answering on ${probe_host}:${port}."
    break
  fi
  sleep 3
done

if ! (exec 3<>"/dev/tcp/${probe_host}/${port}") 2>/dev/null; then
  echo "No TLS answer from ${probe_host}:${port} within ${wait_seconds}s." >&2
  echo "Check: docker compose logs --tail 200 backend" >&2
  exit 1
fi

# --- 3. the exported bundle is the SAN check -------------------------------------------------
# exportClientConfig parses the certificate and fails with
# "Certificate SAN does not match TLS_HOST=..." when it cannot cover the configured address.
exported_crt="client-config/android/res/raw/devserver.crt"
exported_xml="client-config/android/res/values/server_config.xml"

if [[ ! -f "$exported_crt" ]]; then
  echo "Missing ${exported_crt}; the client-config service did not finish." >&2
  echo "Its log says why: docker compose logs --tail 100 client-config" >&2
  echo "If it reported a SAN mismatch, re-run with --rotate-cert to issue a certificate for '${host}'." >&2
  exit 1
fi

echo "Exported certificate covers TLS_HOST '${host}':"
echo "  ${exported_crt}"

# --- 4. hand it to the Android client --------------------------------------------------------
if [[ -z "$client_repo" ]]; then
  guess="$(cd -- "${root}/.." && pwd -P)/enterprise-messages-client"
  [[ -d "${guess}/app/src/main/res" ]] && client_repo="$guess"
fi

if [[ -z "$client_repo" ]]; then
  echo "No Android checkout found next to ${root}; pass --client-repo to copy the certificate." >&2
  exit 0
fi

raw_dir="${client_repo}/app/src/main/res/raw"
mkdir -p "$raw_dir"
cp "$exported_crt" "${raw_dir}/devserver.crt"
echo "Copied the certificate to ${raw_dir}/devserver.crt"

sec_config="${client_repo}/app/src/main/res/xml/network_security_config.xml"
if [[ -f "$sec_config" ]]; then
  if grep -q '@raw/devserver' "$sec_config"; then
    echo "${sec_config} already trusts @raw/devserver."
  elif grep -q '<trust-anchors>' "$sec_config"; then
    tmp="${sec_config}.tmp"
    awk '
      { print }
      !done && /<debug-overrides>/ { in_debug = 1 }
      in_debug && !done && /<trust-anchors>/ {
        print "            <certificates src=\"@raw/devserver\"/>"
        done = 1
      }
    ' "$sec_config" > "$tmp" && mv "$tmp" "$sec_config"
    echo "Added <certificates src=\"@raw/devserver\"/> to ${sec_config}"
  else
    echo "${sec_config} has no <debug-overrides><trust-anchors>; add the @raw/devserver trust anchor by hand." >&2
  fi
else
  echo "No network_security_config.xml under ${client_repo}; the client cannot trust this certificate yet." >&2
fi

# The client owns @string/server_ip / @integer/server_port. server_config.xml declares
# server_host plus its own server_port, so copying it in would collide; show it instead.
if [[ -f "$exported_xml" ]]; then
  echo
  echo "Exported connection settings (${exported_xml}) - match them in the client's strings.xml:"
  cat "$exported_xml"
fi

echo
echo "Rebuild the APK, then on Android 17 grant 'Local network' access to the app on first launch."
