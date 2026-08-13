# Wake-on-LAN

O SyncDeck Android 1.0.1 e o cliente iOS experimental podem enviar um pacote mágico pelo Wi-Fi para a placa Ethernet do PC. Isso permite ligar um computador no estado **S5 (desligamento normal)**, desde que placa-mãe, firmware, placa de rede e fonte mantenham o circuito de rede energizado.

Não é possível ligar por software quando o computador está sem energia, a fonte está desligada ou o cabo de força foi removido. Esse estado físico é chamado G3.

## Antes de começar

- PC conectado ao roteador por cabo Ethernet.
- Android conectado ao Wi-Fi do mesmo roteador e fora de uma rede de convidados.
- Agente Windows 1.0.1 e Android 1.0.1; no iPhone, cliente experimental compatível.
- Placa Ethernet com suporte a Magic Packet.

O resultado de <code>powercfg /devicequery wake_from_any</code> contendo **Realtek PCIe GbE Family Controller** é um bom sinal: o Windows reconhece a placa como capaz de acordar o sistema em algum estado. Isso não garante sozinho o despertar a partir de S5; a BIOS ainda precisa oferecer e manter PME/Wake-on-LAN durante o desligamento.

## 1. Salvar a placa no celular

1. Ligue o PC e inicie o agente SyncDeck 1.0.1.
2. Abra o app já pareado no Android ou iPhone.
3. Toque em <code>↻</code> e aguarde **Conectado ao PC**.
4. Confirme que o botão **Ligar PC** apareceu.

O agente descobre a interface que realmente recebeu a conexão, calcula o broadcast da sub-rede e entrega MAC, broadcast e porta 9 por uma resposta autenticada. No Android 1.0, essa resposta também é cifrada. O aplicativo normaliza, valida e salva os dados localmente. Não é necessário digitar o MAC.

## 2. Configurar a BIOS/UEFI

Reinicie o computador e pressione a tecla indicada na primeira tela, normalmente <code>Del</code>, <code>F2</code>, <code>F10</code> ou <code>Esc</code>. Procure em menus como **Power**, **Power Management**, **APM** ou **Advanced**.

Habilite a opção disponível com nome semelhante a:

- **Wake on LAN**;
- **Power On By PCI-E/PCI**;
- **Resume By PCI-E Device**;
- **PME Event Wake Up**;
- **Wake From S5 by LAN**.

Se existirem, desabilite:

- **ErP Ready** ou **EuP**;
- **Deep Sleep** em S4/S5;
- qualquer economia que desligue PCIe/LAN no estado S5.

Salve com **Save & Exit**. Em placas H61/OEM, os nomes variam e a opção pode estar escondida no firmware do fabricante. Evite atualizar a BIOS somente para testar Wake-on-LAN sem antes identificar exatamente o fabricante e o arquivo correto.

## 3. Configurar a Realtek no Windows

1. Clique com o botão direito em **Iniciar > Gerenciador de Dispositivos**.
2. Expanda **Adaptadores de rede**.
3. Abra **Realtek PCIe GbE Family Controller > Propriedades**.
4. Na aba **Avançado**, habilite **Wake on Magic Packet**.
5. Se aparecer, habilite **Shutdown Wake-On-Lan**.
6. Se aparecer **WOL & Shutdown Link Speed**, mantenha **Auto**; se o LED apagar no teste, experimente **10 Mbps First**.
7. Na aba **Gerenciamento de Energia**, marque:
   - **Permitir que este dispositivo acorde o computador**;
   - **Só permitir que um pacote Magic acorde o computador**.

Em um Terminal/Prompt executado como administrador, confira ou habilite a placa:

~~~powershell
powercfg /devicequery wake_from_any
powercfg /deviceenablewake "Realtek PCIe GbE Family Controller"
powercfg /devicequery wake_armed
~~~

Se o driver Realtek não mostrar as opções, instale o driver oficial do fabricante do computador/placa-mãe ou da Realtek compatível com o hardware. Crie um ponto de restauração antes de trocar drivers em máquinas antigas.

## 4. Desativar a Inicialização Rápida

O desligamento híbrido pode impedir o Wake-on-LAN a partir do botão **Desligar**:

1. Abra **Painel de Controle > Hardware e Sons > Opções de Energia**.
2. Clique em **Escolher a função dos botões de energia**.
3. Clique em **Alterar configurações não disponíveis no momento**.
4. Desmarque **Ligar inicialização rápida (recomendado)**.
5. Salve.

Isso não desativa a suspensão. Como alternativa de diagnóstico, <code>powercfg /hibernate off</code> também remove a Inicialização Rápida, mas desativa a hibernação; use apenas se desejar esse efeito.

## 5. Testar

1. Desligue pelo menu do Windows e aguarde o computador parar completamente.
2. Observe a porta Ethernet. Pelo menos um LED deve continuar aceso ou piscar.
3. Mantenha o Android no Wi-Fi da casa.
4. Abra o SyncDeck; aparecerá **PC desligado ou indisponível — pronto para ligar**.
5. Toque em **Ligar PC**, confirme e aguarde.

O app envia três vezes o mesmo Magic Packet para o broadcast atual do Wi-Fi, para o broadcast salvo e para <code>255.255.255.255</code>. O Windows só volta a aparecer como conectado depois que iniciar o agente — normalmente após o login do usuário.

Depois de um despertar bem-sucedido, este comando pode mostrar a origem registrada pelo Windows:

~~~powershell
powercfg /lastwake
~~~

## Diagnóstico rápido

| Sintoma | Causa mais provável | O que verificar |
|---|---|---|
| Botão Ligar PC não aparece | Configuração ainda não sincronizada | Ligue o PC, atualize agente e Android para 1.0.1 e toque em <code>↻</code> |
| LED Ethernet apaga ao desligar | Energia de espera cortada | Wake/PME na BIOS, ErP/Deep Sleep e opção Shutdown Wake-On-Lan |
| Acorda da suspensão, mas não desligado | S5 bloqueado | Inicialização Rápida, Wake From S5 e suporte real da placa-mãe |
| Sinal é enviado, mas nada acontece | Broadcast isolado ou NIC desarmada | Mesmo roteador, Wi-Fi normal, cabo, driver e <code>wake_armed</code> |
| PC liga, mas app continua offline | Agente ainda não iniciou | Faça login no Windows e habilite iniciar com o Windows no ícone da bandeja |
| Funciona após reiniciar, mas não após desligar | Desligamento híbrido | Desative a Inicialização Rápida |

Se o LED continuar apagado mesmo após as opções corretas, o firmware OEM pode não oferecer Wake-on-LAN em S5. Nesse caso, o SyncDeck ainda poderá acordar da suspensão, mas nenhum aplicativo consegue religar um circuito que a placa-mãe desenergizou.

## Segurança e alcance

- O SyncDeck só envia Wake-on-LAN na rede local; não abre portas no roteador.
- O Magic Packet não autentica o remetente por definição. Qualquer equipamento na mesma rede que conheça o MAC pode acordar o PC.
- A rota que entrega a configuração ao celular é autenticada e assinada pelo pareamento existente.
- Acordar o computador não desbloqueia Windows, PIN, senha, BitLocker ou BIOS.
