# Desenvolvimento

## Ambiente

| Parte | Ferramentas |
|---|---|
| Android | Android Studio, JDK 17, Android SDK 36 |
| Windows | Windows 10/11 e .NET Framework 4.8 |
| Validação | Python 3 e JDK 17 |
| iOS experimental | Xcode 16 em macOS ou GitHub Actions |

Abra `android-app`, e não a raiz inteira, no Android Studio.

## Primeiro build

```powershell
git clone https://github.com/SEU-USUARIO/SyncDeck.git
cd SyncDeck
python scripts/validate_repository.py

windows-agent\build-agent.bat

cd android-app
.\gradlew.bat testDebugUnitTest lintRelease assembleDebug
```

Saídas:

- agente: `windows-agent\SyncDeckAgent.exe`;
- APK de teste: `android-app\app\build\outputs\apk\debug\app-debug.apk`;
- AAB assinado: `android-app\app\build\outputs\bundle\release\app-release.aab`.

`Gerar-APK.bat` copia o APK para `android-app\SyncDeck.apk`. `Gerar-AAB.bat` exige `keystore.properties`; veja [PLAY-STORE.md](PLAY-STORE.md).

## Organização do Android

O aplicativo usa Kotlin 2.2.10 e Jetpack Compose, com estado centralizado em `DeckController`.

- Mantenha a UI declarativa em `MainActivity.kt` e `ActionWizard.kt`.
- Faça rede, disco e criptografia fora do thread principal.
- Converta dados externos em modelos validados antes de atualizar o estado.
- Preserve `minSdk 26` e teste retrato e paisagem.
- Não adicione permissão, SDK de telemetria ou dependência sem atualizar privacidade, Data Safety e modelo de ameaça.
- Não registre segredo, código de pareamento, caminhos, IP/MAC ou payload descriptografado.

## Organização do agente

O agente compila os arquivos de `windows-agent/src` diretamente com o compilador do .NET Framework.

```powershell
windows-agent\build-agent.bat
windows-agent\Instalar-no-Windows.bat
```

Evite APIs exclusivas de .NET moderno. Efeitos do sistema devem passar por `ActionExecutor`; autorização, por `DeckServer`/`DesktopSecurity`; validação e persistência, pelos stores.

## Adicionar ou alterar uma rota

1. Defina modelos limitados em `windows-agent/src/Models.cs`.
2. Adicione a rota depois de `Authenticate` em `DeckServer.cs`.
3. Imponha limites antes de ler, desserializar ou executar.
4. Adicione o método em `ApiClient.kt` e, se mantiver compatibilidade, em `ios-app/SyncDeck/APIClient.swift`.
5. Atualize [PROTOCOL.md](PROTOCOL.md), vetores e matriz de testes.
6. Para uma operação perigosa, inclua autorização explícita no Android e confirmação independente em `DesktopSecurity`.

Nunca aceite comando arbitrário em `/api/execute`; a rota recebe apenas `ActionId` e operação.

## Alterar o protocolo

- Preserve propriedades JSON existentes quando possível.
- Incremente a versão do protocolo se alterar cifra, derivação, strings canônicas ou semântica de segurança.
- Autentique o texto cifrado antes de descriptografá-lo.
- Atualize `tests/protocol-vector.json` e `ProtocolVectorTest.java`.
- Mantenha limites iguais nos dois lados e trate dados desconhecidos como não confiáveis.
- Documente compatibilidade e migração; não faça downgrade silencioso no Android público.

## Ações padrão

As ações de uma instalação nova ficam em `ActionStore.CreateDefaults`, em `windows-agent/src/Stores.cs`. Não sobrescreva `actions.json` existente. Uma mudança de layout precisa de migração idempotente e marcador próprio.

## Versões

Na versão pública Android/Windows, atualize em conjunto:

- `android-app/app/build.gradle`: `versionCode` e `versionName`;
- `windows-agent/src/DeckServer.cs` e `AgentContext.cs`;
- `windows-agent/app.manifest` e `src/AssemblyInfo.cs`;
- `VERSION`, `README.md` e `CHANGELOG.md`.

O iOS experimental pode ter versão própria, declarada no projeto Xcode e no seu README.

## Dados de teste

O agente usa `%LOCALAPPDATA%\SyncDeck`. Antes de testar migrações, faça backup. Para simular primeiro uso, renomeie temporariamente a pasta em vez de apagá-la.

Nunca coloque no Git:

- `clients.json`, `actions.json` ou `settings.json` reais;
- `local.properties`, `keystore.properties` ou chaves;
- APK, AAB, EXE, IPA, PDB ou certificados;
- IP, MAC, código de pareamento ou capturas com dados pessoais.

## iPhone experimental

O projeto SwiftUI exige iOS 15. Sem Mac, o workflow compila um IPA não assinado. Isso valida o código, mas não substitui assinatura Apple nem teste em aparelho real. Consulte [ios-app/README.md](../ios-app/README.md).
