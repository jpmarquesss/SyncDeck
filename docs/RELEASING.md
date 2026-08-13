# Releases

## Artefatos

| Artefato | Uso | Assinatura |
|---|---|---|
| `app-debug.apk` | Teste local | Chave debug; nunca produção |
| `app-release.aab` | Upload na Google Play | Chave de upload + Play App Signing |
| `SyncDeckAgent.exe` | Agente Windows | Authenticode recomendado antes de distribuir |
| `SyncDeck-unsigned.ipa` | Cliente iOS experimental | Precisa ser assinado separadamente |
| ZIP do código-fonte | GitHub/revisão | SHA-256 publicado junto |

O APK debug de outra máquina pode usar uma assinatura diferente e não atualizar uma instalação existente. A primeira instalação da Play também não substitui um APK debug: desinstale-o e pareie novamente uma vez.

## Versão Android/Windows

Para cada release pública:

1. aumente `versionCode` sem reutilizar valores da Play;
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
- [ ] `CHANGELOG.md`, política de privacidade, Data Safety e texto da loja continuam verdadeiros.
- [ ] Nenhum IP, MAC, código, cliente pareado, keystore ou certificado entrou no commit.
- [ ] AAB foi instalado por teste interno/fechado e o relatório de pré-lançamento foi revisado.
- [ ] Agente público foi assinado ou a ausência de assinatura foi claramente informada.

## Gerar AAB

Crie `android-app/keystore.properties` localmente e execute:

```powershell
cd android-app
Gerar-AAB.bat
```

Não publique a chave de upload. Veja [PLAY-STORE.md](PLAY-STORE.md) para o processo completo.

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
Get-FileHash .\android-app\app\build\outputs\bundle\release\app-release.aab -Algorithm SHA256
```

O checksum detecta alteração acidental no download, mas não substitui assinatura de código.

## Rollback

A Google Play não permite diminuir `versionCode`. Para reverter uma falha, publique o último código conhecido com um `versionCode` novo e notas claras. Se houver mudança de formato local, mantenha migração para frente e backup antes de escrever.
