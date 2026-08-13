# Segurança do SyncDeck

## Relatar uma vulnerabilidade

Não publique vulnerabilidades, chaves, IPs ou provas de conceito exploráveis em uma issue. Use **Security > Advisories > Report a vulnerability** no repositório GitHub. Informe versão, impacto, pré-condições e passos mínimos de reprodução. Não teste computadores ou redes sem autorização.

## Versões suportadas

| Versão | Correções de segurança |
|---|---:|
| 1.0.x | Sim |
| 0.5.x | Somente compatibilidade crítica |
| 0.4.x e anteriores | Não |

Atualize o Android e o agente Windows juntos. O Android 1.0 exige o protocolo 2 do agente.

## Modelo de ameaça

O SyncDeck considera a rede local potencialmente observável e aceita que um invasor possa capturar, alterar, repetir ou enviar pacotes. O objetivo é impedir que alguém sem o segredo pareado execute ações ou leia o conteúdo autenticado do Android.

Não fazem parte do modelo:

- um Windows ou Android já comprometido por malware com acesso ao usuário;
- alguém com acesso físico e sessão desbloqueada;
- segurança do Magic Packet Wake-on-LAN, que não possui autenticação por definição;
- controle pela internet, encaminhamento de porta ou redes públicas.

## Pareamento

1. O agente mantém uma chave RSA de 2048 bits no perfil do Windows.
2. O usuário gera um código aleatório de seis dígitos válido por cinco minutos e cinco tentativas.
3. O Android recalcula a impressão digital da chave e exige comparação visual com o PC.
4. O Android cria um segredo aleatório de 32 bytes e envia código, UUID e segredo em RSA-OAEP.
5. O segredo fica no Android Keystore, cifrado com AES-GCM, e no Windows DPAPI `CurrentUser`.

A chave RSA usa OAEP SHA-1 por compatibilidade com o provedor RSA do .NET Framework. Ela protege somente o payload curto de pareamento; HMAC-SHA-256 e AES-256 protegem a sessão posterior.

## Protocolo Android v2

- O corpo autenticado é cifrado com AES-256-CBC e IV aleatório de 128 bits.
- A chave de cifração é `HMAC-SHA-256(segredo, "SyncDeck.Encryption.v1")`.
- A assinatura HMAC cobre método, rota, horário, nonce e SHA-256 do conjunto `IV || texto_cifrado`.
- O agente verifica a assinatura antes de descriptografar: esquema cifrar-e-autenticar.
- A resposta é cifrada e assinada do mesmo modo, usando o nonce da requisição.
- Comparações de código e assinatura evitam atalhos dependentes do conteúdo.
- O relógio aceita no máximo 90 segundos de diferença e nonces não podem ser repetidos.
- Cada dispositivo possui limite de 120 requisições autenticadas por minuto; o servidor aceita no máximo 16 conexões simultâneas.

O canal usa HTTP local, portanto cabeçalhos e tamanhos ainda são visíveis. Conteúdo de ações, caminhos, respostas e ícones do cliente Android 1.0 é cifrado. O status público revela apenas o necessário para encontrar e parear o agente, como nome do PC, versão e chave pública.

O cliente iOS experimental 0.5 mantém o protocolo assinado legado por compatibilidade e não deve ser tratado como distribuição pública 1.0.

## Aprovação no PC

Comandos e atalhos de teclado exigem confirmação no Android e uma segunda confirmação no desktop a cada execução. Cadastrar ou alterar destino, argumentos, diretório de trabalho ou fallback sensível também exige aprovação no PC.

A janela mostra:

- nome e IP do celular pareado;
- nome e tipo da ação;
- destino completo;
- argumentos, diretório de trabalho e link alternativo, quando existirem.

Os dados são exibidos em campo rolável, caracteres de controle são recusados e Enter seleciona **Negar**. A solicitação expira em 45 segundos e somente uma confirmação sensível pode ficar aberta por vez.

Programas escolhidos no catálogo e arquivos/pastas escolhidos no seletor do próprio Windows recebem um token de uso único, vinculado ao dispositivo, tipo e destino por cinco minutos. Um token não autoriza comandos nem argumentos adicionais.

## Execução

- O agente executa apenas ações armazenadas no PC.
- Comandos iniciam diretamente o executável configurado com `UseShellExecute=false`; texto do telefone não é concatenado a `cmd.exe` ou PowerShell.
- Links aceitam apenas HTTP/HTTPS.
- Caminhos precisam existir ou usar um destino `shell:` conhecido.
- Fechamento envia `WM_CLOSE`; não existe `taskkill` ou encerramento forçado pela API.
- Limites de tamanho, tipo, quantidade e caracteres são aplicados novamente no agente.
- Títulos das janelas são usados apenas localmente e nunca enviados ao celular.

## Rede e permissões

- O Android declara somente `INTERNET`. Para `targetSdk 36`, o Android concede implicitamente acesso à LAN com essa permissão.
- O cliente aceita apenas IPv4 privado/link-local e portas de 1024 a 65535.
- O servidor também rejeita endereços fora da LAN.
- A regra recomendada do Firewall permite TCP/47321 somente no perfil privado, `remoteip=localsubnet` e para o executável instalado.
- O SyncDeck não cria encaminhamento de porta, UPnP, túnel ou conta em nuvem.

Não use Wi-Fi público, de hotel, convidados ou rede com dispositivos desconhecidos. O app não foi projetado para receber a porta 47321 pela internet.

## Dados locais

O aplicativo não inclui SDK de anúncio, análise ou crash reporting e não envia dados ao desenvolvedor. Consulte [PRIVACY-POLICY.md](PRIVACY-POLICY.md).

| Local | Dado |
|---|---|
| Android Keystore/SharedPreferences | segredo cifrado, UUID, IP privado, impressão digital e Wake-on-LAN |
| Cache Android | PNGs obtidos do PC |
| `%LOCALAPPDATA%\SyncDeck` | botões, clientes DPAPI, backup e porta |
| Registro HKCU | inicialização automática do agente |

Revogue todos os celulares pelo menu do agente ou despareie apenas o Android na tela de conexão. Apagar os dados do app remove a chave daquele aparelho.

## Wake-on-LAN

O MAC, broadcast e porta são entregues somente em resposta autenticada e ficam no celular. O Magic Packet repete o MAC em broadcast e não possui autenticação. Qualquer equipamento da mesma LAN que conheça esse MAC pode tentar acordar o PC; isso não concede login, PIN, senha ou chave do BitLocker.

## Assinatura de distribuição

- Distribuição Android: guarde a chave de assinatura fora do Git e limite o acesso ao proprietário.
- Windows: o código-fonte compila um executável funcional, mas uma distribuição pública deve assinar o binário com Authenticode.
- Nunca publique `keystore.properties`, `.jks`, `.pfx`, clientes pareados ou arquivos de `%LOCALAPPDATA%\SyncDeck`.
