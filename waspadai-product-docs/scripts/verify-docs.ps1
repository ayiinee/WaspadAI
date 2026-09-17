[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $packageRoot
$errors = [System.Collections.Generic.List[string]]::new()
$expectedContractHash = '039df73adeef6960f12368caafd358e167273d19174c1472656f98b4a7df49a2'

function Add-Error([string]$message) { $script:errors.Add($message) }

function Get-RelativePath([string]$basePath, [string]$targetPath) {
    $baseUri = [Uri]((Resolve-Path -LiteralPath $basePath).Path.TrimEnd('\') + '\')
    $targetUri = [Uri](Resolve-Path -LiteralPath $targetPath).Path
    [Uri]::UnescapeDataString($baseUri.MakeRelativeUri($targetUri).ToString()).Replace('/', '\')
}

$contractPath = Join-Path $packageRoot 'contracts\current\android-api-contract.md'
$sourcePath = Join-Path $workspaceRoot 'android-api-contract.md'
$manifestPath = Join-Path $packageRoot 'manifest.json'

try {
    $manifest = Get-Content -Raw -Encoding UTF8 -LiteralPath $manifestPath | ConvertFrom-Json
    if ($manifest.version -ne '2026.09.16') { Add-Error 'Manifest version must be 2026.09.16.' }
    if ($manifest.current_contract.path -ne 'contracts/current/android-api-contract.md') { Add-Error 'Manifest contract path is invalid.' }
    if ($manifest.current_contract.sha256 -ne $expectedContractHash) { Add-Error 'Manifest contract checksum is stale.' }
    if ($manifest.topology -ne 'ANDROID_PRODUCT_API_INTERNAL_AI') { Add-Error 'Manifest topology is stale.' }
}
catch { Add-Error "Manifest is invalid: $($_.Exception.Message)" }

foreach ($path in @($contractPath, $sourcePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Add-Error "Missing contract: $path"; continue }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    if ($hash -ne $expectedContractHash) { Add-Error "Contract checksum mismatch for $path`: $hash" }
}

if ((Test-Path $contractPath) -and (Test-Path $sourcePath)) {
    $contractBytes = [IO.File]::ReadAllBytes($contractPath)
    $sourceBytes = [IO.File]::ReadAllBytes($sourcePath)
    if ($contractBytes.Length -ne $sourceBytes.Length -or -not [Linq.Enumerable]::SequenceEqual($contractBytes, $sourceBytes)) {
        Add-Error 'Workspace source and current contract are not byte-identical.'
    }

    $contractText = Get-Content -Raw -Encoding UTF8 -LiteralPath $contractPath
    foreach ($required in @(
        'POST /api/v1/verifications/text',
        'Authorization: Bearer <supabase_access_token>',
        'Idempotency-Key: <uuid-v4>',
        'Status dokumen: **MUTLAK v1.0**',
        'Android tidak mengirim `output_mode`',
        'Product Backend selalu meminta `output_mode=BOTH`',
        'community_evidence',
        '"verified_claim"',
        '"stance"',
        '"evidence_summary"',
        '| Record per request | Maksimal 5. |',
        "community_posts.status = 'VERIFIED_EVIDENCE'",
        'consent aktif `RAG_REUSE` tersedia',
        '`PUBLISHED_UNVERIFIED` boleh tampil di feed Product',
        'Status implementasi: `PARTIAL_RUNTIME`; Product Backend sudah mengekspor route'
    )) {
        if (-not $contractText.Contains($required)) { Add-Error "Current contract is missing invariant: $required" }
    }
    foreach ($forbidden in @('docs/database-api-contract.md', 'Android Kotlin -> Public WaspadAI API')) {
        if ($contractText.Contains($forbidden)) { Add-Error "Current contract contains stale statement: $forbidden" }
    }
}

$requiredFiles = @(
    'README.md', 'manifest.json', 'validation-report.json',
    'contracts\README.md', 'contracts\current\README.md', 'contracts\future\README.md',
    'contracts\examples\text-request.json', 'contracts\examples\verification-response.json',
    'contracts\examples\validation-error.json', 'docs\README.md',
    'docs\product\scope-and-status.md', 'docs\architecture\system-overview.md',
    'docs\architecture\android-client.md', 'docs\api\README.md',
    'docs\api\request-response.md', 'docs\development\testing.md',
    'docs\governance\documentation-governance.md', 'docs\adr\0002-future-product-backend.md'
)
foreach ($relative in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $packageRoot $relative) -PathType Leaf)) { Add-Error "Missing required file: $relative" }
}

if (Test-Path -LiteralPath (Join-Path $packageRoot 'contracts\future\database-api-contract.md')) {
    Add-Error 'Obsolete database-api-contract.md must not exist.'
}

try {
    $request = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $packageRoot 'contracts\examples\text-request.json') | ConvertFrom-Json
    if ($null -ne $request.PSObject.Properties['output_mode']) { Add-Error 'Android text fixture must not contain output_mode.' }
    if ($null -eq $request.PSObject.Properties['page_context']) { Add-Error 'Android text fixture must document page_context.' }
}
catch { Add-Error "Text fixture is invalid: $($_.Exception.Message)" }

try {
    $response = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $packageRoot 'contracts\examples\verification-response.json') | ConvertFrom-Json
    if ($null -eq $response.history -or $null -eq $response.result) { Add-Error 'Response fixture must contain history and result.' }
    if ($response.execution_mode -ne 'MOCK') { Add-Error 'Current response fixture must honestly use execution_mode=MOCK.' }
    if ([string]::IsNullOrWhiteSpace($response.result.presentation.narrative.text)) { Add-Error 'Response fixture is missing narrative text.' }
}
catch { Add-Error "Response fixture is invalid: $($_.Exception.Message)" }

try {
    $validation = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $packageRoot 'contracts\examples\validation-error.json') | ConvertFrom-Json
    if ($validation.error.code -ne 'VALIDATION_ERROR') { Add-Error 'Validation fixture must use Product error envelope.' }
}
catch { Add-Error "Validation fixture is invalid: $($_.Exception.Message)" }

$futurePath = Join-Path $packageRoot 'contracts\future\product-api.openapi.yaml'
if (-not (Test-Path $futurePath)) { Add-Error 'Missing future Product OpenAPI draft.' }
elseif (((Get-Content -Encoding UTF8 -TotalCount 3 $futurePath) -join "`n") -notmatch 'STATUS: FUTURE / NON-RUNTIME CONTRACT') {
    Add-Error 'Future Product OpenAPI lacks non-runtime warning.'
}

$markdownFiles = @()
$markdownFiles += Get-Item (Join-Path $packageRoot 'README.md')
$markdownFiles += Get-ChildItem (Join-Path $packageRoot 'docs') -Recurse -File -Filter '*.md' | Where-Object { $_.FullName -notmatch '[\\/]docs[\\/]archive[\\/]' }
$markdownFiles += Get-ChildItem (Join-Path $packageRoot 'contracts') -Recurse -File -Filter '*.md'
$checkedLinks = 0
foreach ($file in $markdownFiles) {
    $content = Get-Content -Raw -Encoding UTF8 $file.FullName
    foreach ($match in [regex]::Matches($content, '(?<!\!)\[[^\]]+\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value.Trim().Trim('<', '>')
        if ($target -match '^(https?://|mailto:|#)' -or $target.Length -eq 0) { continue }
        $target = ($target -split '#', 2)[0]
        if ($target.Length -eq 0) { continue }
        $checkedLinks++
        $resolved = [IO.Path]::GetFullPath((Join-Path $file.DirectoryName $target))
        if (-not (Test-Path -LiteralPath $resolved)) { Add-Error "Broken link in $(Get-RelativePath $packageRoot $file.FullName): $target" }
    }
}

$jsonFiles = @(Get-ChildItem $packageRoot -Recurse -File -Filter '*.json')
foreach ($file in $jsonFiles) {
    try { Get-Content -Raw -Encoding UTF8 $file.FullName | ConvertFrom-Json | Out-Null }
    catch { Add-Error "Invalid JSON in $(Get-RelativePath $packageRoot $file.FullName): $($_.Exception.Message)" }
}

if ($errors.Count -gt 0) {
    foreach ($message in $errors) { Write-Error $message }
    exit 1
}

Write-Output "PASS: Android-Product-AI contract SHA-256 $expectedContractHash verified byte-identically."
Write-Output 'PASS: Product API auth, plural path, wrapper, and community eligibility invariants verified.'
Write-Output 'PASS: current fixtures describe Product text MOCK truthfully.'
Write-Output "PASS: $checkedLinks relative links resolve across $($markdownFiles.Count) active Markdown files."
Write-Output "PASS: $($jsonFiles.Count) JSON files parse."
Write-Output 'PASS: obsolete database-api-contract.md is absent and future OpenAPI remains isolated.'
