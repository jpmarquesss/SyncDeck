@echo off
setlocal
cd /d "%~dp0"

call gradlew.bat assembleDebug
if errorlevel 1 (
  echo.
  echo Nao foi possivel gerar o APK. Abra esta pasta no Android Studio e confira o SDK 36 e o JDK 17.
  pause
  exit /b 1
)

copy /y "app\build\outputs\apk\debug\app-debug.apk" "SyncDeck.apk" >nul
echo.
echo APK criado: %~dp0SyncDeck.apk
pause
