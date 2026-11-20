<#
.SYNOPSIS
    Starts this installation's Compose stack and hands the resulting public TLS
    certificate to the Android client, in one step.

.DESCRIPTION
    The pieces already exist (cert-init creates the certificate, the client-config
    service exports it as an Android resource); this script only runs them in the
    right order and refuses to pretend a deployment works when the certificate
    cannot possibly validate:

      1. reads TLS_HOST / HTTPS_PORT / TLS_CERTS_DIR from the installation's .env
      2. docker compose up -d --build          (cert-init -> client-config -> backend)
      3. waits for HTTPS_PORT and reads the certificate the server actually offers
      4. fails when that certificate's SAN does not contain TLS_HOST, because Android
         verifies SANs and connect() would then fail after an otherwise correct setup
      5. copies client-config/android/res/raw/devserver.crt into the Android client's
         res/raw/, and makes sure debug-overrides trusts @raw/devserver

    The certificate is public (never the keystore, never a key). A dev certificate is
    self-signed, so the Android app has to bundle it in debug builds; see
    app/src/main/res/xml/network_security_config.xml on the client.

    Certificate rotation is deliberately not silent: TLS_HOST only reaches the SAN when
    a NEW certificate is created, and rotating invalidates the old identity. Pass
    -RotateCert to archive the current certificate and let cert-init issue a new one.

.EXAMPLE
    .\scripts\start-and-export-cert.ps1 -LanIp 192.168.1.115 -RotateCert
    Issue a certificate valid for the LAN address a physical phone can reach and copy it
    into the sibling Android checkout.

.EXAMPLE
    .\scripts\start-and-export-cert.ps1 -NoStart
    Stack already running: only verify the served certificate and refresh the client copy.
#>
[CmdletBinding()]
param(
    [string]$EnvFile = '.env',
    [string]$LanIp,
    [switch]$RotateCert,
    [switch]$NoStart,
    [string]$ClientRepo,
    [ValidateRange(10, 1800)]
    [int]$WaitSeconds = 180
)

$ErrorActionPreference = 'Stop'

<#
    Android verifies Subject Alternative Names, never the Common Name. The formatted SAN coming
    from .NET is localized ("IP adresa=...", "Název DNS=..."), so only the address itself is matched
    here; exportClientConfig does the authoritative comparison with a real X.509 parser when it
    writes devserver.crt. A wildcard is included because it covers exactly one DNS label.
#>
function Test-CertificateCoversHost {
    param(
        [AllowEmptyString()][string]$San,
        [AllowEmptyString()][string]$HostName
    )

    if (-not $HostName) { return $false }
    if ($San -like "*$HostName*") { return $true }

    foreach ($match in [regex]::Matches($San, '\*\.(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}')) {
        $suffix = $match.Value.Substring(1).ToLowerInvariant()          # ".firma.cz"
        $actual = $HostName.ToLowerInvariant()
        $prefix = $actual.Substring(0, $actual.Length - $suffix.Length)
        if ($actual.EndsWith($suffix) -and -not $prefix.Contains('.')) {
            return $true
        }
    }

    return $false
}

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    # -EnvFile may name a file next to this checkout or anywhere else.
    $envPath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $root $EnvFile }
    if (-not (Test-Path $envPath -PathType Leaf)) {
        throw "Missing $envPath. Copy .env.example to .env and fill in the secrets first."
    }

    # Same three keys the compose services and exportClientConfig read; anything else in
    # .env stays out of this script.
    $config = @{}
    foreach ($line in (Get-Content -LiteralPath $envPath -Encoding UTF8)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed.StartsWith('export ')) { $trimmed = $trimmed.Substring(7).Trim() }
        $split = $trimmed.IndexOf('=')
        if ($split -lt 1) { continue }
        $key = $trimmed.Substring(0, $split).Trim()
        if ($key -notin 'TLS_HOST', 'HTTPS_PORT', 'TLS_CERTS_DIR') { continue }
        $value = $trimmed.Substring($split + 1).Trim()
        if ($value.StartsWith('"') -or $value.StartsWith("'")) {
            # A quoted value may contain '#'; only its matching quote ends it.
            $quote = $value.Substring(0, 1)
            $end = $value.IndexOf($quote, 1)
            if ($end -lt 0) { throw "Invalid quoted $key in $envPath" }
            # Substring takes a length in .NET, unlike Java's begin/end pair: end - 1 drops the quote.
            $config[$key] = $value.Substring(1, $end - 1)
        } else {
            # An unquoted value ends at its first ' #', exactly like exportClientConfig reads it.
            $hash = $value.IndexOf('#')
            if ($hash -ge 0) { $value = $value.Substring(0, $hash) }
            $config[$key] = $value.Trim()
        }
    }

    $host_ = if ($LanIp) { $LanIp } else { $config['TLS_HOST'] }
    if (-not $host_) { $host_ = 'localhost' }
    $host_ = $host_.Trim()
    if ($host_ -match '://|:') {
        throw "TLS_HOST must be a bare hostname or IPv4 address without scheme or port, got '$host_'."
    }

    $port = if ($config['HTTPS_PORT']) { [int]$config['HTTPS_PORT'] } else { 8443 }
    $certsDir = if ($config['TLS_CERTS_DIR']) { $config['TLS_CERTS_DIR'] } else { './certs' }

    if ($LanIp) {
        # Compose prefers process variables over the .env file, so this is enough for the
        # run but not enough for the next person: say so instead of leaving a hidden override.
        $env:TLS_HOST = $LanIp
        Write-Warning "TLS_HOST is overridden to $LanIp for this run only; write it into $EnvFile to keep it."
    }

    Write-Host "Installation: $host_`:$port  certificates: $certsDir" -ForegroundColor Cyan

    # --- 1. start (or restart) the stack -------------------------------------------------------
    if ($NoStart) {
        Write-Host '-NoStart given: expecting a stack that is already running.'
    } else {
        docker compose --env-file $EnvFile up -d --build
        if ($LASTEXITCODE -ne 0) { throw "docker compose up failed with exit code $LASTEXITCODE." }
    }

    # --- 2. wait for the port, then read the certificate the server offers ---------------------
    $probeHost = if ($host_ -in 'localhost', '127.0.0.1') { '127.0.0.1' } else { $host_ }
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    $served = $null
    $san = $null

    while ((Get-Date) -lt $deadline) {
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $tcp.Connect($probeHost, $port)
            # The certificate is not trusted by the OS yet - that is the client's job - so any
            # validation result is accepted here purely to be able to read the certificate.
            $callback = [System.Net.Security.RemoteCertificateValidationCallback] {
                param($sender, $certificate, $chain, $errors) $true
            }
            $ssl = New-Object System.Net.Security.SslStream($tcp.GetStream(), $false, $callback)
            $ssl.AuthenticateAsClient($probeHost)
            $served = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($ssl.RemoteCertificate)
            $sanExt = $served.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.17' }
            $san = if ($sanExt) { $sanExt.Format($false) } else { '' }
            $ssl.Dispose()
            $tcp.Close()
            break
        } catch {
            $served = $null
            Start-Sleep -Seconds 3
        }
    }

    if (-not $served) {
        throw "No TLS answer from ${probeHost}:$port within $WaitSeconds s. Check: docker compose logs --tail 200 backend"
    }

    Write-Host ("Served certificate: {0}  valid to {1:yyyy-MM-dd}" -f $served.Subject, $served.NotAfter)
    Write-Host ("SAN: $san")

    if (-not (Test-CertificateCoversHost -San $san -HostName $host_)) {
        Write-Error "The certificate does not cover TLS_HOST='$host_'. Android will reject the connection even when the certificate is bundled, because it verifies SANs."

        if ($RotateCert) {
            $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
            $archive = Join-Path (Join-Path $root $certsDir) "archive/$stamp"
            New-Item -ItemType Directory -Force -Path $archive | Out-Null
            foreach ($name in 'keystore.p12', 'certificate.pem') {
                $item = Join-Path (Join-Path $root $certsDir) $name
                if (Test-Path $item -PathType Leaf) {
                    Move-Item -LiteralPath $item -Destination (Join-Path $archive $name)
                    Write-Host "Archived $name to $archive"
                }
            }
            Write-Host "Re-running cert-init so it issues a certificate for $host_ ..." -ForegroundColor Cyan
            docker compose --env-file $EnvFile up -d --build
            if ($LASTEXITCODE -ne 0) { throw "docker compose up failed with exit code $LASTEXITCODE." }
            docker compose --env-file $EnvFile up -d --force-recreate --no-deps backend
            throw "Certificate rotated; run this script again to verify the new one."
        }

        throw "Re-run with -RotateCert to archive the current certificate and issue one for $host_ (the old identity stops working)."
    }

    Write-Host "SAN covers TLS_HOST '$host_'." -ForegroundColor Green

    # --- 3. hand the public certificate to the Android client ---------------------------------
    $exportedCrt = Join-Path $root 'client-config/android/res/raw/devserver.crt'
    $exportedXml = Join-Path $root 'client-config/android/res/values/server_config.xml'
    if (-not (Test-Path $exportedCrt -PathType Leaf)) {
        throw "Missing $exportedCrt. The client-config service did not finish: docker compose logs client-config"
    }

    if (-not $ClientRepo) {
        $guess = Join-Path (Split-Path -Parent $root) 'enterprise-messages-client'
        if (Test-Path (Join-Path $guess 'app/src/main/res') -PathType Container) { $ClientRepo = $guess }
    }
    if (-not $ClientRepo) {
        Write-Warning "No Android checkout found next to $root; pass -ClientRepo to copy the certificate."
        return
    }

    $rawDir = Join-Path $ClientRepo 'app/src/main/res/raw'
    New-Item -ItemType Directory -Force -Path $rawDir | Out-Null
    Copy-Item -LiteralPath $exportedCrt -Destination (Join-Path $rawDir 'devserver.crt') -Force
    Write-Host "Copied the certificate to $rawDir/devserver.crt" -ForegroundColor Green

    $secConfig = Join-Path $ClientRepo 'app/src/main/res/xml/network_security_config.xml'
    if (Test-Path $secConfig -PathType Leaf) {
        $text = Get-Content -LiteralPath $secConfig -Raw
        if ($text -notmatch '@raw/devserver') {
            $updated = [regex]::Replace($text,
                '(?s)(<debug-overrides>\s*<trust-anchors>)',
                "`$1`r`n            <certificates src=""@raw/devserver""/>")
            if ($updated -eq $text) {
                Write-Warning "$secConfig has no <debug-overrides><trust-anchors>; add the @raw/devserver trust anchor by hand."
            } else {
                Set-Content -LiteralPath $secConfig -Value $updated -NoNewline
                Write-Host "Added <certificates src=""@raw/devserver""/> to $secConfig" -ForegroundColor Green
            }
        } else {
            Write-Host "$secConfig already trusts @raw/devserver."
        }
    } else {
        Write-Warning "No network_security_config.xml under $ClientRepo; the client cannot trust this certificate yet."
    }

    # The client owns @string/server_ip / @integer/server_port; server_config.xml declares
    # server_host and its own server_port, so copying it in would collide. Show it instead.
    if (Test-Path $exportedXml -PathType Leaf) {
        Write-Host "`nExported connection settings ($exportedXml) - match them in the client's strings.xml:" -ForegroundColor Cyan
        Get-Content -LiteralPath $exportedXml
    }

    Write-Host ("Certificate SHA-256: " + $served.Thumbprint + " (SHA-1 of DER)")
    Write-Host "Rebuild the APK, then on Android 17 grant 'Local network' access to the app on first launch." -ForegroundColor Cyan
} finally {
    Pop-Location
}
