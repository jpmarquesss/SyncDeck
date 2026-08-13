@echo off
setlocal
cd /d "%~dp0"

net session >nul 2>&1
if not "%errorlevel%"=="0" (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

set "AGENT_EXE=%LOCALAPPDATA%\SyncDeck\Agent\SyncDeckAgent.exe"
if not exist "%AGENT_EXE%" set "AGENT_EXE=%~dp0SyncDeckAgent.exe"
if not exist "%AGENT_EXE%" call build-agent.bat
if errorlevel 1 exit /b 1
if not exist "%AGENT_EXE%" set "AGENT_EXE=%~dp0SyncDeckAgent.exe"

netsh advfirewall firewall delete rule name="SyncDeck Agent" >nul 2>&1
netsh advfirewall firewall add rule name="SyncDeck Agent" dir=in action=allow ^
  program="%AGENT_EXE%" protocol=TCP localport=47321 ^
  profile=private remoteip=localsubnet enable=yes

if errorlevel 1 (
  echo Nao foi possivel criar a regra do Firewall.
) else (
  echo Regra criada somente para redes privadas e dispositivos da rede local.
)
pause
