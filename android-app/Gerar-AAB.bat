@echo off
setlocal
cd /d "%~dp0"

if not exist "keystore.properties" (
  echo ERRO: crie keystore.properties usando keystore.properties.example.
  echo A chave de assinatura e obrigatoria para gerar o pacote de release.
  pause
  exit /b 1
)

call gradlew.bat clean bundleRelease
if errorlevel 1 (
  echo.
  echo A geracao do AAB falhou. Confira as mensagens acima.
  pause
  exit /b 1
)

echo.
echo AAB criado em:
echo %~dp0app\build\outputs\bundle\release\app-release.aab
pause
