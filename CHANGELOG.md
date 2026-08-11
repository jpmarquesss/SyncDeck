# Changelog

Todas as mudanças relevantes do SyncDeck são registradas neste arquivo. O projeto segue [Versionamento Semântico](https://semver.org/lang/pt-BR/).

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
