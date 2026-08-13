# Política de Privacidade do SyncDeck

**Última atualização:** 12 de agosto de 2026

O SyncDeck transforma um dispositivo Android em um painel de controle para um computador Windows do próprio usuário. O aplicativo foi projetado para funcionar diretamente na rede local, sem conta, anúncios, rastreamento ou servidor do desenvolvedor.

## Resumo

- O desenvolvedor não coleta, recebe, vende ou compartilha dados pessoais pelo SyncDeck.
- O aplicativo não possui SDK de publicidade, análise, perfilamento ou crash reporting.
- A comunicação ocorre entre o celular e o PC escolhido pelo usuário na mesma rede privada.
- As configurações permanecem nos dispositivos do usuário.

## Dados processados localmente

Para fornecer a funcionalidade, o SyncDeck pode processar e transmitir entre o celular e o PC:

- endereço IP privado e porta do PC;
- nome do PC e modelo/nome do celular;
- identificador aleatório do dispositivo pareado;
- chave pública, impressão digital e segredo criptográfico de pareamento;
- nomes, tipos e configurações dos botões criados pelo usuário;
- estado aberto/fechado e quantidade de janelas correspondentes;
- imagens dos aplicativos extraídas localmente pelo Windows;
- endereço MAC, broadcast e porta necessários ao Wake-on-LAN.

Essas informações não são enviadas ao desenvolvedor. O endereço MAC é usado apenas para montar um Magic Packet na rede local. Títulos e conteúdo das janelas não são enviados ao celular.

## Permissão Android

O aplicativo solicita somente `INTERNET`. Essa permissão é necessária para abrir a conexão TCP com um endereço privado e enviar Wake-on-LAN no roteador local. O SyncDeck não usa essa permissão para se comunicar com um serviço do desenvolvedor.

O SyncDeck não solicita contatos, SMS, telefone, fotos, arquivos do celular, câmera, microfone, localização, acessibilidade, notificações ou dados de aplicativos bancários.

## Armazenamento e retenção

No Android, endereço, identificador do cliente, impressão digital e configuração Wake-on-LAN ficam nas preferências privadas do aplicativo. O segredo de pareamento é cifrado por uma chave não exportável do Android Keystore. Imagens ficam no cache do app.

No Windows, botões e configurações ficam em `%LOCALAPPDATA%\SyncDeck`. Segredos pareados são protegidos pelo DPAPI no perfil do usuário.

Os dados permanecem até o usuário desparear, limpar os dados/desinstalar o aplicativo, revogar os celulares no agente ou excluir manualmente a configuração do Windows.

## Compartilhamento e terceiros

O SyncDeck não compartilha dados com o desenvolvedor, anunciantes, corretores de dados ou plataformas de análise. O usuário pode criar um botão que peça ao Windows para abrir um site de terceiros; nesse caso, o navegador do PC passa a se relacionar diretamente com esse site segundo a política dele. O aplicativo Android não carrega o conteúdo do site.

## Segurança

O pareamento exige código temporário e comparação visual da impressão digital. O Android 1.0 cifra o conteúdo autenticado com AES-256 e autentica requisições e respostas com HMAC-SHA-256. Segredos ficam no Android Keystore e no Windows DPAPI. Comandos sensíveis exigem confirmação também no PC.

Nenhum sistema é absolutamente seguro. Use o SyncDeck somente em uma rede privada confiável, mantenha Android e Windows atualizados e não exponha a porta do agente à internet.

## Crianças

O SyncDeck é uma ferramenta de produtividade geral e não é direcionado a crianças. Como não existe conta, anúncio ou coleta pelo desenvolvedor, o serviço não cria deliberadamente perfis de menores.

## Controle do usuário

O usuário pode consultar os botões no app/agente, editá-los ou excluí-los a qualquer momento. Para apagar os dados:

1. use **Desparear este celular** no Android;
2. use **Revogar celulares pareados** no agente Windows;
3. limpe os dados ou desinstale o app;
4. se desejar remover também botões e ajustes do PC, exclua `%LOCALAPPDATA%\SyncDeck` após fechar o agente.

Como os dados não chegam ao desenvolvedor, não existe banco de dados remoto para exportar ou apagar.

## Alterações

Mudanças relevantes serão registradas neste documento e no changelog do projeto. A data acima será atualizada antes de uma nova versão entrar em vigor.

## Contato

Antes de publicar esta política, substitua `SEU_EMAIL_DE_SUPORTE` pelo mesmo endereço verificado que será exibido na Google Play.

**E-mail de privacidade e suporte:** `SEU_EMAIL_DE_SUPORTE`

Para vulnerabilidades, use o canal privado **Report a vulnerability** na aba Security do repositório oficial, conforme [SECURITY.md](SECURITY.md).
