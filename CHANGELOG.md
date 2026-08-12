# Changelog

Todas as mudanças relevantes do SyncDeck são registradas neste arquivo. O projeto segue [Versionamento Semântico](https://semver.org/lang/pt-BR/).

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
