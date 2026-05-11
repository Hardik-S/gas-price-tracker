$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $root
try {
    $trackedFiles = git ls-files
    if ($trackedFiles -contains "local.properties") {
        throw "local.properties is tracked; API keys must stay local-only."
    }

    $keyPattern = "AIza[0-9A-Za-z_-]{20,}"
    $hits = @()
    foreach ($file in $trackedFiles) {
        if ($file -like "*.jar") {
            continue
        }
        $content = Get-Content -LiteralPath $file -Raw -ErrorAction SilentlyContinue
        if ($content -match $keyPattern) {
            $hits += $file
        }
    }

    if ($hits.Count -gt 0) {
        throw "Potential Google API key committed in: $($hits -join ', ')"
    }

    $manualSmokePath = Join-Path $root "docs\manual-smoke-evidence.md"
    if (-not (Test-Path -LiteralPath $manualSmokePath)) {
        throw "Missing manual smoke evidence template: docs\manual-smoke-evidence.md"
    }

    $manualSmoke = Get-Content -LiteralPath $manualSmokePath -Raw
    foreach ($requiredPhrase in @("Activity Recognition", "No background microphone", "local-first persistence")) {
        if ($manualSmoke -notmatch [regex]::Escape($requiredPhrase)) {
            throw "Manual smoke template must document: $requiredPhrase"
        }
    }

    $runbook = Get-Content -LiteralPath (Join-Path $root "docs\recruiter-verification.md") -Raw
    if ($runbook -notmatch [regex]::Escape("manual-smoke-evidence.md")) {
        throw "Recruiter verification runbook must link the manual smoke evidence template."
    }

    & .\gradlew.bat :app:test --no-daemon --console plain
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    Write-Output "Gas Price Tracker recruiter proof passed: key hygiene and unit tests verified."
}
finally {
    Pop-Location
}
