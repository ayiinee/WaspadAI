[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $PSScriptRoot
$errors = [System.Collections.Generic.List[string]]::new()
$expectedContractHash = '52df0459942ee565a4b5322fec27ca266d0b41384a351fe7f00266e5cd5bb3f2'

function Add-Error([string]$message) {
    $script:errors.Add($message)
}

function Get-RelativePath([string]$basePath, [string]$targetPath) {
    $baseUri = [Uri]((Resolve-Path -LiteralPath $basePath).Path.TrimEnd('\') + '\')
    $targetUri = [Uri](Resolve-Path -LiteralPath $targetPath).Path
    return [Uri]::UnescapeDataString($baseUri.MakeRelativeUri($targetUri).ToString()).Replace('/', '\')
}

$contractPath = Join-Path $packageRoot 'contracts\current\android-api-contract.md'
$packageManifestPath = Join-Path $packageRoot 'manifest.json'
try {
    $packageManifest = Get-Content -Raw -Encoding UTF8 -LiteralPath $packageManifestPath | ConvertFrom-Json
    if ($packageManifest.version -ne '2026.09.15') { Add-Error 'Package manifest version must be 2026.09.15.' }
    if ($packageManifest.current_contract.path -ne 'contracts/current/android-api-contract.md') { Add-Error 'Package manifest points to the wrong current contract.' }
    if ($packageManifest.current_contract.sha256 -ne $expectedContractHash) { Add-Error 'Package manifest current contract checksum is stale.' }
}
catch {
    Add-Error "Package manifest is missing or invalid: $($_.Exception.Message)"
}

if (-not (Test-Path -LiteralPath $contractPath -PathType Leaf)) {
    Add-Error 'Missing current Android API contract.'
}
else {
    $contractHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $contractPath).Hash.ToLowerInvariant()
    if ($contractHash -ne $expectedContractHash) {
        Add-Error "Current contract checksum mismatch. Expected $expectedContractHash, found $contractHash."
    }

    $contractText = Get-Content -Raw -Encoding UTF8 -LiteralPath $contractPath
    foreach ($requiredText in @(
        'POST /api/v1/verify/text',
        'POST /api/v1/verify/image',
        'Android tidak boleh memanggil endpoint internal WaspadAI',
        'API WaspadAI mode demo tidak menerima dan tidak',
        'Tidak ada wrapper `result`',
        'presentation.narrative.text',
        'Timeout client yang disarankan adalah 120 detik'
    )) {
        if (-not $contractText.Contains($requiredText)) {
            Add-Error "Current contract is missing invariant: $requiredText"
        }
    }
}

$workspaceSource = Join-Path (Split-Path -Parent $packageRoot) 'android-api-contract.md'
if (Test-Path -LiteralPath $workspaceSource -PathType Leaf) {
    $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $workspaceSource).Hash.ToLowerInvariant()
    if ($sourceHash -ne $expectedContractHash) {
        Add-Error "Workspace source contract changed. Review and intentionally update the canonical copy/checksum: $sourceHash"
    }
}

$requiredFiles = @(
    'README.md',
    'manifest.json',
    'docs\README.md',
    'docs\product\overview.md',
    'docs\architecture\system-overview.md',
    'docs\architecture\android-client.md',
    'docs\api\README.md',
    'docs\api\request-response.md',
    'docs\api\errors-and-resilience.md',
    'docs\development\setup.md',
    'docs\governance\documentation-governance.md',
    'docs\adr\0001-current-mvp-direct-ai.md',
    'contracts\README.md',
    'contracts\future\README.md',
    'contracts\reference\README.md'
)
foreach ($relativePath in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $packageRoot $relativePath) -PathType Leaf)) {
        Add-Error "Missing required documentation file: $relativePath"
    }
}

$rootReadme = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $packageRoot 'README.md')
foreach ($invariant in @(
    'Android memanggil `POST /api/v1/verify/text`',
    'Android tidak mengirim Bearer token Supabase ke WaspadAI',
    'tidak ada wrapper `result`, `history`',
    'Fitur tersebut membutuhkan Product Backend pada fase berikutnya'
)) {
    if (-not $rootReadme.Contains($invariant)) {
        Add-Error "Root README is missing current-mode statement: $invariant"
    }
}

$textFixturePath = Join-Path $packageRoot 'contracts\examples\text-request.json'
$responseFixturePath = Join-Path $packageRoot 'contracts\examples\verification-response.json'
try {
    $textFixture = Get-Content -Raw -Encoding UTF8 -LiteralPath $textFixturePath | ConvertFrom-Json
    if ($textFixture.output_mode -ne 'BOTH') { Add-Error 'Current text fixture must use output_mode=BOTH.' }
    if ($null -ne $textFixture.PSObject.Properties['page_context']) { Add-Error 'Current text fixture must not contain legacy page_context.' }
    if ($null -ne $textFixture.PSObject.Properties['authorization']) { Add-Error 'Current text fixture must not contain authorization.' }
}
catch {
    Add-Error "Current text fixture is invalid JSON: $($_.Exception.Message)"
}

try {
    $responseFixture = Get-Content -Raw -Encoding UTF8 -LiteralPath $responseFixturePath | ConvertFrom-Json
    if ($null -ne $responseFixture.PSObject.Properties['result']) { Add-Error 'Current response fixture must be a direct response without result wrapper.' }
    if ($null -ne $responseFixture.PSObject.Properties['history']) { Add-Error 'Current response fixture must not contain Product history metadata.' }
    if ([string]::IsNullOrWhiteSpace($responseFixture.presentation.narrative.text)) { Add-Error 'Current response fixture must contain presentation.narrative.text.' }
}
catch {
    Add-Error "Current response fixture is invalid JSON: $($_.Exception.Message)"
}

$futureContractPath = Join-Path $packageRoot 'contracts\future\product-api.openapi.yaml'
if (-not (Test-Path -LiteralPath $futureContractPath -PathType Leaf)) {
    Add-Error 'Missing preserved future Product API draft.'
}
else {
    $futureHeader = (Get-Content -Encoding UTF8 -TotalCount 3 -LiteralPath $futureContractPath) -join "`n"
    if ($futureHeader -notmatch 'STATUS: FUTURE / NON-RUNTIME CONTRACT') {
        Add-Error 'Future Product OpenAPI must carry a non-runtime warning at the top.'
    }
}

$markdownFiles = @()
$markdownFiles += Get-Item -LiteralPath (Join-Path $packageRoot 'README.md')
$markdownFiles += Get-ChildItem -LiteralPath (Join-Path $packageRoot 'docs') -Recurse -File -Filter '*.md' |
    Where-Object { $_.FullName -notmatch '[\\/]docs[\\/]archive[\\/]' }
$markdownFiles += Get-ChildItem -LiteralPath (Join-Path $packageRoot 'contracts') -Recurse -File -Filter '*.md'

$checkedLinks = 0
foreach ($file in $markdownFiles) {
    $content = Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName
    foreach ($match in [regex]::Matches($content, '(?<!\!)\[[^\]]+\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value.Trim()
        if ($target -match '^(https?://|mailto:|#)' -or $target.Length -eq 0) { continue }
        if ($target.StartsWith('<') -and $target.EndsWith('>')) {
            $target = $target.Substring(1, $target.Length - 2)
        }
        $target = ($target -split '#', 2)[0]
        if ($target.Length -eq 0) { continue }
        $checkedLinks++
        $resolved = [IO.Path]::GetFullPath((Join-Path $file.DirectoryName $target))
        if (-not (Test-Path -LiteralPath $resolved)) {
            $relativeFile = Get-RelativePath $packageRoot $file.FullName
            Add-Error "Broken link in ${relativeFile}: $target"
        }
    }
}

$jsonFiles = @(Get-ChildItem -LiteralPath $packageRoot -Recurse -File -Filter '*.json')
foreach ($file in $jsonFiles) {
    try {
        Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName | ConvertFrom-Json | Out-Null
    }
    catch {
        $relativeFile = Get-RelativePath $packageRoot $file.FullName
        Add-Error "Invalid JSON in ${relativeFile}: $($_.Exception.Message)"
    }
}

if ($errors.Count -gt 0) {
    foreach ($errorMessage in $errors) { Write-Error $errorMessage }
    exit 1
}

Write-Output "PASS: current Android API contract SHA-256 $expectedContractHash verified."
Write-Output 'PASS: direct public AI topology, auth, response, and timeout invariants verified.'
Write-Output 'PASS: current fixtures use BOTH and direct response shape.'
Write-Output "PASS: $checkedLinks relative links resolve across $($markdownFiles.Count) active Markdown files."
Write-Output "PASS: $($jsonFiles.Count) JSON files parse."
Write-Output 'PASS: future Product OpenAPI is isolated and marked non-runtime.'
