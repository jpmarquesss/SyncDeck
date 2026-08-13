# Solução de problemas

## O app abre e fecha no Android

1. No Android Studio, selecione o aparelho e abra **Logcat**.
2. Filtre por `package:com.syncdeck.app level:ERROR`.
3. Procure a primeira linha `Caused by` e abra o arquivo/linha apontado.
4. Confirme que instalou o APK 1.0.1 gerado do código atual.

Se veio de uma versão antiga assinada por outra chave, desinstale antes de instalar. Isso apaga o pareamento apenas do celular; o agente preserva seus botões.

## Gradle não encontra `android.jar`

No Android Studio, abra **Settings > Languages & Frameworks > Android SDK**:

1. desmarque a plataforma Android 16/API 36 com erro e aplique;
2. marque novamente **Android SDK Platform 36**;
3. em **SDK Tools**, confirme Build-Tools, Platform-Tools e Command-line Tools;
4. use **File > Invalidate Caches / Restart** e sincronize o Gradle.

O projeto exige JDK 17, AGP 9.0.1, Gradle 9.1 e SDK 36.

## “Use um endereço privado”

Digite apenas o IPv4 do PC, normalmente `192.168.x.x` ou `10.x.x.x`, sem `http://`, barra ou nome do computador. A porta padrão é `47321`.

No Windows:

```cmd
ipconfig
```

Use o IPv4 da Ethernet conectada ao mesmo roteador do Wi-Fi do celular. VPN, rede de convidados e isolamento de clientes podem impedir a conexão.

## PC não conecta depois de reiniciar

Execute uma vez:

```cmd
windows-agent\Instalar-no-Windows.bat
```

Esse script instala em `%LOCALAPPDATA%\SyncDeck\Agent`, registra o início automático e inicia a cópia permanente. Evite executar o agente dentro de Downloads, pois a pasta pode ser movida ou removida.

Depois do login, confira:

```cmd
tasklist /FI "IMAGENAME eq SyncDeckAgent.exe"
netstat -ano | findstr :47321
```

O resultado esperado é uma única instância e `0.0.0.0:47321 LISTENING`. Muitas linhas `TIME_WAIT` são conexões curtas normais do polling.

## O agente mostra que não pode iniciar na porta

Já existe outra instância ou processo usando `47321`.

```cmd
netstat -ano | findstr :47321
tasklist /FI "PID eq NUMERO_ENCONTRADO"
```

Feche a instância antiga, execute o instalador permanente e confirme que só um `SyncDeckAgent.exe` ficou ativo. O agente 1.0 também usa mutex para impedir duplicatas.

## Celular perde o pareamento no dia seguinte

O Android guarda o segredo no Keystore e o agente mantém uma cópia DPAPI com backup. Ele também procura o mesmo fingerprint se o IP mudar.

Se ainda falhar:

1. confirme que Android e agente são 1.0.1;
2. não limpe dados do app nem use otimizador que apague armazenamento;
3. confira se `%LOCALAPPDATA%\SyncDeck\clients.json` e `clients.backup.json` existem;
4. use o instalador permanente do agente;
5. toque em atualizar e aguarde até sete segundos pela busca de IP.

Só despareie depois dessas verificações.

## Firewall ou rede privada

Execute `Configurar-Firewall.bat` como administrador. Em **Configurações > Rede e Internet > Ethernet**, mantenha o perfil como **Privada**. A regra foi criada para TCP/47321, programa instalado e `remoteip=localsubnet`.

Não crie redirecionamento da porta no roteador e não use o app por IP público.

## Programa abre, mas não fica marcado como aberto

O agente conta janelas visíveis, não apenas processos. Edite o botão e revise:

- `ProcessNames`, sem `.exe`;
- `AppNames`, como aparece no Menu Iniciar;
- destino correto do executável;
- se o app é hospedado por `ApplicationFrameHost` ou `WindowsTerminal`.

Feche e abra o programa uma vez, toque em atualizar e confira se existe uma janela normal na mesma sessão do usuário.

## O botão não consegue fechar

O fechamento usa `WM_CLOSE`; aplicativos elevados, caixas modais, janelas de outra sessão ou programas que ignoram esse pedido podem recusar. Execute o agente e o programa no mesmo usuário e nível de privilégio. O SyncDeck não força `taskkill` para evitar perda de dados.

## Chrome pede para escolher conta/perfil

Abra manualmente o perfil desejado uma vez e feche o Chrome normalmente. O agente usa, nesta ordem, uma janela Chrome já aberta, `last_active_profiles`, `last_used` e `Default`.

Remova qualquer argumento `--profile-directory` incorreto do botão. Um argumento manual sempre tem prioridade.

## Adicionar programa não lista o aplicativo

O catálogo consulta App Paths e Menu Iniciar. Se um programa portátil não aparecer, use **Pasta ou arquivo > Escolher arquivo no PC** e selecione o `.exe`. O PC cria um token confiável sem expor navegação de arquivos ao celular.

## Comando foi negado

Isso é esperado até autorizar também na janela do PC. Confira destino, argumentos, diretório e celular solicitante. Enter, fechar a janela ou esperar 45 segundos significa **Negar**.

Se nenhuma janela apareceu, desbloqueie a sessão do Windows e confirme que o ícone do agente está perto do relógio.

## Wake-on-LAN não liga o PC

Siga [WAKE-ON-LAN.md](WAKE-ON-LAN.md). Confirme BIOS/UEFI, Magic Packet no driver, gerenciamento de energia, inicialização rápida e LED da porta Ethernet após desligar.

O botão só aparece offline depois que o app sincronizou a configuração ao menos uma vez com o PC ligado.

## Ainda não resolveu

Abra uma issue com versão, Windows/Android, passos e mensagem exata. Remova IP, MAC, fingerprint, código de pareamento, caminhos pessoais e capturas bancárias. Vulnerabilidades devem seguir [SECURITY.md](../SECURITY.md), sem issue pública.
