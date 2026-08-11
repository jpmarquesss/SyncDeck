# Publicação de versões

## 1. Preparar

1. Atualize versões conforme [DEVELOPMENT.md](DEVELOPMENT.md).
2. Atualize <code>CHANGELOG.md</code>.
3. Execute <code>python scripts/validate_repository.py</code>.
4. Compile Windows e Android.
5. Execute a matriz manual relevante.
6. Confirme que <code>git status</code> não contém binários, chaves ou dados locais.

## 2. Assinatura Android

O APK debug do GitHub Actions é adequado apenas para testes. Cada ambiente pode usar uma chave debug diferente; por isso, um APK de CI pode não atualizar uma instalação anterior.

Para releases:

- crie uma chave privada própria e estável;
- mantenha o keystore fora do Git;
- faça backup seguro da chave e das senhas;
- gere um APK release assinado;
- publique também o SHA-256 do arquivo.

Não existe recuperação caso a chave de assinatura seja perdida. Uma chave diferente exige desinstalar o aplicativo anterior, o que remove o pareamento local.

## 3. Agente Windows

Compile em Windows limpo:

~~~powershell
windows-agent\build-agent.bat
~~~

O executável atual não possui assinatura Authenticode. O Windows pode exibir alerta. Não desative SmartScreen globalmente; distribua código-fonte e hashes junto do binário.

## 4. Tag

~~~powershell
git tag -a v0.3.0 -m "SyncDeck 0.3.0"
git push origin v0.3.0
~~~

## 5. GitHub Release

Inclua:

- resumo do changelog;
- requisitos;
- passos de atualização;
- APK release assinado;
- pacote do agente Windows;
- arquivo de código-fonte;
- SHA-256 de cada binário;
- aviso sobre rede privada e instalação manual.

## 6. Verificação

Em PowerShell:

~~~powershell
Get-FileHash .\SyncDeck.apk -Algorithm SHA256
Get-FileHash .\SyncDeckAgent.exe -Algorithm SHA256
~~~

Teste instalação limpa e atualização por cima da versão anterior antes de marcar a release como estável.
