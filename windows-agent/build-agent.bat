@echo off
setlocal
cd /d "%~dp0"

set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" set "CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"

if not exist "%CSC%" (
  echo ERRO: compilador do .NET Framework nao encontrado.
  echo Ative o .NET Framework 4.8 nos Recursos do Windows e tente novamente.
  pause
  exit /b 1
)

echo Compilando SyncDeck Agent...
"%CSC%" /nologo /target:winexe /optimize+ /platform:anycpu ^
  /out:"SyncDeckAgent.exe" /win32manifest:"app.manifest" ^
  /reference:System.dll /reference:System.Core.dll /reference:System.Drawing.dll ^
  /reference:System.Security.dll /reference:System.Web.Extensions.dll ^
  /reference:System.Windows.Forms.dll src\*.cs

if errorlevel 1 (
  echo.
  echo A compilacao falhou. Confira as mensagens acima.
  pause
  exit /b 1
)

echo SyncDeckAgent.exe criado com sucesso.
exit /b 0
