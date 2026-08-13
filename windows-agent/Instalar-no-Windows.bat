@echo off
setlocal
cd /d "%~dp0"

if not exist "SyncDeckAgent.exe" call build-agent.bat
if errorlevel 1 exit /b 1

set "INSTALL_DIR=%LOCALAPPDATA%\SyncDeck\Agent"
set "INSTALL_EXE=%INSTALL_DIR%\SyncDeckAgent.exe"

if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if errorlevel 1 (
  echo ERRO: nao foi possivel criar %INSTALL_DIR%.
  pause
  exit /b 1
)

taskkill /IM SyncDeckAgent.exe /F >nul 2>&1
timeout /t 1 /nobreak >nul

copy /Y "%~dp0SyncDeckAgent.exe" "%INSTALL_EXE%" >nul
if errorlevel 1 (
  echo ERRO: nao foi possivel instalar o agente.
  pause
  exit /b 1
)

reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v SyncDeckAgent /t REG_SZ /d "\"%INSTALL_EXE%\"" /f >nul
if errorlevel 1 (
  echo ERRO: nao foi possivel ativar o inicio automatico.
  pause
  exit /b 1
)

reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run" /v SyncDeckAgent /f >nul 2>&1

start "" "%INSTALL_EXE%"
echo.
echo SyncDeck Agent instalado em um local permanente e iniciado.
echo O pareamento e os botoes existentes foram preservados.
echo.
echo Agora execute Configurar-Firewall.bat uma vez como administrador.
pause
