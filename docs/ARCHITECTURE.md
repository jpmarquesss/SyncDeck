# Arquitetura

## Objetivo

O SyncDeck separa interface e execução: o Android apresenta o painel, enquanto o Windows mantém as ações autorizadas e interage com o sistema operacional. A comunicação ocorre diretamente na rede local, sem serviço em nuvem.

## Componentes

~~~mermaid
flowchart TD
    subgraph Android
        UI["MainActivity<br/>painel, estados e editor"]
        API["ApiClient<br/>HTTP, cache e assinaturas"]
        STORE["SecureStore<br/>Android Keystore"]
        UI --> API
        API --> STORE
    end
    subgraph Windows
        SERVER["DeckServer<br/>rotas e autenticação"]
        EXEC["ActionExecutor<br/>abrir, focar e fechar"]
        WINDOWS["WindowInspector<br/>janelas visíveis"]
        ICONS["IconResolver<br/>extração de ícones"]
        DATA["Stores<br/>ações, clientes e ajustes"]
        SERVER --> EXEC
        EXEC --> WINDOWS
        SERVER --> ICONS
        SERVER --> DATA
    end
    API <-->|"IPv4 privado · porta 47321"| SERVER
~~~

## Responsabilidades

### Android

| Classe | Responsabilidade |
|---|---|
| <code>MainActivity</code> | Montar a interface, executar ações, animar cartões e atualizar estados |
| <code>ApiClient</code> | Construir requisições, validar respostas, buscar ações/ícones e administrar threads |
| <code>SecureStore</code> | Proteger o segredo do cliente com Android Keystore e AES-GCM |
| <code>SignatureUtil</code> | Hash SHA-256, HMAC e Base64URL |
| <code>SyncAction</code> | Modelo JSON de uma ação |
| <code>ActionEditorDialog</code> | Editar ações pelo celular |

### Windows

| Classe | Responsabilidade |
|---|---|
| <code>AgentContext</code> | Ciclo de vida do agente, bandeja e inicialização |
| <code>DeckServer</code> | Servidor TCP/HTTP, autenticação e roteamento |
| <code>ActionExecutor</code> | Abrir, focar, fechar e executar ações |
| <code>WindowInspector</code> | Enumerar janelas Win32 e relacioná-las aos processos |
| <code>ChromeProfileResolver</code> | Selecionar o perfil ativo/último do Chrome |
| <code>IconResolver</code> | Resolver executáveis, AppIDs e PNGs |
| <code>ActionStore</code> | Validar e persistir ações |
| <code>ClientStore</code> | Persistir segredos protegidos por DPAPI |
| <code>PairingManager</code> | Código temporário e chave RSA do agente |

## Fluxo de abertura

~~~mermaid
sequenceDiagram
    participant A as Android
    participant S as DeckServer
    participant E as ActionExecutor
    participant W as Windows
    A->>S: POST /api/execute (assinado)
    S->>S: Autentica e localiza ActionId
    S->>E: Execute(action, open)
    E->>W: Procura janela visível
    alt Janela encontrada
        E->>W: Restaura e traz para frente
    else Janela ausente
        E->>W: Inicia destino configurado
    end
    S-->>A: Resposta assinada
~~~

## Fluxo de estado

1. O Android carrega <code>/api/actions</code>, que já inclui <code>IsOpen</code> e <code>WindowCount</code>.
2. Enquanto a Activity está ativa, consulta <code>/api/actions/state</code> aproximadamente a cada 2,4 segundos.
3. O agente executa uma única captura das janelas visíveis e compara cada ação com nomes de processo, executável e pistas de título.
4. O Android atualiza apenas o estilo dos cartões alterados.
5. Processos em segundo plano sem janela visível permanecem fechados para fins de interface.

## Detecção de janelas

<code>WindowInspector</code> usa <code>EnumWindows</code>, filtra janelas invisíveis, pertencentes a outra janela, ocultas pelo DWM e superfícies do shell. Para aplicativos UWP, também inspeciona processos de janelas filhas. O resultado é ordenado pela ordem retornada pelo Windows, normalmente próxima da ordem Z.

Hosts intermediários conhecidos, como <code>ApplicationFrameHost</code> e <code>WindowsTerminal</code>, podem usar o título como pista adicional. Títulos são usados somente dentro do PC e nunca são enviados ao Android.

## Persistência

O agente grava no perfil do usuário:

| Arquivo | Conteúdo |
|---|---|
| <code>%LOCALAPPDATA%\SyncDeck\actions.json</code> | Ações configuradas |
| <code>%LOCALAPPDATA%\SyncDeck\clients.json</code> | Clientes pareados e segredos protegidos |
| <code>%LOCALAPPDATA%\SyncDeck\settings.json</code> | Porta e inicialização |

O Android usa SharedPreferences para endpoint/ID e Android Keystore para o segredo. Ícones ficam apenas no cache da aplicação.

## Limites de confiança

- A rede local não é tratada como confiável: comandos e respostas exigem HMAC.
- O usuário precisa conferir a impressão digital no pareamento.
- Um dispositivo pareado é confiável para executar todas as ações salvas.
- O agente executa no mesmo nível de integridade do usuário. Uma janela elevada pode recusar foco ou fechamento.
- HTTP autenticado evita alteração e repetição, mas não oferece confidencialidade.

## Decisões técnicas

- **Sem backend:** menor superfície operacional e nenhuma conta externa.
- **C#/.NET Framework:** compilação disponível no próprio Windows, sem instalador de SDK adicional.
- **Android Java nativo:** APK pequeno e poucas dependências.
- **HTTP mínimo sobre TcpListener:** protocolo simples, porém exige cuidado manual com parsing e limites.
- **WM_CLOSE:** preserva o comportamento normal do aplicativo e evita encerramento forçado.
