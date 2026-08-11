@echo off
setlocal
cd /d "%~dp0"

where git >nul 2>&1
if errorlevel 1 (
  echo ERRO: Git para Windows nao foi encontrado.
  echo Instale em https://git-scm.com/download/win e tente novamente.
  pause
  exit /b 1
)

echo.
echo Cole a URL HTTPS do repositorio vazio criado no GitHub.
echo Exemplo: https://github.com/SEU-USUARIO/SyncDeck.git
set /p "SYNCDECK_REPO_URL=URL: "

if "%SYNCDECK_REPO_URL%"=="" (
  echo ERRO: nenhuma URL foi informada.
  pause
  exit /b 1
)

python scripts\validate_repository.py
if errorlevel 1 (
  echo.
  echo A validacao falhou. Corrija os itens mostrados antes de publicar.
  pause
  exit /b 1
)

if not exist ".git" git init
set /p "SYNCDECK_VERSION="<VERSION
git add .
git diff --cached --quiet
if errorlevel 1 git commit -m "feat: publica SyncDeck %SYNCDECK_VERSION%"
if errorlevel 1 (
  echo.
  echo Nao foi possivel criar o commit. Configure seu nome e email do Git.
  echo Consulte REPOSITORY-SETUP.md.
  pause
  exit /b 1
)

git branch -M main
git remote get-url origin >nul 2>&1
if errorlevel 1 (
  git remote add origin "%SYNCDECK_REPO_URL%"
) else (
  git remote set-url origin "%SYNCDECK_REPO_URL%"
)

git push -u origin main
if errorlevel 1 (
  echo.
  echo O envio falhou. Confira a URL e o login do GitHub.
  pause
  exit /b 1
)

echo.
echo SyncDeck publicado com sucesso.
pause
