# Releases

## Artefatos

| Artefato | Uso | Assinatura |
|---|---|---|
| `app-debug.apk` | Teste local | Chave debug; nunca produção |
| `SyncDeck-Android.apk` | Download Android oficial | Assinatura de distribuição |
| `SyncDeckAgent.exe` | Agente Windows | Authenticode recomendado antes de distribuir |
| `SyncDeck-unsigned.ipa` | Cliente iOS experimental | Precisa ser assinado separadamente |
| ZIP do código-fonte | GitHub/revisão | SHA-256 publicado junto |

Um APK assinado por outra chave não atualiza uma instalação existente. Confirme a origem antes de instalar ou substituir o aplicativo.

## Versão Android/Windows

Para cada release pública:

1. aumente `versionCode` sem reutilizar valores anteriores;
2. atualize `versionName` em `android-app/app/build.gradle`;
3. atualize agente, manifesto, `AssemblyInfo.cs`, `VERSION`, README e changelog;
4. mantenha o iOS experimental em sua própria cadência;
5. documente qualquer mudança de protocolo ou migração de dados.

## Checklist

- [ ] `python scripts/validate_repository.py` passou.
- [ ] Vetor Java AES/HMAC passou.
- [ ] Android `testDebugUnitTest`, `lintRelease`, `assembleDebug` e `bundleRelease` passaram.
- [ ] Agente compilou no Windows e foi testado após login/reinício.
- [ ] Pareamento, criação dos quatro tipos, aprovação no PC, janelas e WOL foram testados.
- [ ] GitHub Actions ficou totalmente verde.
- [ ] `CHANGELOG.md` e a política de privacidade continuam verdadeiros.
- [ ] Nenhum IP, MAC, código, cliente pareado, keystore ou certificado entrou no commit.
- [ ] Agente público foi assinado ou a ausência de assinatura foi claramente informada.

## Criar tag

Depois de confirmar o commit exato:

```powershell
git tag -a v1.0.1 -m "SyncDeck 1.0.1"
git push origin v1.0.1
```

Para uma versão futura, substitua o número em todos os locais e na tag.

## Release do GitHub

Anexe, conforme apropriado:

- agente Windows assinado e scripts de instalação/firewall;
- arquivo SHA-256 de cada binário;
- ZIP do código-fonte gerado pelo próprio GitHub ou por `scripts/package-source.ps1`;
- notas de instalação e requisitos do agente.

Não anexe chave de upload, `keystore.properties`, PFX, dados do perfil local ou APK debug como se fosse produção.

## Checksums

```powershell
Get-FileHash .\windows-agent\SyncDeckAgent.exe -Algorithm SHA256
Get-FileHash .\android-app\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

O checksum detecta alteração acidental no download, mas não substitui assinatura de código.

## Rollback

Para reverter uma falha, publique o último código conhecido com um `versionCode` novo e notas claras. Se houver mudança de formato local, mantenha migração para frente e backup antes de escrever.
