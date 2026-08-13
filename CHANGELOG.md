# Changelog

Todas as mudanças relevantes do SyncDeck são registradas neste arquivo. O projeto segue [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [1.0.0] - 2026-08-12

### Adicionado

- Novo assistente visual para criar botões escolhendo **Programa**, **Site**, **Pasta ou arquivo** ou **Comando**.
- Catálogo de programas instalado no Windows e seletores nativos de arquivo/pasta, sem digitar caminhos.
- Aprovação obrigatória no PC ao cadastrar, alterar ou executar comandos e atalhos sensíveis.
- Protocolo de segurança v2 com conteúdo cifrado, respostas autenticadas, limite de requisições e confirmação visual detalhada.
- Geração de Android App Bundle, exemplo de configuração da chave de upload e documentação completa para a Play Store.
- Ícone adaptativo, tema monocromático e materiais iniciais para a listagem pública.
- Instalador simples do agente em uma pasta permanente do perfil do Windows.

### Alterado

- Aplicativo Android totalmente migrado de Java/View para Kotlin e Jetpack Compose.
- Interface redesenhada com glass, gradiente, feedback de estado, animações e navegação mais clara.
- Android atualizado para <code>compileSdk</code>/<code>targetSdk 36</code>, AGP 9.0.1, Kotlin 2.2.10 e Java 17.
- O aplicativo Android agora usa apenas a permissão <code>INTERNET</code> e continua limitado por código a endereços IPv4 privados.
- O agente passa a aceitar no máximo 16 conexões simultâneas e 120 solicitações autenticadas por minuto por dispositivo.

### Segurança

- Segredo de pareamento mantido no Android Keystore e no Windows DPAPI.
- AES-256-CBC com chave derivada por HMAC e HMAC-SHA-256 sobre o conteúdo cifrado, em esquema cifrar-e-autenticar.
- Tokens curtos e descartáveis vinculam escolhas feitas pelo catálogo do PC ao botão salvo.
- Campos de execução e confirmações bloqueiam caracteres de controle e exibem os valores completos no PC.
- A tecla Enter nega a confirmação sensível por padrão, reduzindo aprovações acidentais.

## [0.5.0] - 2026-08-12

### Adicionado

- Aplicativo nativo para iPhone em Swift e SwiftUI, compatível com iOS 15 ou superior.
- Todas as funções do Android no iPhone: pareamento, ações, editor, ícones do Windows, estado das janelas, fechamento múltiplo, rotação e Wake-on-LAN.
- Armazenamento do segredo do iPhone no Keychain, separado do pareamento Android.
- Projeto Xcode completo, ícone em todos os tamanhos exigidos e suporte ao iPhone 11 Pro.
- Build macOS no GitHub Actions que produz o artefato <code>SyncDeck-unsigned.ipa</code> sem guardar conta, senha, certificado ou perfil da Apple.
- Guia de instalação gratuita pelo Windows e renovação semanal da assinatura pessoal.

### Alterado

- Documentação, arquitetura, testes e release agora cobrem Android, iOS e Windows.
- O mesmo agente Windows aceita simultaneamente celulares Android e iPhones pareados.

### Segurança

- O iOS valida a impressão digital RSA e todas as respostas HMAC antes de usar JSON ou ícones.
- A permissão de Rede Local explica exatamente a comunicação com o agente e o Wake-on-LAN.
- Nenhuma credencial Apple é incluída no repositório ou no GitHub Actions.

## [0.4.0] - 2026-08-12

### Adicionado

- Botão local **Ligar PC** por Wake-on-LAN, disponível mesmo quando o agente Windows está offline.
- Descoberta autenticada do MAC, broadcast e interface Ethernet realmente usada pela conexão.
- Envio redundante do Magic Packet para broadcasts da rede atual e configuração salva.
- Guia completo de BIOS/UEFI, Realtek, Inicialização Rápida e diagnóstico do estado S5.

### Alterado

- Ao não localizar o agente, o Android mantém o painel funcional com o botão de ligar em vez de esconder todas as ações.
- Quinze segundos após enviar o sinal, o app tenta localizar novamente o agente sem exigir novo pareamento.

### Segurança

- A configuração Wake-on-LAN é disponibilizada somente após autenticação HMAC e resposta assinada.
- MAC e broadcast são validados e salvos localmente; nenhuma porta externa ou serviço em nuvem é usado.

## [0.3.1] - 2026-08-12

### Adicionado

- Reconexão automática ao mesmo PC quando o endereço IPv4 muda após reiniciar o computador ou o roteador.
- Botão **ChatGPT** que abre <code>https://chatgpt.com/</code> diretamente no perfil atual/último perfil do Chrome.
- Botão **Desligar PC** com aviso explícito e confirmação antes de iniciar o desligamento em cinco segundos.
- Ícones próprios para ChatGPT e desligamento.

### Alterado

- O painel personalizado remove automaticamente Android Studio/Android App e Downloads uma única vez durante a atualização.
- O parâmetro do perfil do Chrome agora é posicionado antes da URL.

### Corrigido

- Gravação do endereço e do pareamento no Android reforçada com persistência síncrona.
- Arquivo de celulares pareados no Windows agora usa gravação atômica e cópia de segurança recuperável.

## [0.3.0] - 2026-08-11

### Adicionado

- Detecção das janelas visíveis reais do Windows, incluindo aplicativos modernos, Explorador e múltiplas janelas.
- Estado aberto/fechado e quantidade de janelas enviados ao Android por resposta autenticada.
- Atualização automática do estado no celular e contorno luminoso para aplicativos abertos.
- Escolha entre fechar uma janela ou todas quando houver múltiplas janelas.
- Seleção automática do perfil ativo ou do último perfil utilizado no Chrome.
- Interface glass com gradiente e animações de abertura e fechamento.

### Corrigido

- Foco de uma janela existente não abre mais uma segunda instância quando o Windows recusa o retorno de `SetForegroundWindow`.
- Reconhecimento de janelas que não aparecem em `Process.MainWindowHandle`.

## [0.2.1] - 2026-08-11

### Corrigido

- Falha ao iniciar no Android 11 por acesso prematuro ao `WindowInsetsController`.

## [0.2.0] - 2026-08-11

### Adicionado

- Modo paisagem em tela cheia, três colunas e somente logos.
- Menu de toque longo no modo paisagem.
- Extração automática dos ícones instalados no Windows.
- Campo de conexão aceitando `IP:porta`.

## [0.1.0] - 2026-08-11

### Adicionado

- Primeiro agente Windows e aplicativo Android.
- Pareamento RSA e autenticação HMAC-SHA-256.
- Ações para aplicativos, sites, arquivos, pastas, comandos e atalhos.
