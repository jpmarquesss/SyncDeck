[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $RepositoryRoot
try {
    Write-Host "Validando o repositorio..." -ForegroundColor Cyan
    python scripts\validate_repository.py
    if ($LASTEXITCODE -ne 0) { throw "A validacao falhou." }

    Write-Host "Compilando o agente Windows..." -ForegroundColor Cyan
    & cmd.exe /c windows-agent\build-agent.bat
    if ($LASTEXITCODE -ne 0) { throw "A compilacao do agente falhou." }

    Write-Host "Compilando o aplicativo Android..." -ForegroundColor Cyan
    Push-Location android-app
    try {
        & .\gradlew.bat --no-daemon assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "A compilacao Android falhou." }
        Copy-Item -Force "app\build\outputs\apk\debug\app-debug.apk" "SyncDeck.apk"
    }
    finally {
        Pop-Location
    }

    Write-Host ""
    Write-Host "Build concluido:" -ForegroundColor Green
    Write-Host "- windows-agent\SyncDeckAgent.exe"
    Write-Host "- android-app\SyncDeck.apk"
}
finally {
    Pop-Location
}
