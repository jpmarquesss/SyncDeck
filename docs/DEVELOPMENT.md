# Desenvolvimento

## Preparação do Windows

1. Instale Git para Windows.
2. Ative .NET Framework 4.8 em **Recursos do Windows**.
3. Instale Android Studio.
4. Configure JDK 17.
5. No SDK Manager, instale Android SDK Platform 36, Build-Tools e Platform-Tools.
6. Instale Python 3 para validações locais.

## Clonar

~~~powershell
git clone https://github.com/SEU-USUARIO/SyncDeck.git
cd SyncDeck
python scripts/validate_repository.py
~~~

Substitua a URL pelo endereço real após publicar.

## Agente Windows

O agente usa arquivos C# diretamente e o compilador do .NET Framework:

~~~powershell
windows-agent\build-agent.bat
~~~

Saída: <code>windows-agent\SyncDeckAgent.exe</code>.

Para recompilar, encerrar uma instância antiga e iniciar:

~~~powershell
windows-agent\Compilar-e-Iniciar.bat
~~~

O script referencia apenas assemblies presentes no .NET Framework. Evite APIs exclusivas de .NET moderno.

## Android

Abra a pasta <code>android-app</code>, não a raiz inteira, no Android Studio.

Build pelo terminal:

~~~powershell
cd android-app
.\gradlew.bat assembleDebug
~~~

Saída padrão: <code>app\build\outputs\apk\debug\app-debug.apk</code>.

O script <code>Gerar-APK.bat</code> copia a saída para <code>android-app\SyncDeck.apk</code>.

## Build completo

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1
~~~

Esse script valida o repositório, compila o agente e gera o APK debug.

## Alterar ações padrão

As ações iniciais ficam em <code>windows-agent/src/Stores.cs</code>, no método <code>CreateDefaults</code>. Mudanças afetam apenas instalações sem <code>actions.json</code>. Para migrar configurações existentes, implemente uma migração explícita e não sobrescreva personalizações do usuário.

## Adicionar uma rota

1. Defina modelos em <code>Models.cs</code>.
2. Adicione a rota autenticada em <code>DeckServer.Route</code>.
3. Imponha limites e validação antes de executar efeitos.
4. Adicione o método correspondente em <code>ApiClient.java</code>.
5. Atualize <code>docs/PROTOCOL.md</code>.
6. Acrescente testes e casos manuais.

Rotas privadas devem ficar depois de <code>Authenticate</code>.

## Alterar o protocolo

- Preserve os nomes de propriedades existentes quando possível.
- Nunca aceite nomes de executáveis ou comandos arbitrários vindos de <code>/api/execute</code>; use somente <code>ActionId</code>.
- Atualize os vetores criptográficos caso a forma canônica mude.
- Considere compatibilidade entre uma versão nova do APK e um agente antigo.

## Versões

Atualize em conjunto:

- <code>android-app/app/build.gradle</code>: <code>versionCode</code> e <code>versionName</code>.
- <code>windows-agent/src/DeckServer.cs</code>: versão pública do agente.
- <code>windows-agent/app.manifest</code>: <code>assemblyIdentity</code>.
- <code>CHANGELOG.md</code>.

## Dados locais

Durante testes, o agente usa <code>%LOCALAPPDATA%\SyncDeck</code>. Faça backup antes de testar migrações. Nunca copie <code>clients.json</code> para o repositório.

Para testar primeiro uso, renomeie temporariamente a pasta local em vez de apagá-la.

## Estilo e revisão

- Use C# compatível com o compilador do .NET Framework 4.8.
- Use Java 17 e APIs Android respeitando <code>minSdk 26</code>.
- Não faça rede no thread principal do Android.
- Feche/disponha objetos Win32 e <code>Process</code>.
- Trate títulos de janela como dados locais potencialmente sensíveis.
- Prefira mudanças pequenas, validáveis e sem novas permissões.
