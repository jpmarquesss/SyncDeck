# Modelo de ações

As ações são armazenadas no Windows e identificadas por um ID. O Android recebe somente os campos necessários para exibir e executar o botão; a configuração completa é enviada apenas quando o editor é aberto.

## Campos

| Propriedade | Tipo | Regra |
|---|---|---|
| <code>Id</code> | string | 2 a 64 caracteres, letras minúsculas, números e hífen |
| <code>Label</code> | string | Nome visível, até 40 caracteres |
| <code>Type</code> | string | <code>app</code>, <code>url</code>, <code>path</code>, <code>command</code> ou <code>hotkey</code> |
| <code>Target</code> | string | Destino principal, até 1.000 caracteres |
| <code>Arguments</code> | string | Argumentos separados do executável |
| <code>WorkingDirectory</code> | string | Diretório opcional de trabalho |
| <code>ProcessNames</code> | array | Processos usados para localizar/focar/fechar, sem necessidade de <code>.exe</code> |
| <code>AppNames</code> | array | Nomes pesquisados pelo PowerShell em Get-StartApps |
| <code>FallbackUrl</code> | string | Link alternativo para ações de aplicativo |
| <code>Icon</code> | string | Glifo alternativo caso a extração falhe |
| <code>Color</code> | string | Cor hexadecimal <code>#RRGGBB</code> |
| <code>Confirm</code> | boolean | Exigir confirmação antes de abrir/executar |
| <code>Closable</code> | boolean | Permitir fechamento pelo painel |
| <code>Enabled</code> | boolean | Exibir e permitir a ação |

Arrays são normalizados, removem duplicatas e aceitam no máximo 12 valores.

## Aplicativo

~~~json
{
  "Id": "android-studio",
  "Label": "Android Studio",
  "Type": "app",
  "Target": "C:\\Program Files\\Android\\Android Studio\\bin\\studio64.exe",
  "Arguments": "",
  "WorkingDirectory": "",
  "ProcessNames": ["studio64"],
  "AppNames": ["Android Studio"],
  "FallbackUrl": "",
  "Icon": "app",
  "Color": "#3DDC84",
  "Confirm": false,
  "Closable": true,
  "Enabled": true
}
~~~

Ao abrir, o agente procura primeiro uma janela correspondente. Se não houver, tenta o destino, depois um item do Menu Iniciar e, por último, o link alternativo.

## Site

~~~json
{
  "Id": "intranet",
  "Label": "Intranet",
  "Type": "url",
  "Target": "https://intranet.example.com",
  "ProcessNames": [],
  "AppNames": [],
  "Icon": "globe",
  "Color": "#2563EB",
  "Confirm": false,
  "Closable": false,
  "Enabled": true
}
~~~

Somente HTTP e HTTPS são aceitos.

## Pasta ou arquivo

~~~json
{
  "Id": "downloads",
  "Label": "Downloads",
  "Type": "path",
  "Target": "%USERPROFILE%\\Downloads",
  "ProcessNames": [],
  "AppNames": [],
  "Icon": "download",
  "Color": "#8B5CF6",
  "Confirm": false,
  "Closable": false,
  "Enabled": true
}
~~~

Variáveis de ambiente são expandidas no Windows.

## Comando

~~~json
{
  "Id": "meu-comando",
  "Label": "Meu comando",
  "Type": "command",
  "Target": "programa.exe",
  "Arguments": "--opcao valor",
  "ProcessNames": ["programa"],
  "AppNames": [],
  "Icon": "terminal",
  "Color": "#64748B",
  "Confirm": true,
  "Closable": true,
  "Enabled": true
}
~~~

Comandos são sempre forçados para <code>Confirm: true</code>. O executável e os argumentos ficam em campos separados; o agente não concatena dados recebidos em um shell.

## Atalho

<code>hotkey</code> usa a sintaxe de <code>System.Windows.Forms.SendKeys</code>. Exemplo: <code>^+{ESC}</code>.

## Correspondência de janela

O agente combina:

1. nomes informados em <code>ProcessNames</code>;
2. nome do executável em <code>Target</code>;
3. processos de janelas filhas para aplicativos modernos;
4. pistas de título somente para hosts intermediários ou ações sem processo.

Processos de fundo não são suficientes; é necessária uma janela visível.

## Chrome

Para ações cujo processo é <code>chrome</code>, o agente acrescenta automaticamente <code>--profile-directory</code> quando não existe um valor manual. A prioridade é:

1. janela Chrome já aberta;
2. primeiro item em <code>last_active_profiles</code>;
3. <code>last_used</code>;
4. perfil <code>Default</code>, se existente.

Um argumento manual <code>--profile-directory</code> sempre tem prioridade.

## ChatGPT no Chrome

~~~json
{
  "Id": "chatgpt-web",
  "Label": "ChatGPT",
  "Type": "url",
  "Target": "https://chatgpt.com/",
  "Arguments": "chrome",
  "ProcessNames": [],
  "AppNames": [],
  "Icon": "codex",
  "Color": "#10A37F",
  "Confirm": false,
  "Closable": false,
  "Enabled": true
}
~~~

Para ações do tipo <code>url</code>, o argumento especial <code>chrome</code> abre o site explicitamente no Google Chrome. Cada toque abre a página em uma guia, usando o perfil ativo ou o último perfil utilizado. Sem esse argumento, o navegador padrão do Windows é usado.

## Desligar o PC

~~~json
{
  "Id": "shutdown-pc",
  "Label": "Desligar PC",
  "Type": "command",
  "Target": "shutdown.exe",
  "Arguments": "/s /t 5",
  "ProcessNames": [],
  "AppNames": [],
  "Icon": "power",
  "Color": "#EF4444",
  "Confirm": true,
  "Closable": false,
  "Enabled": true
}
~~~

O Android exibe um aviso específico antes de executar. O agente também recusa o comando se a confirmação autenticada não estiver presente.
