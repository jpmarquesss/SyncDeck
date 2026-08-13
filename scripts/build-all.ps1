[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $RepositoryRoot
try {
    Write-Host "Validando o repositorio..." -ForegroundColor Cyan
    python scripts\validate_repository.py
    if ($LASTEXITCODE -ne 0) { throw "A validacao falhou." }

    if (Get-Command javac -ErrorAction SilentlyContinue) {
        Write-Host "Validando os vetores do protocolo..." -ForegroundColor Cyan
        New-Item -ItemType Directory -Force -Path "build\protocol-test" | Out-Null
        & javac tests\ProtocolVectorTest.java -d build\protocol-test
        if ($LASTEXITCODE -ne 0) { throw "A compilacao do teste de protocolo falhou." }
        & java -cp build\protocol-test ProtocolVectorTest
        if ($LASTEXITCODE -ne 0) { throw "O teste de protocolo falhou." }
    }

    Write-Host "Compilando o agente Windows..." -ForegroundColor Cyan
    & cmd.exe /c windows-agent\build-agent.bat
    if ($LASTEXITCODE -ne 0) { throw "A compilacao do agente falhou." }

    Write-Host "Compilando o aplicativo Android..." -ForegroundColor Cyan
    Push-Location android-app
    try {
        & .\gradlew.bat --no-daemon testDebugUnitTest lintRelease assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "A compilacao Android falhou." }
        Copy-Item -Force "app\build\outputs\apk\debug\app-debug.apk" "SyncDeck.apk"

        if (Test-Path "keystore.properties") {
            Write-Host "Gerando o AAB assinado..." -ForegroundColor Cyan
            & .\gradlew.bat --no-daemon bundleRelease
            if ($LASTEXITCODE -ne 0) { throw "A geracao do AAB falhou." }
        }
    }
    finally {
        Pop-Location
    }

    Write-Host ""
    Write-Host "Build concluido:" -ForegroundColor Green
    Write-Host "- windows-agent\SyncDeckAgent.exe"
    Write-Host "- android-app\SyncDeck.apk"
    if (Test-Path "android-app\app\build\outputs\bundle\release\app-release.aab") {
        Write-Host "- android-app\app\build\outputs\bundle\release\app-release.aab"
    }
}
finally {
    Pop-Location
}
