# Publicar o SyncDeck na Google Play

Este roteiro parte de uma publicação pública para qualquer pessoa. O app Android está configurado como `com.syncdeck.app`, versão `1.0.0`, `versionCode 10`, `targetSdk 36` e formato Android App Bundle.

## 1. Decisões permanentes

Antes do primeiro upload:

1. confirme que **SyncDeck** é o nome desejado;
2. confirme que `com.syncdeck.app` está disponível e será o package name permanente;
3. escolha uma conta Google que continuará sob seu controle;
4. defina um e-mail público de suporte;
5. publique `PRIVACY-POLICY.md` em uma URL pública, por exemplo com GitHub Pages.
6. substitua `SEU_EMAIL_DE_SUPORTE` dentro da política antes de publicar a URL.

O package name não pode ser reutilizado ou alterado depois que o app é criado no Console. Se precisar trocá-lo, altere `applicationId` e `namespace` antes do primeiro AAB.

Referência: [criar e configurar um app](https://support.google.com/googleplay/android-developer/answer/9859152).

## 2. Conta de desenvolvedor

1. Crie a conta no [Google Play Console](https://play.google.com/console/).
2. Escolha **Pessoal** ou **Organização** conforme a situação real; não crie empresa fictícia.
3. Conclua identidade, endereço, telefone, e-mail e verificação do dispositivo solicitados.
4. Pague a taxa única de cadastro exibida pelo Google; a documentação atual informa US$ 25.

Contas pessoais criadas depois de 13 de novembro de 2023 precisam, atualmente, de teste fechado com pelo menos 12 testadores inscritos continuamente por 14 dias antes de solicitar acesso à produção. O Console exige também respostas sobre uso, feedback e prontidão. Veja [requisitos oficiais de teste](https://support.google.com/googleplay/android-developer/answer/14151465).

## 3. Criar a chave de upload

No terminal do Android Studio ou Prompt de Comando com JDK 17:

```powershell
cd android-app
keytool -genkeypair -v -keystore syncdeck-upload.jks -alias syncdeck-upload -keyalg RSA -keysize 4096 -validity 10000
```

Crie `android-app/keystore.properties` a partir de `keystore.properties.example`:

```properties
storeFile=syncdeck-upload.jks
storePassword=SENHA_FORTE_DA_CHAVE
keyAlias=syncdeck-upload
keyPassword=SENHA_FORTE_DA_CHAVE
```

Cuidados:

- `*.jks` e `keystore.properties` já estão ignorados pelo Git;
- mantenha duas cópias cifradas da chave em locais diferentes;
- guarde as senhas em um gerenciador de senhas;
- nunca envie a chave por issue, chat, release ou workflow público;
- ative **Play App Signing**. A chave de upload poderá ser redefinida pelo processo do Google; a chave de assinatura do app é a identidade instalada pelo Play.

## 4. Gerar o AAB

Execute:

```powershell
cd android-app
Gerar-AAB.bat
```

Ou:

```powershell
.\gradlew.bat clean testDebugUnitTest lintRelease bundleRelease
```

Saída:

```text
android-app/app/build/outputs/bundle/release/app-release.aab
```

Antes de enviar, execute também:

```powershell
python scripts/validate_repository.py
```

O `versionCode` deve aumentar em toda atualização: 11, 12, 13 e assim por diante. Não reutilize um código já enviado ao Play Console.

## 5. Criar o app no Console

Use estas opções iniciais:

| Campo | Valor sugerido |
|---|---|
| Idioma padrão | Português (Brasil) |
| Nome | SyncDeck |
| Tipo | Aplicativo |
| Gratuito ou pago | Gratuito |
| Categoria | Produtividade |
| Contém anúncios | Não |

Aceite os termos do Play App Signing e informe um e-mail de contato que você realmente acompanhe.

## 6. Preencher conteúdo e políticas

Use [STORE-LISTING.md](STORE-LISTING.md) e [DATA-SAFETY.md](DATA-SAFETY.md) como base, conferindo cada resposta no Console.

Checklist:

- política de privacidade com URL pública;
- acesso ao app: nenhuma conta é exigida;
- anúncios: não;
- público-alvo: adultos e público geral, não direcionado especificamente a crianças;
- classificação de conteúdo: utilitário/produtividade, sem conteúdo violento ou social;
- Data Safety: sem coleta/compartilhamento pelo desenvolvedor, após confirmar que nenhuma dependência nova adicionou telemetria;
- permissões: somente `INTERNET`, sem formulário de permissão sensível;
- instruções ao revisor explicando que o app precisa do agente Windows e da mesma LAN.

Texto sugerido ao revisor:

> O SyncDeck é um controle local para um PC Windows do próprio usuário. Não exige conta e não usa backend. Para testar, execute o SyncDeck Agent no Windows, mantenha PC e Android na mesma rede privada, gere “Parear celular” no ícone da bandeja e informe IP, porta e código temporário no app. Comandos avançados exigem aprovação também no desktop.

Disponibilize ao revisor um link direto para uma release do agente Windows, sem exigir acesso privado ao GitHub.

## 7. Materiais gráficos

Prepare:

- ícone 512 × 512 PNG, sem transparência;
- pelo menos duas capturas reais do telefone; recomendado retrato 1080 × 1920 ou superior;
- captura paisagem mostrando somente logos;
- captura do novo assistente de botão;
- imagem de destaque 1024 × 500, caso o Console solicite.

Não mostre IP real, MAC, código de pareamento, e-mail, nome de banco, notificações ou dados pessoais. Use um PC/roteador de teste e dados fictícios.

## 8. Testes antes da produção

1. Publique primeiro em **Teste interno**.
2. Instale pelo link do Play, não apenas pelo Android Studio.
3. Teste Android 8/11/13/16 quando possível, retrato e paisagem.
4. Teste pareamento correto, código errado, revogação e mudança de IP.
5. Teste programa, site, pasta, arquivo, comando negado/aprovado e múltiplas janelas.
6. Teste PC desligado, Wake-on-LAN e retorno do agente após login.
7. Confira o relatório de pré-lançamento, Android Vitals e avisos de política.
8. Para conta pessoal nova, conduza o teste fechado exigido e registre feedback real.

A Play Store não instala o agente Windows. A listagem e a primeira tela precisam deixar isso evidente para evitar avaliações negativas.

## 9. Produção

1. Corrija todos os erros e avisos relevantes do Console.
2. Solicite acesso à produção após o teste obrigatório, se aplicável.
3. Envie a versão inicialmente com rollout controlado quando essa opção estiver disponível.
4. Acompanhe crashes, ANRs, avaliações e suporte.
5. Para cada atualização, aumente `versionCode`, atualize `versionName`, `VERSION` e `CHANGELOG.md`.

Em 31 de agosto de 2026, novas versões precisam mirar API 36 segundo os [requisitos de target API do Google Play](https://support.google.com/googleplay/android-developer/answer/11926878). O SyncDeck já usa `targetSdk 36`.

## 10. Observação sobre rede local

Para aplicativos com `targetSdk 36` ou inferior, o acesso à LAN continua implícito na permissão `INTERNET`; a própria documentação orienta não declarar `ACCESS_LOCAL_NETWORK`. Essa permissão passa a ser obrigatória para apps que miram Android 17/API 37. Consulte [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission) antes de elevar o target no futuro.

## 11. Migração do APK atual

O APK criado pelo Android Studio usa normalmente a chave de depuração. A versão distribuída pelo Play será assinada pela chave do Play App Signing e não poderá atualizar por cima da instalação debug. Para sua própria migração:

1. confirme que o agente Windows mantém o pareamento atual;
2. desinstale o APK debug;
3. instale pelo teste interno/fechado da Play Store;
4. pareie novamente uma vez.

Usuários que já começarem pela Play Store receberão atualizações futuras normalmente.
