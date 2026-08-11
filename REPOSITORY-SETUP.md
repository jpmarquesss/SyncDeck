# Publicar o SyncDeck no GitHub

O pacote já contém README, licença MIT, documentação, workflows, modelos de issues e regras para não enviar arquivos sensíveis.

## 1. Criar o repositório

No GitHub, crie um repositório chamado `SyncDeck` com estas sugestões:

- **Description:** `Use um celular Android como painel Stream Deck seguro para controlar um PC Windows pela rede local.`
- **Visibility:** Public.
- Não marque criação automática de README, `.gitignore` ou licença, pois esses arquivos já existem.
- **Topics:** `android`, `windows`, `stream-deck`, `remote-control`, `local-network`, `java`, `csharp`, `hmac`.

## 2. Publicar pelo script do Windows

1. Instale o [Git para Windows](https://git-scm.com/download/win).
2. Extraia o projeto.
3. Execute `PUBLICAR-NO-GITHUB.bat`.
4. Cole a URL HTTPS do repositório, por exemplo: `https://github.com/SEU-USUARIO/SyncDeck.git`.
5. Faça login no GitHub se o Git solicitar.

O script não pede nem armazena token.

## 3. Publicar manualmente

Abra o Terminal na raiz do projeto e execute:

```powershell
git init
git add .
git commit -m "feat: publica SyncDeck 0.3.0"
git branch -M main
git remote add origin https://github.com/SEU-USUARIO/SyncDeck.git
git push -u origin main
```

Se o Git ainda não conhecer sua identidade:

```powershell
git config --global user.name "Seu nome"
git config --global user.email "seu-email-publico-ou-noreply-do-github"
```

## 4. Configurar o GitHub

Depois do primeiro envio:

1. Em **Settings > General**, ative Issues.
2. Em **Settings > Actions > General**, mantenha permissões de workflow somente para leitura.
3. Em **Settings > Code security**, ative Dependabot alerts e Private vulnerability reporting.
4. Proteja a branch `main`, exigindo o workflow de validação para Pull Requests.
5. Para publicar binários, siga [docs/RELEASING.md](docs/RELEASING.md).

## Antes de tornar público

Execute:

```powershell
python scripts/validate_repository.py
```

Não envie `SyncDeck.apk`, `SyncDeckAgent.exe`, `local.properties`, keystores, arquivos `.env` ou a pasta `%LOCALAPPDATA%\SyncDeck`.
