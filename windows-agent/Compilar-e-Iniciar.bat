@echo off
setlocal
cd /d "%~dp0"

taskkill /IM SyncDeckAgent.exe /F >nul 2>&1
timeout /t 1 /nobreak >nul

call build-agent.bat
if errorlevel 1 exit /b 1

start "" "%~dp0SyncDeckAgent.exe"
echo SyncDeck foi iniciado e esta na bandeja do Windows.
timeout /t 3 /nobreak >nul
exit /b 0
