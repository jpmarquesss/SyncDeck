# Testes

## Validação automática

Execute na raiz:

~~~powershell
python scripts/validate_repository.py
~~~

O script verifica:

- JSON e XML válidos.
- Consistência das versões Android/Windows.
- Permissões Android permitidas.
- Vetores SHA-256/HMAC de requisição e resposta.
- Presença dos arquivos essenciais do repositório.
- Ausência de APKs, executáveis, keystores e configurações locais.
- Invariantes de segurança do protocolo e da inicialização Android.

## Compilação

### Windows

~~~powershell
windows-agent\build-agent.bat
~~~

### Android

~~~powershell
cd android-app
.\gradlew.bat assembleDebug
~~~

O GitHub Actions executa os dois builds em sistemas operacionais nativos.

## Matriz manual

| Área | Cenário | Resultado esperado |
|---|---|---|
| Primeiro uso | Agente sem clientes | Janela de pareamento é aberta |
| Rede | IP público ou hostname | Android rejeita o endpoint |
| Pareamento | Fingerprint diferente | Android bloqueia o pareamento |
| Pareamento | Código errado repetido | Limite de tentativas é aplicado |
| Persistência | Reiniciar o Windows | Cliente continua autenticado sem novo pareamento |
| Rede | IP do PC muda na mesma sub-rede | Android encontra a mesma impressão digital e salva o novo IP |
| Abertura | Aplicativo fechado | Programa inicia |
| Abertura | Aplicativo aberto/minimizado | Janela é restaurada e focada |
| Estado | Janela abre/fecha | Contorno muda em até alguns segundos |
| Múltiplas | Duas janelas abertas | Android oferece fechar uma ou todas |
| Fechamento | Documento não salvo | Aplicativo pode exibir seu aviso normal |
| Chrome | Janela existente | A mesma janela é trazida para frente |
| Chrome | Nenhuma janela | Último perfil abre sem seletor |
| ChatGPT | Tocar no botão web | <code>chatgpt.com</code> abre no perfil atual/último do Chrome |
| Desligamento | Tocar em Desligar PC | Android exige confirmação explícita antes de enviar |
| Explorer | Nenhuma pasta aberta | Cartão permanece fechado |
| Explorer | Duas pastas abertas | Contagem e fechamento múltiplo funcionam |
| Ícones | Primeiro carregamento | Ícone é obtido do Windows e armazenado em cache |
| Rotação | Retrato para paisagem | Três colunas, apenas logos e tela cheia |
| Android 11 | Abrir app | Não ocorre falha de WindowInsetsController |
| Segurança | Resposta adulterada | Android recusa a resposta |
| Replay | Repetir nonce | Agente responde não autorizado |

## Logcat

Para falhas Android:

1. Conecte o aparelho por USB.
2. Abra **Logcat** no Android Studio.
3. Filtre por <code>package:com.syncdeck.app</code> ou <code>FATAL EXCEPTION</code>.
4. Remova IPs, identificadores e dados pessoais antes de anexar a uma issue.

## Diagnóstico Windows

O agente é compilado como aplicação de janela e não abre console. Para investigar:

- reproduza pelo Android e registre mensagem exibida;
- confirme o processo no Gerenciador de Tarefas;
- confirme endereço/porta em **Status e conexão**;
- execute <code>build-agent.bat</code> em um Prompt para visualizar erros de compilação.

Nunca publique <code>clients.json</code>.
