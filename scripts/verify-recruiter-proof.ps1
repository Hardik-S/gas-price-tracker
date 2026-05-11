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

    & .\gradlew.bat :app:test --no-daemon --console plain
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    Write-Output "Gas Price Tracker recruiter proof passed: key hygiene and unit tests verified."
}
finally {
    Pop-Location
}
