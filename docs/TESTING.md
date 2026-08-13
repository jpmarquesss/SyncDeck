# Testes

## Validação rápida

Na raiz do repositório:

```powershell
python scripts/validate_repository.py
```

O validador confere estrutura, versões, links, XML/JSON, arte da loja, permissões Android, configuração Kotlin/Compose, invariantes do protocolo 2, aprovação no desktop, DPAPI/Keystore, CI e ausência de artefatos ou segredos publicáveis.

O vetor completo de AES/HMAC usa a implementação Java da JDK:

```powershell
mkdir build\protocol-test
javac tests\ProtocolVectorTest.java -d build\protocol-test
java -cp build\protocol-test ProtocolVectorTest
```

## Compilação

### Android

```powershell
cd android-app
.\gradlew.bat --no-daemon testDebugUnitTest lintRelease assembleDebug bundleRelease
```

Além de compilar Kotlin/Compose, o `lintRelease` precisa terminar sem erro bloqueante. Instale o APK em pelo menos um aparelho físico; um build bem-sucedido não valida rede local, rotação ou Wake-on-LAN.

### Windows

```powershell
windows-agent\build-agent.bat
windows-agent\Instalar-no-Windows.bat
```

Teste no perfil de rede **Privada** e confira que somente uma instância aparece no Gerenciador de Tarefas.

### iOS experimental

O job `ios` valida HMAC em Swift, compila para iPhone sem assinatura e empacota `SyncDeck-unsigned.ipa`. A instalação precisa de assinatura Apple separada.

## Matriz funcional manual

| Área | Cenário | Resultado esperado |
|---|---|---|
| Primeiro uso | Sem endpoint | Assistente de conexão é exibido |
| Endereço | IP público, domínio ou porta inválida | Recusado antes da conexão |
| Pareamento | Fingerprint não confirmado | Código não pode ser enviado |
| Pareamento | Código errado seis vezes | O sexto não ocorre; após cinco falhas é preciso gerar outro código |
| Persistência | Reiniciar celular e Windows | Pareamento e botões continuam válidos |
| Mudança de IP | DHCP troca o IP do PC | Mesmo agente é reencontrado pelo fingerprint |
| Programa | Escolher no catálogo | Botão salva sem digitar caminho e ganha ícone real |
| Pasta/arquivo | Abrir seletor | Janela aparece no PC e retorna o caminho escolhido |
| Site | URL sem esquema | Interface orienta a usar `https://` |
| Comando | Salvar | PC mostra tipo, destino, argumentos e diretório; negar não salva |
| Comando | Executar | Confirmação Android e nova aprovação no PC são obrigatórias |
| Alteração | Trocar argumento/diretório/fallback | Nova aprovação aparece no PC |
| Janela | Programa já aberto | Janela é restaurada e trazida à frente |
| Estado | Uma ou mais janelas | Contorno acende e quantidade é atualizada |
| Fechar | Várias janelas | Android pergunta uma ou todas; agente envia `WM_CLOSE` |
| Chrome | Mais de um perfil | Usa a janela ativa ou último perfil conhecido sem seletor |
| Rotação | Paisagem | Três colunas, logos apenas, barras do sistema reduzidas |
| Wake-on-LAN | PC em S5 | Botão local envia Magic Packet e tenta reconectar após 15 s |
| Revogação | Revogar todos no agente | Requisições antigas passam a receber não autorizado |

## Matriz de segurança

| Teste | Resultado esperado |
|---|---|
| Alterar um byte do ciphertext | HMAC inválido; nada é descriptografado ou executado |
| Repetir mesmo nonce | Segunda requisição recusada |
| Relógio ±91 segundos | Requisição recusada com orientação de sincronização |
| Enviar mais de 120 req/min | Cliente limitado temporariamente |
| Abrir mais de 16 conexões | Excesso recebe 429 |
| Tentar fora de IPv4 privado | Cliente e servidor recusam |
| Token usado duas vezes/outro celular | Recusado; salvar pede aprovação no PC |
| Token de programa com argumentos extras | Deixa de ser confiável e pede aprovação |
| Pressionar Enter no diálogo do PC | **Negar** é a ação padrão |
| Não responder por 45 s | Pedido é negado automaticamente |
| Resposta sem assinatura/cifra v2 | Android não interpreta o corpo |

## Compatibilidade Android

Antes de produção, teste quando possível:

- Android 8/API 26, que é o mínimo;
- Android 11, aparelho físico atual do projeto;
- uma versão intermediária recente;
- Android 16/API 36;
- telas pequenas, fonte ampliada e tema escuro do sistema;
- instalação via teste da Play Store, não apenas via ADB.

## GitHub Actions

O workflow `Validate and build` executa validação independente, teste do protocolo, Android test/lint/APK/AAB, agente Windows e iOS experimental. Todos os jobs precisam ficar verdes antes de criar tag ou enviar um AAB.
