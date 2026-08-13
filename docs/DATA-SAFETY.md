# Modelo de Data Safety

Este documento ajuda a preencher o formulário da Google Play para o código 1.0.1 atual. Ele não substitui a responsabilidade do proprietário da conta: revise novamente se adicionar analytics, anúncios, crash reporting, login, servidor, suporte embutido ou novas bibliotecas.

## Respostas propostas

| Pergunta | Resposta proposta | Motivo |
|---|---|---|
| O app coleta ou compartilha tipos de dados obrigatórios? | Não | Não existe backend/SDK do desenvolvedor; a comunicação é local com o PC do usuário |
| Os dados são criptografados em trânsito? | Sim, para o canal autenticado Android 1.0 | O conteúdo é cifrado antes do HTTP e autenticado por HMAC |
| O usuário pode solicitar exclusão? | Sim, diretamente no dispositivo | Desparear/limpar dados remove o celular; o agente revoga clientes e permite apagar a pasta local |
| O app segue a política de famílias? | Não declarar como app infantil | Ferramenta de produtividade geral, sem direcionamento específico a crianças |
| O app contém anúncios? | Não | Nenhum SDK ou conteúdo publicitário |

## Dados que não chegam ao desenvolvedor

O app processa localmente IP privado, nome/modelo do dispositivo, UUID aleatório, segredo, botões, ícones, estado de janelas e Wake-on-LAN. Esses dados vão somente ao computador selecionado pelo próprio usuário.

Na interpretação usada por este projeto, isso não é coleta pelo desenvolvedor nem compartilhamento com terceiros, pois não existe servidor, conta, parceiro ou acesso do mantenedor. Confirme a definição exibida no formulário vigente do Console e mantenha a resposta coerente com [PRIVACY-POLICY.md](../PRIVACY-POLICY.md).

## Checklist antes de confirmar “nenhuma coleta”

- [ ] `AndroidManifest.xml` contém somente `android.permission.INTERNET`.
- [ ] Não foram adicionados Firebase, Sentry, Crashlytics, AdMob ou SDK semelhante.
- [ ] Não existe URL de API externa no Android.
- [ ] O agente continua aceitando somente IP privado/local.
- [ ] Logs de desenvolvimento não enviam conteúdo a terceiros.
- [ ] A política de privacidade está pública e tem contato válido.
- [ ] O e-mail de suporte do Console é monitorado.

Referência: [formulário Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469).
