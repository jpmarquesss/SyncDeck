# Segurança do SyncDeck

## Relatar uma vulnerabilidade

Não publique vulnerabilidades, chaves, IPs ou provas de conceito exploráveis em uma issue pública. No GitHub, abra a aba **Security**, acesse **Advisories** e use **Report a vulnerability** para comunicar de forma privada.

Inclua versão, impacto, pré-condições, passos mínimos de reprodução e uma sugestão de correção, quando possível. Não teste contra computadores ou redes sem autorização. O recebimento e a prioridade serão confirmados conforme a disponibilidade do mantenedor; não existe SLA comercial.

## Versões suportadas

| Versão | Correções de segurança |
|---|---:|
| 0.3.x | Sim |
| 0.2.x e anteriores | Não |

Atualize sempre o APK e o agente Windows em conjunto.

## Uso no celular bancário

O SyncDeck foi desenhado para não acessar dados bancários ou pessoais do Android. O manifesto não solicita SMS, contatos, armazenamento, câmera, microfone, notificações nem serviço de acessibilidade. Ainda assim, instalar qualquer APK manual em um aparelho dedicado a bancos deve ser uma decisão consciente:

1. Gere o APK a partir deste código-fonte ou do fluxo de compilação incluído.
2. Não aceite APKs recompilados por terceiros.
3. Desative novamente a permissão “Instalar apps desconhecidos” após a instalação.
4. Não ative depuração USB permanentemente.
5. Use o SyncDeck somente no Wi‑Fi particular de casa ou da empresa; evite redes públicas.

Alguns aplicativos bancários podem alertar sobre modo desenvolvedor, depuração USB ou instalação por fontes externas. Isso depende das regras de cada banco.

## Modelo de proteção

- **Alcance:** porta TCP `47321`, limitada pelo agente a endereços IPv4 privados e pela regra recomendada do Firewall ao perfil privado e à sub-rede local.
- **Pareamento:** chave RSA persistida no perfil do Windows; código temporário de seis números; conferência manual da impressão digital; segredo aleatório de 256 bits criado no Android e enviado cifrado.
- **Autenticação:** HMAC‑SHA‑256 sobre método, rota, horário, nonce e hash do corpo. As respostas também são assinadas e conferidas pelo Android antes de serem usadas.
- **Antirrepetição:** janela de horário de 90 segundos e cache de nonces já utilizados.
- **Proteção em repouso:** Android Keystore com AES‑GCM e Windows DPAPI no escopo do usuário atual.
- **Execução:** somente ações salvas. O fechamento usa `WM_CLOSE`; não existe encerramento forçado de processos.
- **Comandos:** ações do tipo `command` exigem confirmação e executam diretamente o programa configurado, sem concatenar texto recebido a um shell.
- **Imagens:** os ícones são extraídos localmente do Windows, enviados em PNG por uma resposta autenticada e recusados pelo Android se excederem os limites de tamanho ou dimensão.

## O que não é criptografado

Nesta versão, após o pareamento, o transporte usa HTTP local autenticado. Um observador da mesma rede pode ver nomes, IDs, imagens e o estado aberto/fechado dos botões, mas não consegue alterar ou repetir comandos sem a chave. Títulos das janelas não são enviados ao celular. Caminhos completos aparecem somente quando o editor Android solicita a configuração.

Não utilize o SyncDeck em Wi‑Fi público, de hotel ou de terceiros. Uma versão futura pode adicionar TLS com certificado fixado no aplicativo.

## Revogação

No ícone da bandeja do Windows, selecione **Revogar celulares pareados**. No Android, abra `•••` e use **Desparear**. Depois disso, gere um novo código.
