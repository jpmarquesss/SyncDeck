# Protocolo local

Este documento descreve o protocolo 2 do SyncDeck Android/agente Windows 1.0.1. Ele não é uma API para exposição na internet.

## Transporte e limites

- HTTP/1.1 sobre TCP, porta padrão `47321`.
- Somente IPv4 privado, loopback ou link-local.
- `Connection: close`, `Cache-Control: no-store` e `X-Content-Type-Options: nosniff`.
- Requisição máxima: 65.536 bytes, incluindo corpo cifrado.
- Resposta máxima aceita pelo Android: 524.288 bytes.
- Máximo de 16 conexões simultâneas.
- Máximo de 120 requisições autenticadas por cliente/minuto.

O HTTP é apenas a moldura local. O protocolo 2 cifra o conteúdo autenticado antes do transporte e autentica o texto cifrado.

## Rotas

| Método | Rota | Autenticada | Finalidade |
|---|---|---:|---|
| GET | `/api/status` | Não | Versão, horário, chave pública e disponibilidade de pareamento |
| POST | `/api/pair` | RSA no payload | Concluir pareamento |
| GET | `/api/wake-config` | Sim | Configuração Wake-on-LAN da interface usada |
| GET | `/api/catalog/apps` | Sim | Programas detectados e tokens temporários |
| POST | `/api/catalog/pick` | Sim | Abrir seletor de arquivo/pasta no PC |
| GET | `/api/actions` | Sim | Botões públicos, imagem e estado inicial |
| GET | `/api/actions/state` | Sim | Estado/quantidade de janelas |
| GET | `/api/actions/edit` | Sim | Definições completas para edição |
| GET | `/api/icons/{id}` | Sim | PNG do botão |
| POST | `/api/execute` | Sim | Abrir, fechar uma ou fechar todas |
| POST | `/api/actions/save` | Sim | Criar/atualizar botão |
| POST | `/api/actions/delete` | Sim | Excluir botão |

## Envelope JSON

```json
{
  "Ok": true,
  "Data": {},
  "Message": "Concluído.",
  "Code": null
}
```

Erros usam `Ok: false`, uma mensagem segura e um código estável como `unauthorized`, `invalid_action`, `desktop_approval_required` ou `rate_limited`.

## Pareamento

1. O Windows mantém uma chave RSA de 2048 bits no perfil do usuário.
2. **Parear celular** cria código aleatório de seis dígitos, expiração de cinco minutos e cinco tentativas.
3. `/api/status` publica módulo, expoente e impressão digital.
4. O Android recalcula a impressão digital SHA-256 e o usuário compara os grupos nas duas telas.
5. O Android gera `ClientId` UUID e segredo aleatório de 32 bytes.
6. Código, ID, nome do aparelho e segredo são cifrados com RSA-OAEP SHA-1/MGF1 SHA-1.
7. O agente valida tudo, invalida o código e protege o segredo com DPAPI `CurrentUser`.
8. O Android protege o segredo com uma chave AES-GCM não exportável do Android Keystore.

O outer body de `/api/pair` contém apenas o payload RSA em Base64. Código e segredo não trafegam em claro.

## Autenticação da requisição

Cabeçalhos:

| Cabeçalho | Valor |
|---|---|
| `X-SyncDeck-Client` | UUID pareado |
| `X-SyncDeck-Timestamp` | Unix time em segundos |
| `X-SyncDeck-Nonce` | 16 bytes aleatórios em Base64URL |
| `X-SyncDeck-Signature` | HMAC-SHA-256 em Base64URL |
| `X-SyncDeck-Encryption` | `aes-256-cbc-v1` no protocolo 2 |

String canônica:

```text
METHOD
/api/path
TIMESTAMP
NONCE
SHA256_HEX(WIRE_BODY)
```

`METHOD` é maiúsculo e a rota não inclui query string. `WIRE_BODY` é vazio para GET ou o corpo cifrado para POST. O agente verifica HMAC em tempo constante antes de descriptografar, aceita diferença máxima de 90 segundos e rejeita nonces repetidos.

## Cifragem do conteúdo

Derivação:

```text
ENCRYPTION_KEY = HMAC-SHA256(PAIRING_SECRET, UTF8("SyncDeck.Encryption.v1"))
```

Corpo:

```text
WIRE_BODY = RANDOM_IV_16 || AES_256_CBC_PKCS7(ENCRYPTION_KEY, IV, PLAINTEXT)
```

O HMAC da requisição cobre `WIRE_BODY`. Isso fornece confidencialidade e integridade usando cifrar-e-autenticar. O IV precisa ser novo em cada mensagem.

Para GET autenticado, o request body permanece vazio, mas o cabeçalho de protocolo solicita resposta cifrada.

## Resposta autenticada

Cabeçalhos:

- `X-SyncDeck-Response-Signature`
- `X-SyncDeck-Encryption: aes-256-cbc-v1`

String canônica:

```text
RESPONSE
STATUS_CODE
REQUEST_NONCE
SHA256_HEX(RESPONSE_WIRE_BODY)
```

O Android valida o HMAC antes de descriptografar e só então interpreta JSON/PNG. Respostas de erro autenticadas também são cifradas.

## Catálogo e tokens de seleção

`GET /api/catalog/apps` retorna até 100 itens:

```json
{
  "Name": "Google Chrome",
  "Target": "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  "ProcessNames": ["chrome"],
  "AppNames": ["Google Chrome"],
  "Icon": "app",
  "Color": "#64748B",
  "SelectionToken": "..."
}
```

`POST /api/catalog/pick` recebe `{"Kind":"file"}` ou `{"Kind":"folder"}` e abre uma janela nativa no desktop.

Tokens:

- expiram em cinco minutos;
- são removidos no primeiro uso;
- pertencem ao `ClientId` que solicitou;
- vinculam tipo e destino exatos;
- deixam de ser confiáveis se houver argumentos, diretório ou fallback;
- nunca autorizam `command`/`hotkey`.

## Salvar ação

```json
{
  "Action": {
    "Id": "chrome",
    "Label": "Chrome",
    "Type": "app",
    "Target": "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "Arguments": "",
    "WorkingDirectory": "",
    "ProcessNames": ["chrome"],
    "AppNames": ["Google Chrome"],
    "FallbackUrl": "",
    "Icon": "app",
    "Color": "#4285F4",
    "Confirm": false,
    "Closable": true,
    "Enabled": true
  },
  "SelectionToken": "..."
}
```

Novo destino não-URL sem token confiável pede aprovação no PC. Comando/atalho e qualquer mudança de destino, argumentos, diretório ou fallback sensível sempre pedem aprovação. Alterar apenas nome, cor ou pistas de detecção não muda a execução.

## Executar ação

```json
{
  "ActionId": "chrome",
  "Operation": "open",
  "Confirmed": false
}
```

Operações:

- `open`: focar uma janela existente ou iniciar;
- `close`: enviar `WM_CLOSE` a uma janela;
- `close-all`: enviar `WM_CLOSE` a todas as correspondentes.

Fechamento exige confirmação no Android. `command` e `hotkey` exigem confirmação no Android e uma decisão independente no desktop com expiração de 45 segundos.

## Estado de janelas

```json
[
  { "Id": "chrome", "IsOpen": true, "WindowCount": 2 }
]
```

O agente captura janelas visíveis e envia somente ID, booleano e quantidade. Título, texto e conteúdo da janela não deixam o PC.

## Wake-on-LAN

`GET /api/wake-config` fornece MAC, broadcast, UDP/9 e interface correspondente ao endereço local da conexão TCP. O Android valida e armazena a resposta. Quando o PC está offline, ele monta `FF` seis vezes seguido por 16 repetições do MAC e envia para broadcasts locais.

Magic Packet não possui autenticação por definição e não passa pelo HTTP/HMAC.

## Compatibilidade

- O agente 1.0 reconhece o cabeçalho v2 e cifra a resposta quando solicitado.
- O cliente Android 1.0 rejeita agente sem protocolo 2.
- O agente ainda aceita o transporte HMAC sem conteúdo cifrado do cliente iOS experimental 0.5.0; essa compatibilidade é legada.
- Propriedades JSON desconhecidas devem ser ignoradas.
- Mudança em strings canônicas, derivação, cifra ou semântica de rota exige nova versão de protocolo.

## Vetores

`tests/protocol-vector.json` contém segredo, IVs, ciphertexts, hashes e HMACs determinísticos exclusivamente para teste. `ProtocolVectorTest.java` valida AES e HMAC sem depender do Android.
