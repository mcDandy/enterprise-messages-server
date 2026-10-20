param(
    [string]$LanIp = ""
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$certDir = Join-Path $root "certs"
$keystore = Join-Path $certDir "keystore.p12"

New-Item -ItemType Directory -Path $certDir -Force | Out-Null
if (Test-Path $keystore) {
    throw "Certificate already exists: $keystore. Not overwriting it."
}

$keytool = "keytool"
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin/keytool.exe"))) {
    $keytool = Join-Path $env:JAVA_HOME "bin/keytool.exe"
}

$san = "SAN=DNS:localhost,IP:127.0.0.1"
if ($LanIp) {
    $parsed = $null
    if (-not [System.Net.IPAddress]::TryParse($LanIp, [ref]$parsed)) {
        throw "Invalid LAN IP: $LanIp"
    }
    $san += ",IP:$LanIp"
}

Write-Host "Creating a development-only self-signed TLS certificate with $san"
Write-Host "Choose a keystore password when prompted (at least 6 characters)."
Write-Host "Put exactly that password into TLS_KEYSTORE_PASSWORD in your .env."
& $keytool -genkeypair -alias ems-backend -keyalg RSA -keysize 3072 `
    -storetype PKCS12 -keystore $keystore -validity 90 `
    -dname "CN=localhost, OU=EMS, O=UPCE, C=CZ" -ext $san
if ($LASTEXITCODE -ne 0) { throw "keytool failed" }

Write-Host "Certificate created at $keystore"
Write-Host "Exporting the public certificate (without private key)..."
& $keytool -exportcert -rfc -alias ems-backend -keystore $keystore `
    -file (Join-Path $certDir "dev-server.crt")
if ($LASTEXITCODE -ne 0) { throw "Failed to export the public certificate" }
Write-Host "Public certificate: $(Join-Path $certDir 'dev-server.crt')"
Write-Host "NOTE: Android clients must explicitly trust this certificate for development."
