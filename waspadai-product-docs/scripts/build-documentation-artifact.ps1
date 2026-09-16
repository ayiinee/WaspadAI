[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$OutputDirectory,
    [string]$Version = '2026.09.16'
)

$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $PSScriptRoot

& (Join-Path $PSScriptRoot 'verify-docs.ps1')
if (-not $?) { throw 'Documentation verification failed; artifact was not built.' }

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$artifactName = "waspadai-product-docs-$Version.zip"
$artifactPath = Join-Path $OutputDirectory $artifactName
$checksumPath = "$artifactPath.sha256"

Compress-Archive -Path @(
    (Join-Path $packageRoot 'README.md'),
    (Join-Path $packageRoot 'manifest.json'),
    (Join-Path $packageRoot 'docs'),
    (Join-Path $packageRoot 'contracts'),
    (Join-Path $packageRoot 'scripts'),
    (Join-Path $packageRoot 'validation-report.json')
) -DestinationPath $artifactPath -Force

$hash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath $checksumPath -Value "$hash  $artifactName" -Encoding ascii -NoNewline
Write-Output "PASS: built $artifactPath"
Write-Output "PASS: SHA-256 $hash"
