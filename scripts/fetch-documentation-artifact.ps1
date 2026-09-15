[CmdletBinding()]
param(
    [string]$ArtifactUrl,
    [string]$ArtifactSha256,
    [string]$ArtifactPath,
    [string]$LockPath = "contracts/documentation-artifact.lock.json",
    [string]$Destination = ".artifacts/documentation"
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $LockPath -PathType Leaf)) {
    throw "Documentation artifact lock is missing: $LockPath"
}
if (Test-Path -LiteralPath $Destination) {
    throw "Refusing to overwrite existing artifact destination: $Destination"
}

$lock = Get-Content -LiteralPath $LockPath -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $ArtifactUrl -and -not $ArtifactPath) { $ArtifactUrl = $lock.release_asset_url }
if (-not $ArtifactSha256) { $ArtifactSha256 = $lock.archive_sha256 }
if (([string]::IsNullOrWhiteSpace($ArtifactUrl) -and [string]::IsNullOrWhiteSpace($ArtifactPath)) -or [string]::IsNullOrWhiteSpace($ArtifactSha256)) {
    throw "Artifact URL/path and SHA-256 must be pinned in the lock or supplied by CI. Status: $($lock.status)"
}
if ($ArtifactSha256 -notmatch '^[a-fA-F0-9]{64}$') {
    throw 'Artifact SHA-256 must be exactly 64 hexadecimal characters.'
}

if ($ArtifactPath) {
    if (-not (Test-Path -LiteralPath $ArtifactPath -PathType Leaf)) {
        throw "Local documentation artifact is missing: $ArtifactPath"
    }
    $archivePath = (Resolve-Path -LiteralPath $ArtifactPath).Path
}
else {
    $archivePath = Join-Path ([System.IO.Path]::GetTempPath()) ("waspadai-product-docs-$($lock.version).zip")
    Invoke-WebRequest -Uri $ArtifactUrl -OutFile $archivePath
}
$actualArchiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualArchiveHash -ne $ArtifactSha256.ToLowerInvariant()) {
    throw "Artifact checksum mismatch: expected $ArtifactSha256, got $actualArchiveHash"
}

New-Item -ItemType Directory -Path $Destination -Force | Out-Null
Expand-Archive -LiteralPath $archivePath -DestinationPath $Destination
foreach ($property in $lock.required_files.psobject.Properties) {
    $filePath = Join-Path $Destination $property.Name
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
        throw "Pinned file is missing from documentation artifact: $($property.Name)"
    }
    $actualFileHash = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualFileHash -ne $property.Value.ToLowerInvariant()) {
        throw "Pinned file checksum mismatch: $($property.Name)"
    }
}
$manifestPath = Join-Path $Destination 'docs/archive/source-manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw 'Documentation source manifest is missing from the artifact.'
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.product_contract.version -ne $lock.version) {
    throw "Documentation artifact version does not match the Product lock: $($lock.version)"
}
Write-Output "PASS: documentation artifact $($lock.artifact_name) $($lock.version) is pinned and verified."
