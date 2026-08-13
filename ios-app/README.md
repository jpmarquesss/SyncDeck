# SyncDeck para iPhone

Aplicativo nativo SwiftUI experimental do SyncDeck 0.5.0. O projeto não usa CocoaPods, Swift Package Manager nem serviço em nuvem.

> O cliente permanece compatível com o agente 1.0, mas ainda usa o protocolo HMAC legado sem a cifra de conteúdo v2 do Android. Não o trate como a distribuição pública 1.0 antes de migrar e revisar essa camada.

## Compatibilidade

- iOS 15 ou superior.
- iPhone somente; o layout foi dimensionado também para o iPhone 11 Pro.
- Retrato com duas colunas e paisagem com três colunas/logos.

## Abrir no Xcode

Abra <code>SyncDeck.xcodeproj</code>, selecione o target **SyncDeck** e escolha uma equipe em **Signing & Capabilities** para executar em aparelho real. O bundle ID padrão é <code>com.eudollyn.syncdeck</code>; altere-o se a conta Apple informar que ele não está disponível.

## Compilar sem Mac

O job <code>ios</code> de [build.yml](../.github/workflows/build.yml):

1. valida o vetor HMAC em Swift;
2. compila para um iPhone genérico com assinatura desabilitada;
3. empacota <code>Payload/SyncDeck.app</code>;
4. publica <code>SyncDeck-unsigned.ipa</code> e seu SHA-256.

O artefato não assinado não contém conta ou certificado Apple. Para instalar pelo Windows, siga [INSTALAR-NO-IPHONE.txt](../INSTALAR-NO-IPHONE.txt).

## Arquivos principais

| Arquivo | Função |
|---|---|
| <code>SyncDeckApp.swift</code> | Entrada SwiftUI |
| <code>ContentView.swift</code> | Painel, cartões, rotação e gestos |
| <code>DeckViewModel.swift</code> | Estado, polling, ações e mensagens |
| <code>APIClient.swift</code> | Protocolo local, descoberta, ícones e Wake-on-LAN |
| <code>Security.swift</code> | Keychain, RSA, HMAC e SHA-256 |
| <code>ConnectionView.swift</code> | IP, fingerprint e pareamento |
| <code>ActionEditorView.swift</code> | Editor de botões |
| <code>Info.plist</code> | Rede Local, orientações e transporte local |

## Dados locais

- O segredo de pareamento fica no Keychain como <code>WhenUnlockedThisDeviceOnly</code>.
- IP, fingerprint e Wake-on-LAN ficam em <code>UserDefaults</code>.
- Imagens dos aplicativos ficam apenas no cache e podem ser baixadas novamente.
- Ações e clientes autorizados continuam armazenados no agente Windows.

Não adicione certificados, perfis <code>.mobileprovision</code>, arquivos <code>.p12</code>, IPAs assinados ou credenciais Apple ao Git.
