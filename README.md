# SyncDeck

Use um celular Android como um painel Stream Deck para abrir, focar e fechar aplicativos em um PC Windows pela rede local.

**Versão atual:** 0.3.0 · **Licença:** MIT · **Status:** beta funcional

> O SyncDeck foi pensado para funcionar sem nuvem: celular e computador se comunicam diretamente pela mesma rede Wi-Fi privada. O projeto não acessa aplicativos bancários, notificações, contatos, câmera, microfone ou arquivos do Android.

## Conteúdo

- [Recursos](#recursos)
- [Como funciona](#como-funciona)
- [Instalação rápida](#instalação-rápida)
- [Uso](#uso)
- [Adicionar aplicativos](#adicionar-aplicativos)
- [Desenvolvimento](#desenvolvimento)
- [Segurança e privacidade](#segurança-e-privacidade)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Documentação](#documentação)

## Recursos

- Abre aplicativos, sites, pastas, arquivos, comandos e atalhos do Windows.
- Traz para frente uma janela já aberta sem iniciar outra instância.
- Detecta janelas visíveis reais, inclusive Explorer e aplicativos modernos.
- Atualiza automaticamente o estado aberto/fechado no Android.
- Exibe contorno luminoso quando o aplicativo está aberto.
- Quando existem várias janelas, permite fechar somente uma ou todas.
- Abre o Chrome diretamente no perfil ativo ou no último perfil utilizado.
- Busca automaticamente o ícone real do aplicativo no PC.
- Modo retrato para configuração e modo paisagem em tela cheia com três colunas.
- Interface escura glass, gradiente discreto e animações.
- Editor de botões no Android e no agente Windows.
- Agente silencioso na bandeja e inicialização automática opcional.
- Pareamento autenticado, respostas assinadas e proteção contra repetição.
- Funciona somente em IPv4 privado/local.

## Como funciona

~~~mermaid
flowchart LR
    A["Android<br/>painel e editor"] <-->|"Wi-Fi local<br/>HTTP + HMAC"| B["Agente Windows<br/>porta 47321"]
    B --> C["Janelas e processos"]
    B --> D["Aplicativos, sites<br/>pastas e comandos"]
    B --> E["Ícones instalados"]
~~~

O Android envia apenas o identificador de uma ação previamente salva. O agente valida a autenticação, localiza a configuração e executa a ação no Windows.

Mais detalhes estão em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) e [docs/PROTOCOL.md](docs/PROTOCOL.md).

## Requisitos

### Windows

- Windows 10 ou 11.
- .NET Framework 4.8.
- Rede configurada como **Privada**.

### Android

- Android 8.0/API 26 ou superior.
- Celular conectado à mesma rede Wi-Fi privada do PC.

### Para compilar

- Android Studio.
- JDK 17.
- Android SDK Platform 36.
- Git e Python 3 recomendados para desenvolvimento.

## Instalação rápida

### 1. Agente Windows

1. Abra <code>windows-agent</code>.
2. Execute <code>Compilar-e-Iniciar.bat</code>.
3. Execute <code>Configurar-Firewall.bat</code> uma vez e confirme a elevação.
4. Procure o ícone do SyncDeck próximo ao relógio.
5. Clique com o botão direito e selecione **Parear celular**.

O agente compila para <code>windows-agent/SyncDeckAgent.exe</code>. Esse arquivo é ignorado pelo Git.

### 2. Aplicativo Android

1. Abra <code>android-app</code> no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Execute <code>Gerar-APK.bat</code> ou use **Run**.
4. O APK será copiado para <code>android-app/SyncDeck.apk</code>.
5. Transfira o APK ao celular e instale-o.
6. Desative novamente **Instalar apps desconhecidos**.

Para atualizar sem perder o pareamento, gere e assine o APK no mesmo computador/keystore e instale por cima.

### 3. Pareamento

1. No Windows, abra **Parear celular**.
2. No Android, toque em <code>•••</code>.
3. Informe o IP privado e a porta exibidos no agente.
4. Toque em **Verificar PC**.
5. Compare a impressão digital nas duas telas.
6. Digite o código de seis números.

O código expira em cinco minutos e possui limite de tentativas.

## Uso

| Gesto/controle | Resultado |
|---|---|
| Toque em um cartão | Abre o item ou traz sua janela para frente |
| Botão <code>×</code> | Pede confirmação e fecha normalmente |
| Várias janelas | Pergunta se deve fechar uma ou todas |
| Toque longo em retrato | Abre o editor do botão |
| Toque longo em paisagem | Abre o menu Abrir, Fechar ou Editar |
| Botão <code>＋</code> | Adiciona uma ação |
| Botão <code>↻</code> | Atualiza conexão, botões, ícones e estados |
| Botão <code>•••</code> | Configura conexão e pareamento |

O indicador é atualizado aproximadamente a cada 2,4 segundos. Processos sem janela visível não são marcados como abertos.

## Adicionar aplicativos

É possível adicionar pelo botão <code>＋</code> no Android ou por **Editar botões** no ícone da bandeja do Windows.

| Campo | Exemplo para Android Studio |
|---|---|
| Nome | <code>Android Studio</code> |
| Identificador | <code>android-studio</code> |
| Tipo | <code>app</code> |
| Destino | <code>C:\Program Files\Android\Android Studio\bin\studio64.exe</code> |
| Processos | <code>studio64</code> |
| Nomes no Menu Iniciar | <code>Android Studio</code> |
| Cor | <code>#3DDC84</code> |
| Pode fechar | Marcado |

Para descobrir o executável, abra o programa, use <code>Ctrl + Shift + Esc</code>, acesse **Detalhes**, clique no processo e escolha **Abrir local do arquivo**.

| Tipo | Destino esperado | Exemplo |
|---|---|---|
| <code>app</code> | Executável ou protocolo | <code>chrome.exe</code> |
| <code>url</code> | URL HTTPS/HTTP | <code>https://example.com</code> |
| <code>path</code> | Arquivo ou pasta | <code>%USERPROFILE%\Downloads</code> |
| <code>command</code> | Executável; argumentos separados | <code>shutdown.exe</code> |
| <code>hotkey</code> | Sintaxe de SendKeys | <code>^+{ESC}</code> |

Ações <code>command</code> são sempre consideradas sensíveis e exigem confirmação.

## Desenvolvimento

### Validar

~~~powershell
python scripts/validate_repository.py
~~~

### Compilar o agente

~~~powershell
windows-agent\build-agent.bat
~~~

### Compilar o Android

~~~powershell
cd android-app
.\gradlew.bat assembleDebug
~~~

### Compilar as duas partes

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1
~~~

O GitHub Actions executa builds independentes para Windows e Android. O APK de CI usa assinatura de depuração temporária e serve para testes; releases atualizáveis devem usar uma chave privada estável.

Leia [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md), [docs/TESTING.md](docs/TESTING.md), [docs/RELEASING.md](docs/RELEASING.md) e [CONTRIBUTING.md](CONTRIBUTING.md).

## Segurança e privacidade

### Permissões Android

- <code>INTERNET</code>
- <code>ACCESS_NETWORK_STATE</code>
- <code>VIBRATE</code>

Não são solicitados SMS, contatos, armazenamento, câmera, microfone, notificações, localização ou acessibilidade.

### Proteções

- Rede limitada a IPv4 privado/local.
- Pareamento RSA de 2048 bits com conferência da impressão digital.
- Segredo de 256 bits criado no Android.
- HMAC-SHA-256 em todas as requisições autenticadas e respostas.
- Timestamp e nonce contra repetição.
- Android Keystore/AES-GCM no celular.
- Windows DPAPI no perfil do usuário.
- Fechamento normal por <code>WM_CLOSE</code>; o agente não mata processos.
- Limites de tamanho e dimensão para ícones.

O transporte local usa HTTP autenticado, mas não cifrado. Um observador da mesma rede pode ver metadados como nomes, imagens e estado dos botões. Use somente rede privada confiável. Títulos das janelas não são enviados ao Android.

Leia [SECURITY.md](SECURITY.md).

## Estrutura do repositório

- <code>android-app/</code>: aplicativo Android nativo em Java.
- <code>windows-agent/</code>: agente Windows em C#/.NET Framework.
- <code>tests/</code>: vetores criptográficos compartilhados.
- <code>scripts/</code>: validação e build para desenvolvedores.
- <code>docs/</code>: arquitetura, protocolo, testes, releases e troubleshooting.
- <code>.github/</code>: workflows, Dependabot e modelos comunitários.

## Documentação

| Documento | Conteúdo |
|---|---|
| [Arquitetura](docs/ARCHITECTURE.md) | Componentes, responsabilidades e fluxos |
| [Ações](docs/ACTIONS.md) | Campos, validação e exemplos |
| [Protocolo](docs/PROTOCOL.md) | Rotas, autenticação e estruturas JSON |
| [Desenvolvimento](docs/DEVELOPMENT.md) | Ambiente e fluxo de trabalho |
| [Testes](docs/TESTING.md) | Validação automática e matriz manual |
| [Releases](docs/RELEASING.md) | Versões, assinatura e publicação |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Erros conhecidos e diagnóstico |
| [Changelog](CHANGELOG.md) | Histórico de versões |
| [Roadmap](ROADMAP.md) | Melhorias planejadas |
| [Publicação no GitHub](REPOSITORY-SETUP.md) | Primeiro push e configurações |

## Compatibilidade

| Componente | Versão mínima |
|---|---|
| Android | 8.0 / API 26 |
| Target Android | API 36 |
| Java | 17 |
| Windows | 10 |
| .NET Framework | 4.8 |

## Contribuição

Issues e Pull Requests são bem-vindos. Leia [CONTRIBUTING.md](CONTRIBUTING.md) e [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Licença

SyncDeck é distribuído sob a [Licença MIT](LICENSE). Copyright © 2026 Erick Carmo.
