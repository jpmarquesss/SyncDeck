[CmdletBinding()]
param(
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = (Get-Content (Join-Path $RepositoryRoot "VERSION") -Raw).Trim()
}
$OutputDirectory = Join-Path $RepositoryRoot "dist"
$OutputFile = Join-Path $OutputDirectory "SyncDeck-source-v$Version.zip"

Push-Location $RepositoryRoot
try {
    if (-not (Test-Path ".git")) {
        throw "Este comando deve ser executado em um clone Git."
    }

    python scripts\validate_repository.py
    if ($LASTEXITCODE -ne 0) { throw "A validacao falhou." }

    git diff --quiet
    if ($LASTEXITCODE -ne 0) { throw "Existem alteracoes locais nao commitadas." }
    git diff --cached --quiet
    if ($LASTEXITCODE -ne 0) { throw "Existem alteracoes preparadas e ainda nao commitadas." }

    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    if (Test-Path $OutputFile) { Remove-Item -Force $OutputFile }

    git archive --format=zip --prefix="SyncDeck-$Version/" --output="$OutputFile" HEAD
    if ($LASTEXITCODE -ne 0) { throw "git archive falhou." }

    $Hash = (Get-FileHash $OutputFile -Algorithm SHA256).Hash
    Write-Host "Pacote: $OutputFile" -ForegroundColor Green
    Write-Host "SHA-256: $Hash"
}
finally {
    Pop-Location
}
