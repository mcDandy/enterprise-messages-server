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
      2. checks the certificate that is already in TLS_CERTS_DIR against TLS_HOST, so a
         stale SAN is reported here instead of aborting 'docker compose up' later
      3. docker compose up -d --build          (cert-init -> client-config -> backend)
      4. waits for HTTPS_PORT and reads the certificate the server actually offers
      5. fails when that certificate's SAN does not contain TLS_HOST, because Android
         verifies SANs and connect() would then fail after an otherwise correct setup
      6. copies client-config/android/res/raw/devserver.crt into the Android client's
         res/raw/, and makes sure debug-overrides trusts @raw/devserver

    The certificate is public (never the keystore, never a key). A dev certificate is
    self-signed, so the Android app has to bundle it in debug builds; see
    app/src/main/res/xml/network_security_config.xml on the client.

    Certificate rotation is deliberately not silent: TLS_HOST only reaches the SAN when
    a NEW certificate is created, and rotating invalidates the old identity. Pass
    -RotateCert to archive the current certificate and let cert-init issue a new one;
    the rotation happens before the stack is started, so one run is enough.

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
# Compose and BuildKit report progress on stderr; that must not look like a failure.
if (Test-Path Variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

<#
    Android verifies Subject Alternative Names, never the Common Name, so the comparison
    has to see the real names. X509Extension::Format() cannot be used for it: it is
    localised ("IP adresa=192.168.1.115" on a Czech Windows), which makes a substring
    match depend on the display language and even match the wrong address, because
    "192.168.1.1" is contained in "192.168.1.115". The SAN extension is therefore read
    from its DER encoding, which is locale-independent. exportClientConfig does the
    authoritative version of the same check with a real X.509 parser when it writes
    devserver.crt; failing fast here only keeps 'docker compose up' from failing with
    the far less helpful "service client-config didn't complete successfully: exit 1".
#>
function Read-DerLength {
    param(
        [Parameter(Mandatory)][byte[]]$Bytes,
        [Parameter(Mandatory)][ref]$Position
    )

    $first = $Bytes[$Position.Value]
    $Position.Value++
    if (($first -band 0x80) -eq 0) { return $first }          # short form: length is the byte

    $length = 0
    for ($byte = 0; $byte -lt ($first -band 0x7F); $byte++) { # long form: length follows
        $length = ($length -shl 8) -bor $Bytes[$Position.Value]
        $Position.Value++
    }
    return $length
}

function Get-CertificateHostNames {
    param(
        [Parameter(Mandatory)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate
    )

    $names = New-Object System.Collections.Generic.List[string]
    $extensions = @($Certificate.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.17' })
    if ($extensions.Count -eq 0) { return ,$names }

    $bytes = $extensions[0].RawData                # GeneralNames ::= SEQUENCE OF GeneralName
    $position = 0
    if ($bytes[0] -ne 0x30) { return ,$names }     # not a SEQUENCE: nothing reliable to read
    $position = 1
    [void](Read-DerLength -Bytes $bytes -Position ([ref]$position))

    while ($position -lt $bytes.Length) {
        $tag = $bytes[$position]
        $position++
        $length = Read-DerLength -Bytes $bytes -Position ([ref]$position)
        if ($length -le 0 -or ($position + $length) -gt $bytes.Length) { break }

        $value = New-Object byte[] $length
        [Array]::Copy($bytes, $position, $value, 0, $length)
        $position += $length

        if ($tag -eq 0x82) {                                       # [2] dNSName
            $names.Add([System.Text.Encoding]::ASCII.GetString($value))
        } elseif ($tag -eq 0x87 -and $value.Length -eq 4) {        # [7] iPAddress (IPv4)
            $names.Add((New-Object System.Net.IPAddress(,$value)).ToString())
        }
    }

    # No leading comma: PowerShell unrolls a returned collection, and ',$names' would hand the
    # caller one element containing everything instead of the individual names.
    return $names
}

function Read-CertificatePem {
    param([Parameter(Mandatory)][string]$Path)

    # Handing the PEM file to X509Certificate2 directly behaves differently on Windows
    # PowerShell and PowerShell 7, so the base64 body is decoded and the DER is passed.
    $match = [regex]::Match((Get-Content -LiteralPath $Path -Raw),
        '-----BEGIN CERTIFICATE-----\s*(?<body>[A-Za-z0-9+/=\s]+?)-----END CERTIFICATE-----')
    if (-not $match.Success) { return $null }

    $der = [Convert]::FromBase64String(($match.Groups['body'].Value -replace '\s', ''))
    return New-Object System.Security.Cryptography.X509Certificates.X509Certificate2(,$der)
}

function Test-HostCovered {
    param(
        [AllowEmptyCollection()][string[]]$Names,
        [AllowEmptyString()][string]$HostName
    )

    if (-not $HostName) { return $false }
    $target = $HostName.Trim().TrimEnd('.').ToLowerInvariant()

    foreach ($name in $Names) {
        $candidate = ('' + $name).Trim().TrimEnd('.').ToLowerInvariant()
        if ($candidate -eq $target) { return $true }

        # A wildcard covers exactly one DNS label, so *.firma.cz never matches a.b.firma.cz.
        if (-not $candidate.StartsWith('*.')) { continue }
        $suffix = $candidate.Substring(1)                          # ".firma.cz"
        if (-not $target.EndsWith($suffix)) { continue }
        $prefix = $target.Substring(0, $target.Length - $suffix.Length)
        if ($prefix.Length -gt 0 -and -not $prefix.Contains('.')) { return $true }
    }

    return $false
}

function Invoke-Compose {
    param(
        [Parameter(Mandatory)][string]$ComposeEnvFile,
        [Parameter(Mandatory)][string[]]$Arguments
    )

    # Compose reports progress (and BuildKit does most of its output) on stderr. Under
    # $ErrorActionPreference = 'Stop' those lines can become terminating errors as soon as
    # somebody redirects the script's output, which would skip the $LASTEXITCODE check and
    # hide the reason. The exit code alone decides here, so stderr is only noise.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        docker compose --env-file $ComposeEnvFile @Arguments 2>&1 | ForEach-Object { Write-Host "$_" }
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Show-ComposeDiagnostics {
    param([Parameter(Mandatory)][string]$ComposeEnvFile)

    # Both one-shot services abort the whole 'up'; without their log the only clue is
    # "service client-config didn't complete successfully: exit 1".
    foreach ($service in 'cert-init', 'client-config') {
        Write-Host "--- docker compose logs --tail 40 $service ---" -ForegroundColor Yellow
        [void](Invoke-Compose -ComposeEnvFile $ComposeEnvFile -Arguments @('logs', '--tail', '40', $service))
    }
}

function Invoke-CertificateRotation {
    param(
        [Parameter(Mandatory)][string]$CertsPath,
        [Parameter(Mandatory)][string]$TargetHost
    )

    $archive = Join-Path $CertsPath ('archive/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Force -Path $archive | Out-Null

    $moved = 0
    foreach ($name in 'keystore.p12', 'certificate.pem') {
        $item = Join-Path $CertsPath $name
        if (Test-Path $item -PathType Leaf) {
            Move-Item -LiteralPath $item -Destination (Join-Path $archive $name)
            Write-Host "Archived $name to $archive"
            $moved++
        }
    }

    if ($moved -eq 0) {
        Write-Host "Nothing to rotate under $CertsPath; cert-init will create a certificate for $TargetHost."
    }
}

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    # -EnvFile may name a file next to this checkout or anywhere else.
    $envPath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $root $EnvFile }
    if (-not (Test-Path $envPath -PathType Leaf)) {
        throw "Missing $envPath. Copy .env.example to .env and fill in the secrets first."
    }

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'docker is not on PATH; this installation is started through Docker Compose.'
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
    $certsPath = if ([System.IO.Path]::IsPathRooted($certsDir)) { $certsDir } else { Join-Path $root $certsDir }
    $publicCertPath = Join-Path $certsPath 'certificate.pem'
    $keystorePath = Join-Path $certsPath 'keystore.p12'

    if ($LanIp) {
        # Compose prefers process variables over the .env file, so this is enough for the
        # run but not enough for the next person: say so instead of leaving a hidden override.
        $env:TLS_HOST = $LanIp
        Write-Warning "TLS_HOST is overridden to $LanIp for this run only; write it into $EnvFile to keep it."
    }

    Write-Host "Installation: $host_`:$port  certificates: $certsDir" -ForegroundColor Cyan

    # --- 1. what does the certificate that is already on disk cover? --------------------------
    # cert-init never changes the identity of an existing keystore, so TLS_HOST (and therefore
    # -LanIp) only reaches the SAN of a certificate that still has to be created. Checking that
    # here is what turns "copied certs/ from another machine / changed the IP" into an
    # understandable message instead of a failed image build followed by exit 1.
    $covered = @()
    if (Test-Path $publicCertPath -PathType Leaf) {
        $existing = Read-CertificatePem $publicCertPath
        if ($existing) {
            $covered = @(Get-CertificateHostNames -Certificate $existing)
            # Precomputed: ')' inside a quoted string would end a $( ... ) subexpression early.
            $coveredText = $covered -join ', '
            if (Test-HostCovered -Names $covered -HostName $host_) {
                Write-Host "Existing certificate covers '$host_' (SAN: $coveredText); it will be reused."
            } elseif (-not $RotateCert) {
                throw ("The existing certificate in $certsDir covers '$coveredText' and not TLS_HOST='$host_'. " +
                       "client-config would abort the stack with 'Certificate SAN does not match TLS_HOST=$host_'. " +
                       "Re-run with -RotateCert to archive it and let cert-init issue a certificate for '$host_' " +
                       "(the old identity stops working).")
            } else {
                Write-Host "Existing certificate covers '$coveredText', not '$host_'; rotating it before the stack starts." -ForegroundColor Yellow
            }
        } else {
            Write-Warning "$publicCertPath holds no PEM certificate; its SAN is only verified against the running server."
        }
    } elseif (Test-Path $keystorePath -PathType Leaf) {
        Write-Warning "$keystorePath exists without certificate.pem; its SAN cannot be read here, so a mismatch can only be reported by client-config."
    } else {
        Write-Host "No certificate in $certsDir yet; cert-init will issue one for '$host_'."
    }

    $rotated = $false
    if ($RotateCert) {
        Invoke-CertificateRotation -CertsPath $certsPath -TargetHost $host_
        $rotated = $true
    }

    # --- 2. start (or restart) the stack -------------------------------------------------------
    if ($NoStart) {
        Write-Host '-NoStart given: expecting a stack that is already running.'
    } else {
        $exitCode = Invoke-Compose -ComposeEnvFile $EnvFile -Arguments @('up', '-d', '--build')
        if ($exitCode -ne 0) {
            Show-ComposeDiagnostics -ComposeEnvFile $EnvFile
            throw "docker compose up failed with exit code $exitCode."
        }
    }

    # --- 3. wait for the port, then read the certificate the server offers ---------------------
    $probeHost = if ($host_ -in 'localhost', '127.0.0.1') { '127.0.0.1' } else { $host_ }
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    $served = $null
    $servedNames = @()

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
            $servedNames = @(Get-CertificateHostNames -Certificate $served)
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
    $servedText = $servedNames -join ', '
    Write-Host "SAN: $servedText"

    if (-not (Test-HostCovered -Names $servedNames -HostName $host_)) {
        # Write-Error would abort under $ErrorActionPreference = 'Stop' and hide every option below.
        Write-Host "The served certificate does not cover TLS_HOST='$host_'. Android verifies SANs, so it would reject the connection even with this certificate bundled." -ForegroundColor Red

        if ($rotated) {
            throw "The certificate was rotated for this run, yet the server still offers one that covers '$servedText'. Check that TLS_HOST reaches cert-init: docker compose --env-file $EnvFile config"
        }
        if ($NoStart) {
            throw "Re-run without -NoStart and with -RotateCert to archive the current certificate and issue one for '$host_' (the old identity stops working)."
        }

        # Only reachable when no certificate.pem existed above (a keystore without its public
        # certificate): the SAN could not be known before the stack ran.
        Invoke-CertificateRotation -CertsPath $certsPath -TargetHost $host_
        Write-Host "Re-running cert-init so it issues a certificate for '$host_' ..." -ForegroundColor Cyan
        $exitCode = Invoke-Compose -ComposeEnvFile $EnvFile -Arguments @('up', '-d', '--build')
        if ($exitCode -ne 0) {
            Show-ComposeDiagnostics -ComposeEnvFile $EnvFile
            throw "docker compose up failed with exit code $exitCode."
        }
        [void](Invoke-Compose -ComposeEnvFile $EnvFile -Arguments @('up', '-d', '--force-recreate', '--no-deps', 'backend'))
        throw "Certificate rotated; run this script again to verify the new one."
    }

    Write-Host "SAN covers TLS_HOST '$host_'." -ForegroundColor Green

    # --- 4. hand the public certificate to the Android client ---------------------------------
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
