# Como contribuir

Obrigado por considerar uma contribuição ao SyncDeck.

## Antes de começar

1. Leia [README.md](README.md), [SECURITY.md](SECURITY.md) e [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
2. Procure uma issue existente antes de criar outra.
3. Para mudanças grandes ou de protocolo, abra primeiro uma proposta de funcionalidade.
4. Não inclua APKs, executáveis, keystores, tokens, arquivos de pareamento ou dados de `%LOCALAPPDATA%\SyncDeck`.

## Ambiente

- Windows 10 ou 11 com .NET Framework 4.8.
- Android Studio, JDK 17 e Android SDK 36.
- Python 3 para executar a validação do repositório.

Consulte [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) para a preparação completa.

## Fluxo sugerido

1. Crie uma branch a partir de `main`.
2. Faça mudanças pequenas e focadas.
3. Execute `python scripts/validate_repository.py`.
4. Compile o agente Windows com `windows-agent\build-agent.bat`.
5. Compile o Android com `android-app\gradlew.bat assembleDebug`.
6. Execute os testes manuais afetados descritos em [docs/TESTING.md](docs/TESTING.md).
7. Abra um Pull Request explicando motivação, solução, testes e impacto de segurança.

## Convenções

- Preserve compatibilidade com .NET Framework 4.8 e Android 8.0 ou superior.
- Não adicione dependências sem justificar necessidade, manutenção e risco.
- Mantenha mensagens exibidas ao usuário em português claro.
- Mudanças de API devem atualizar `docs/PROTOCOL.md` e os vetores de teste.
- Mudanças visuais devem incluir captura antes/depois no Pull Request.
- Corrija warnings novos quando forem consequência direta da mudança.

## Commits

Mensagens no formato Conventional Commits são recomendadas:

- `feat: adiciona controle de volume`
- `fix: corrige detecção do Outlook`
- `docs: detalha assinatura do APK`
- `test: amplia vetor de protocolo`

Ao contribuir, você concorda que seu código será disponibilizado sob a licença MIT do projeto.
