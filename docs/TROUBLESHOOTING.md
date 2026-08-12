# Solução de problemas

## O Android Studio não gera o APK

### Cannot create mockable android.jar

Se o erro apontar para <code>platforms\android-36\android.jar</code>, a plataforma do SDK provavelmente está corrompida:

1. Abra **Settings > Languages & Frameworks > Android SDK**.
2. Em **SDK Platforms**, remova Android API 36.0.
3. Aplique a remoção.
4. Marque novamente API 36.0 e reinstale.
5. Confirme Android SDK Build-Tools, Platform-Tools e Command-line Tools.
6. Use **File > Invalidate Caches / Restart**.

### JDK incompatível

Configure o Gradle JDK como 17. O projeto usa Android Gradle Plugin 9 e Java 17.

## O aplicativo abre e fecha

Abra o Logcat e procure a primeira linha <code>Caused by</code>. A falha conhecida de <code>WindowInsetsController</code> no Android 11 foi corrigida na 0.2.1. Confirme que a versão instalada é 0.3.1 ou superior.

## Precisa parear novamente depois de reiniciar o PC

A partir da versão 0.3.1, o app guarda a impressão digital do agente e procura automaticamente o mesmo PC se o roteador mudar seu IP. A primeira tentativa pode levar até cerca de sete segundos.

1. Atualize o agente do Windows e o APK para 0.3.1.
2. Instale o APK por cima do atual; não desinstale.
3. Abra o app uma vez enquanto a conexão atual ainda funciona, para registrar a impressão digital.
4. Confirme que o agente inicia com o Windows.

O Windows também mantém uma cópia de segurança de <code>clients.json</code>. Se o APK for desinstalado, assinado com outra chave ou os celulares forem revogados no agente, um novo pareamento será necessário.

## O Android pede endereço privado

Use o endereço exibido em **SyncDeck > Status e conexão**, normalmente começando por:

- <code>192.168.</code>
- <code>10.</code>
- <code>172.16.</code> até <code>172.31.</code>

Não use IP público, endereço de site ou IP de VPN.

## PC indisponível

1. Confirme a mesma rede Wi-Fi.
2. Confirme que o agente está próximo ao relógio.
3. Verifique IP e porta.
4. Configure a rede do Windows como **Privada**.
5. Execute <code>Configurar-Firewall.bat</code> como administrador.
6. Evite rede de convidados, que costuma bloquear dispositivos entre si.

## O ícone não aparece

1. Atualize agente e APK para a mesma versão.
2. Abra o programa uma vez no Windows.
3. Toque em <code>↻</code>.
4. Confira Destino, Processos e Nome no Menu Iniciar.
5. Aguarde a primeira extração; depois o ícone fica em cache.

## O cartão não fica luminoso

- A versão 0.3.1 precisa estar instalada nas duas partes.
- O programa precisa possuir uma janela visível; processo em segundo plano não conta.
- Aguarde cerca de três segundos.
- Confira o nome do processo sem <code>.exe</code>.
- Para programas elevados, execute o agente com o nível necessário apenas se compreender o risco.

## Não consegue fechar

- Marque **Pode fechar** no botão.
- Confira o processo configurado.
- O SyncDeck envia <code>WM_CLOSE</code>, não força o processo.
- Aplicativos elevados podem recusar mensagens de um agente não elevado.
- O programa pode pedir confirmação para salvar.

## Chrome mostra seletor de contas

1. Atualize o agente para 0.3.1.
2. Abra o perfil desejado manualmente uma vez.
3. Feche a janela e abra pelo SyncDeck.
4. Confira se o botão usa <code>chrome.exe</code> e processo <code>chrome</code>.

Se o botão já possui <code>--profile-directory</code> em Argumentos, esse valor manual tem prioridade.

## APK não instala por cima

O Android exige a mesma assinatura. Isso acontece ao gerar o APK em outro computador/keystore. Opções:

- voltar a assinar com a chave original; ou
- desinstalar e instalar novamente, sabendo que o pareamento local será removido.

## Porta ocupada

Outra instância ou programa pode estar usando <code>47321</code>. Encerre instâncias antigas no Gerenciador de Tarefas. Se mudar a porta em configuração, ajuste também o Firewall e o Android.
