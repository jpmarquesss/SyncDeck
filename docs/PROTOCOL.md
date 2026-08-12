# Protocolo local

Este documento descreve o protocolo da versão 0.3.1. Mudanças incompatíveis devem atualizar a versão do agente, este documento e os vetores em <code>tests/</code>.

## Transporte

- HTTP/1.1 sobre TCP.
- Porta padrão: <code>47321</code>.
- Somente IPv4 privado, loopback ou link-local.
- Respostas usam <code>Connection: close</code> e <code>Cache-Control: no-store</code>.
- Corpo máximo de requisição: 65.536 bytes.
- Corpo máximo aceito pelo Android: 262.144 bytes.

## Rotas

| Método | Rota | Assinada | Finalidade |
|---|---|---:|---|
| GET | <code>/api/status</code> | Não | Nome, versão, horário e dados públicos de pareamento |
| POST | <code>/api/pair</code> | Não | Concluir pareamento com payload RSA |
| GET | <code>/api/actions</code> | Sim | Lista pública, ícones e estado inicial |
| GET | <code>/api/actions/state</code> | Sim | Estado aberto/fechado e quantidade de janelas |
| GET | <code>/api/actions/edit</code> | Sim | Configuração completa para o editor |
| GET | <code>/api/icons/{id}</code> | Sim | Ícone PNG autenticado |
| POST | <code>/api/execute</code> | Sim | Abrir, fechar uma ou fechar todas |
| POST | <code>/api/actions/save</code> | Sim | Criar ou atualizar ação |
| POST | <code>/api/actions/delete</code> | Sim | Excluir ação |

## Envelope JSON

Sucesso:

~~~json
{
  "Ok": true,
  "Data": {},
  "Message": "Concluído.",
  "Code": null
}
~~~

Erro:

~~~json
{
  "Ok": false,
  "Data": null,
  "Message": "Descrição segura do erro.",
  "Code": "error_code"
}
~~~

## Pareamento

1. O Windows cria/mantém uma chave RSA de 2048 bits.
2. O usuário solicita pareamento e recebe um código temporário de seis números.
3. <code>/api/status</code> publica módulo, expoente, fingerprint e expiração.
4. O Android calcula novamente a fingerprint e exige conferência visual.
5. O Android cria um segredo aleatório de 32 bytes.
6. Código, ClientId, DeviceName e Secret são serializados e cifrados com RSA OAEP SHA-1/MGF1 SHA-1 para compatibilidade com .NET Framework.
7. O agente valida código, expiração, tentativas e tamanho do segredo.
8. O Android guarda o segredo no Keystore; o Windows usa DPAPI CurrentUser.

O código aceita no máximo cinco tentativas e expira em cinco minutos.

## Requisições autenticadas

Cabeçalhos:

| Cabeçalho | Valor |
|---|---|
| <code>X-SyncDeck-Client</code> | UUID do cliente |
| <code>X-SyncDeck-Timestamp</code> | Unix time em segundos |
| <code>X-SyncDeck-Nonce</code> | 16 bytes aleatórios em Base64URL |
| <code>X-SyncDeck-Signature</code> | HMAC-SHA-256 em Base64URL |

String canônica:

~~~text
METHOD
/api/path
TIMESTAMP
NONCE
SHA256_HEX(BODY)
~~~

O método é maiúsculo e a rota não inclui query string. A comparação de assinatura é feita em tempo constante. O servidor aceita diferença máxima de 90 segundos e rejeita nonces repetidos.

## Respostas autenticadas

Cabeçalho:

<code>X-SyncDeck-Response-Signature</code>

String canônica:

~~~text
RESPONSE
STATUS_CODE
REQUEST_NONCE
SHA256_HEX(RESPONSE_BODY)
~~~

O Android valida a resposta antes de interpretar JSON ou imagem.

## Executar ação

~~~json
{
  "ActionId": "chrome",
  "Operation": "open",
  "Confirmed": false
}
~~~

Operações:

- <code>open</code>: focar ou iniciar.
- <code>close</code>: fechar uma janela.
- <code>close-all</code>: fechar todas as janelas correspondentes.

Operações de fechamento exigem <code>Confirmed: true</code>. Ações do tipo <code>command</code> também exigem confirmação.

## Estado de janelas

~~~json
[
  {
    "Id": "chrome",
    "IsOpen": true,
    "WindowCount": 2
  }
]
~~~

Nenhum título, texto da janela ou linha de comando é enviado.

## Compatibilidade

- Clientes devem ignorar propriedades JSON desconhecidas.
- O servidor valida operações por lista permitida.
- Novas propriedades opcionais podem ser adicionadas sem mudar a versão principal.
- Alterações na string canônica, criptografia ou semântica das rotas exigem coordenação entre agente e APK.

## Vetor de teste

<code>tests/protocol-vector.json</code> contém um segredo determinístico exclusivamente para testes. Ele não é uma credencial real. Execute:

~~~powershell
python scripts/validate_repository.py
~~~
