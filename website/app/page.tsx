"use client";

import { useState } from "react";

const repositoryUrl = "https://github.com/eudollyn/SyncDeck";
const downloadsUrl =
  "https://github.com/eudollyn/SyncDeck/actions/workflows/build.yml?query=branch%3Amain";

type Platform = "windows" | "android" | "iphone";

const platformSummary: Record<
  Platform,
  { eyebrow: string; title: string; text: string; artifact: string }
> = {
  windows: {
    eyebrow: "01 · Prepare o computador",
    title: "Agente Windows",
    text: "Baixe o artefato SyncDeck-Windows, extraia a pasta e execute o instalador permanente. Ele mantém o agente ativo sempre que você entrar no Windows.",
    artifact: "SyncDeck-Windows",
  },
  android: {
    eyebrow: "02 · Instale no celular",
    title: "Aplicativo Android",
    text: "Baixe SyncDeck-Android-debug, instale o APK e permita a instalação manual somente durante esse processo. Compatível com Android 8.0 ou superior.",
    artifact: "SyncDeck-Android-debug",
  },
  iphone: {
    eyebrow: "Alternativa experimental",
    title: "Aplicativo para iPhone",
    text: "Baixe o IPA sem assinatura e instale pelo Windows usando uma ferramenta de assinatura pessoal. Esta versão ainda não é uma distribuição oficial da App Store.",
    artifact: "SyncDeck-iOS-unsigned",
  },
};

const features = [
  {
    number: "01",
    title: "Um toque. Ação imediata.",
    text: "Abra programas, sites, pastas e arquivos ou traga uma janela aberta para frente sem voltar ao teclado.",
    className: "feature-wide feature-green",
    visual: "focus",
  },
  {
    number: "02",
    title: "Seu painel, do seu jeito.",
    text: "Crie botões com um assistente simples. Os ícones dos programas são encontrados automaticamente no PC.",
    className: "feature-standard",
    visual: "add",
  },
  {
    number: "03",
    title: "Ligou, conectou.",
    text: "O pareamento permanece salvo e o celular reencontra o computador quando o roteador troca o IP.",
    className: "feature-standard",
    visual: "reconnect",
  },
  {
    number: "04",
    title: "Controle até com o PC desligado.",
    text: "Com Wake-on-LAN configurado, um botão envia o Magic Packet e liga o computador pela rede cabeada.",
    className: "feature-wide feature-purple",
    visual: "power",
  },
];

const guideSteps = [
  {
    number: "01",
    title: "Baixe os arquivos oficiais",
    text: "Abra o último processo concluído na área Actions do GitHub e role até Artifacts. Baixe SyncDeck-Windows e SyncDeck-Android-debug.",
    note: "Os artefatos ficam disponíveis por 14 dias após cada compilação.",
  },
  {
    number: "02",
    title: "Instale o agente no Windows",
    text: "Extraia o ZIP do Windows, abra a pasta e execute Instalar-no-Windows.bat. Depois, execute Configurar-Firewall.bat como administrador uma única vez.",
    note: "O agente fica em %LOCALAPPDATA%\\SyncDeck\\Agent e inicia com seu usuário.",
  },
  {
    number: "03",
    title: "Instale o aplicativo Android",
    text: "Extraia o download do Android, envie o APK ao celular, permita a instalação dessa fonte quando solicitado e conclua a instalação.",
    note: "Após instalar, desative novamente a permissão para instalar apps desconhecidos.",
  },
  {
    number: "04",
    title: "Pareie celular e computador",
    text: "Abra o ícone do SyncDeck perto do relógio, escolha Parear celular e informe no app o IP e a porta exibidos. Compare as impressões digitais e digite o código de seis números.",
    note: "O código expira em cinco minutos e aceita no máximo cinco tentativas.",
  },
];

const actionTypes = [
  { icon: "▦", title: "Programa", text: "Escolha na lista do PC. O caminho e o ícone são detectados automaticamente." },
  { icon: "⌁", title: "Site", text: "Informe o endereço e escolha entre navegador padrão ou Chrome no perfil ativo." },
  { icon: "⌑", title: "Pasta ou arquivo", text: "Abra o seletor seguro no PC e escolha o destino sem digitar caminhos." },
  { icon: ">_", title: "Comando", text: "Configure executável e argumentos com confirmação no celular e aprovação no PC." },
];

const securityItems = [
  { title: "Sem nuvem", text: "O SyncDeck não possui conta, servidor remoto, anúncios, telemetria ou rastreamento." },
  { title: "Pareamento verificado", text: "Código temporário, impressão digital comparada nas duas telas e segredo exclusivo por celular." },
  { title: "Conteúdo cifrado", text: "AES-256, HMAC-SHA-256, timestamp e nonce protegem as solicitações autenticadas." },
  { title: "Aprovação dupla", text: "Comandos e atalhos sensíveis exigem confirmação no Android e autorização visível no Windows." },
  { title: "Rede privada", text: "O aplicativo aceita apenas IPs locais e a regra de firewall limita o agente à sub-rede privada." },
  { title: "Privilégio mínimo", text: "O Android usa apenas acesso à rede; não lê contatos, SMS, localização, câmera ou dados bancários." },
];

const faqs = [
  {
    question: "O SyncDeck funciona pela internet?",
    answer: "Não. Ele foi projetado para funcionar com celular e PC conectados ao mesmo roteador. Não abra a porta 47321 na internet e não use encaminhamento de porta.",
  },
  {
    question: "Preciso parear novamente todo dia?",
    answer: "Não. O segredo fica protegido no Android e no Windows. Se o roteador mudar o IP do PC, o aplicativo procura automaticamente o mesmo computador.",
  },
  {
    question: "O PC pode estar conectado por cabo?",
    answer: "Sim. O cenário ideal é o PC conectado por cabo Ethernet e o celular no Wi-Fi do mesmo roteador. Essa configuração também é necessária para ligar o PC por Wake-on-LAN.",
  },
  {
    question: "Posso fechar qualquer programa pelo celular?",
    answer: "O agente envia um pedido normal de fechamento à janela. Aplicativos elevados, travados ou que ignoram esse pedido podem recusar; o SyncDeck não força o encerramento para evitar perda de dados.",
  },
  {
    question: "Existe versão para iPhone?",
    answer: "Existe um cliente experimental compatível com iOS 15 e iPhone 11 Pro. Ele ainda precisa ser assinado e instalado manualmente e não é uma distribuição oficial da App Store.",
  },
];

function SyncDeckMark() {
  return (
    <span className="brand-mark" aria-hidden="true">
      <span />
      <span />
      <span />
      <span />
    </span>
  );
}

function ArrowIcon() {
  return <span aria-hidden="true">↗</span>;
}

function PhoneDemo() {
  const buttons = [
    { label: "Chrome", icon: "◉", tone: "blue", active: true },
    { label: "WhatsApp", icon: "W", tone: "green", active: true },
    { label: "Outlook", icon: "O", tone: "azure", active: false },
    { label: "Explorador", icon: "—", tone: "gold", active: false },
    { label: "Prompt", icon: ">_", tone: "indigo", active: false },
    { label: "ChatGPT", icon: "✦", tone: "cyan", active: true },
  ];

  return (
    <div className="product-visual" aria-label="Prévia do painel SyncDeck">
      <div className="ambient-ring ambient-ring-one" />
      <div className="ambient-ring ambient-ring-two" />

      <div className="desktop-card glass-card">
        <div className="desktop-topbar">
          <span className="traffic"><i /><i /><i /></span>
          <span>SyncDeck Agent</span>
          <span className="secure-pill">Protegido</span>
        </div>
        <div className="desktop-content">
          <div className="desktop-status-icon"><SyncDeckMark /></div>
          <div>
            <strong>Pronto para controlar</strong>
            <p>PC conectado · porta 47321</p>
          </div>
        </div>
        <div className="desktop-pairing">
          <span>Dispositivo pareado</span>
          <i className="live-dot" />
        </div>
      </div>

      <div className="phone-shell">
        <div className="phone-speaker" />
        <div className="phone-screen">
          <div className="phone-header">
            <div>
              <small>SYNCDECK</small>
              <strong>Meu painel</strong>
            </div>
            <button type="button" aria-label="Adicionar botão">＋</button>
          </div>
          <div className="connection-line"><i /> Conectado ao PC</div>
          <div className="deck-grid">
            {buttons.map((button) => (
              <div
                className={`deck-button ${button.active ? "is-active" : ""}`}
                key={button.label}
              >
                <span className={`app-symbol tone-${button.tone}`}>{button.icon}</span>
                <strong>{button.label}</strong>
                {button.active && <i className="app-live-dot" />}
              </div>
            ))}
          </div>
          <div className="phone-homebar" />
        </div>
      </div>

      <div className="floating-chip chip-open glass-card">
        <i /> Chrome aberto
      </div>
      <div className="floating-chip chip-lan glass-card">
        <span>⌁</span> Rede local
      </div>
    </div>
  );
}

export default function Home() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [platform, setPlatform] = useState<Platform>("windows");
  const selectedPlatform = platformSummary[platform];

  return (
    <main id="top">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="SyncDeck — início">
          <SyncDeckMark />
          <span>SyncDeck</span>
        </a>

        <nav className="desktop-nav" aria-label="Navegação principal">
          <a href="#como-funciona">Como funciona</a>
          <a href="#recursos">Recursos</a>
          <a href="#instalacao">Instalação</a>
          <a href="#seguranca">Segurança</a>
        </nav>

        <div className="header-actions">
          <a className="github-link" href={repositoryUrl} target="_blank" rel="noreferrer">
            GitHub <ArrowIcon />
          </a>
          <a className="button button-small button-primary" href={downloadsUrl} target="_blank" rel="noreferrer">
            Baixar grátis
          </a>
          <button
            className="menu-toggle"
            type="button"
            aria-label="Abrir menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((current) => !current)}
          >
            <span />
            <span />
          </button>
        </div>

        {menuOpen && (
          <nav className="mobile-nav" aria-label="Navegação para celular">
            <a href="#como-funciona" onClick={() => setMenuOpen(false)}>Como funciona</a>
            <a href="#recursos" onClick={() => setMenuOpen(false)}>Recursos</a>
            <a href="#instalacao" onClick={() => setMenuOpen(false)}>Instalação</a>
            <a href="#seguranca" onClick={() => setMenuOpen(false)}>Segurança</a>
            <a href={repositoryUrl} target="_blank" rel="noreferrer">Abrir no GitHub</a>
          </nav>
        )}
      </header>

      <section className="hero section-shell">
        <div className="hero-copy">
          <div className="version-badge">
            <i />
            SyncDeck 1.0.0 disponível
          </div>
          <h1>
            Seu PC, a um
            <span> toque de distância.</span>
          </h1>
          <p className="hero-lead">
            Transforme seu celular em um painel inteligente para abrir, focar,
            fechar e automatizar tarefas no Windows — diretamente pela sua rede local.
          </p>
          <div className="hero-actions">
            <a className="button button-primary button-large" href={downloadsUrl} target="_blank" rel="noreferrer">
              Baixar SyncDeck <span>↓</span>
            </a>
            <a className="button button-ghost button-large" href="#instalacao">
              Ver como instalar <span>→</span>
            </a>
          </div>
          <div className="hero-trust" aria-label="Características principais">
            <span><i>✓</i> Gratuito e código aberto</span>
            <span><i>✓</i> Sem conta ou anúncios</span>
            <span><i>✓</i> Sem nuvem</span>
          </div>
        </div>
        <PhoneDemo />
      </section>

      <section className="proof-bar" aria-label="Compatibilidade e segurança">
        <div><strong>100%</strong><span>Rede local</span></div>
        <div><strong>AES-256</strong><span>Conteúdo cifrado</span></div>
        <div><strong>3</strong><span>Plataformas</span></div>
        <div><strong>MIT</strong><span>Código aberto</span></div>
      </section>

      <section className="section section-shell" id="como-funciona">
        <div className="section-heading centered-heading">
          <span className="eyebrow">SIMPLES POR NATUREZA</span>
          <h2>Do celular ao Windows.<br />Sem complicação.</h2>
          <p>O celular envia o pedido, o agente executa no PC e o estado volta para o seu painel em tempo real.</p>
        </div>

        <div className="steps-flow">
          <article className="step-card">
            <span className="step-number">01</span>
            <div className="step-icon phone-icon"><i /></div>
            <h3>Toque no botão</h3>
            <p>Escolha uma ação no painel em modo retrato ou paisagem.</p>
          </article>
          <span className="flow-arrow" aria-hidden="true">→</span>
          <article className="step-card highlighted-step">
            <span className="step-number">02</span>
            <div className="step-icon signal-icon"><i /><i /><i /></div>
            <h3>Envio protegido</h3>
            <p>A solicitação autenticada e cifrada viaja apenas pela rede local.</p>
          </article>
          <span className="flow-arrow" aria-hidden="true">→</span>
          <article className="step-card">
            <span className="step-number">03</span>
            <div className="step-icon monitor-icon"><i /></div>
            <h3>O Windows responde</h3>
            <p>O agente abre, foca ou fecha a janela e atualiza o painel.</p>
          </article>
        </div>
      </section>

      <section className="section section-shell" id="recursos">
        <div className="section-heading split-heading">
          <div>
            <span className="eyebrow">FEITO PARA O SEU FLUXO</span>
            <h2>Mais que atalhos.<br />Controle de verdade.</h2>
          </div>
          <p>Uma experiência pensada para ser rápida na primeira configuração e poderosa no uso diário.</p>
        </div>

        <div className="features-grid">
          {features.map((feature) => (
            <article className={`feature-card ${feature.className}`} key={feature.number}>
              <span className="feature-number">{feature.number}</span>
              <div className={`feature-visual visual-${feature.visual}`} aria-hidden="true">
                {feature.visual === "focus" && (
                  <><span className="mini-app active-mini">C</span><span className="mini-app">W</span><i className="focus-line" /></>
                )}
                {feature.visual === "add" && <span className="big-plus">＋</span>}
                {feature.visual === "reconnect" && <><span className="reconnect-pc" /><span className="reconnect-phone" /><i /></>}
                {feature.visual === "power" && <><span className="power-button">⌁</span><i className="power-wave wave-one" /><i className="power-wave wave-two" /></>}
              </div>
              <div className="feature-copy">
                <h3>{feature.title}</h3>
                <p>{feature.text}</p>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="section section-shell install-preview" id="instalacao">
        <div className="section-heading centered-heading compact-heading">
          <span className="eyebrow">COMECE EM POUCOS MINUTOS</span>
          <h2>Escolha onde instalar</h2>
          <p>Prepare o agente no Windows e depois instale o painel no celular.</p>
        </div>

        <div className="platform-tabs" role="tablist" aria-label="Plataformas">
          {(["windows", "android", "iphone"] as Platform[]).map((item) => (
            <button
              type="button"
              role="tab"
              aria-selected={platform === item}
              className={platform === item ? "active-tab" : ""}
              onClick={() => setPlatform(item)}
              key={item}
            >
              <span className={`platform-logo logo-${item}`} aria-hidden="true">
                {item === "windows" ? "⊞" : item === "android" ? "A" : "●"}
              </span>
              {item === "windows" ? "Windows" : item === "android" ? "Android" : "iPhone"}
            </button>
          ))}
        </div>

        <div className="platform-panel" role="tabpanel">
          <div>
            <span className="panel-eyebrow">{selectedPlatform.eyebrow}</span>
            <h3>{selectedPlatform.title}</h3>
            <p>{selectedPlatform.text}</p>
          </div>
          <div className="artifact-card">
            <small>ARQUIVO NO GITHUB</small>
            <strong>{selectedPlatform.artifact}</strong>
            <a href={downloadsUrl} target="_blank" rel="noreferrer">Abrir downloads <ArrowIcon /></a>
          </div>
        </div>

        <a className="text-cta" href="#guia-instalacao">
          Ver instalação completa passo a passo <span>→</span>
        </a>
      </section>

      <section className="guide-section" id="guia-instalacao">
        <div className="section-shell guide-layout">
          <aside className="guide-aside">
            <span className="eyebrow">GUIA DE INSTALAÇÃO</span>
            <h2>Do download ao primeiro toque.</h2>
            <p>Siga estas quatro etapas na ordem. Em uma instalação comum, você só precisa configurar o firewall e parear uma vez.</p>
            <a className="button button-primary button-large" href={downloadsUrl} target="_blank" rel="noreferrer">
              Abrir downloads <ArrowIcon />
            </a>
          </aside>
          <div className="guide-steps">
            {guideSteps.map((step) => (
              <article className="guide-step" key={step.number}>
                <span>{step.number}</span>
                <div>
                  <h3>{step.title}</h3>
                  <p>{step.text}</p>
                  <small><i>i</i>{step.note}</small>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section section-shell setup-check">
        <div className="section-heading split-heading setup-heading">
          <div>
            <span className="eyebrow">ANTES DE PAREAR</span>
            <h2>Checklist rápido.</h2>
          </div>
          <p>Evite os erros mais comuns conferindo estes pontos antes de tocar em Verificar PC.</p>
        </div>
        <div className="check-grid">
          <div><i>✓</i><strong>Mesma rede</strong><span>PC e celular no mesmo roteador</span></div>
          <div><i>✓</i><strong>Rede privada</strong><span>Perfil da Ethernet como Privada</span></div>
          <div><i>✓</i><strong>Agente aberto</strong><span>Escudo visível perto do relógio</span></div>
          <div><i>✓</i><strong>IP correto</strong><span>Somente IPv4, sem http://</span></div>
        </div>
      </section>

      <section className="section section-shell actions-section" id="adicionar-botoes">
        <div className="section-heading centered-heading">
          <span className="eyebrow">MONTE SEU PAINEL</span>
          <h2>Adicionar um botão ficou simples.</h2>
          <p>Toque em ＋ no aplicativo, escolha o tipo e siga o assistente. As opções avançadas aparecem somente quando você precisa delas.</p>
        </div>
        <div className="action-types">
          {actionTypes.map((action) => (
            <article key={action.title}>
              <span>{action.icon}</span>
              <h3>{action.title}</h3>
              <p>{action.text}</p>
            </article>
          ))}
        </div>
        <div className="gestures-card">
          <div>
            <span className="panel-eyebrow">GESTOS DO PAINEL</span>
            <h3>Rápido para usar. Difícil de acionar sem querer.</h3>
          </div>
          <div className="gesture-list">
            <span><b>Toque</b> Abrir ou focar</span>
            <span><b>Segurar</b> Editar ou ver opções</span>
            <span><b>×</b> Confirmar e fechar</span>
            <span><b>Girar</b> Painel com 3 colunas</span>
          </div>
        </div>
      </section>

      <section className="wake-section" id="wake-on-lan">
        <div className="section-shell wake-layout">
          <div className="wake-visual" aria-hidden="true">
            <i className="wake-ring wake-ring-one" />
            <i className="wake-ring wake-ring-two" />
            <span>⌁</span>
          </div>
          <div>
            <span className="eyebrow">WAKE-ON-LAN</span>
            <h2>Ligue o PC mesmo totalmente desligado.</h2>
            <p>Quando a placa-mãe e o adaptador Ethernet suportam Magic Packet em S5, o botão Ligar PC continua disponível mesmo com o agente offline.</p>
            <ul>
              <li>Ative Wake-on-LAN ou PME na BIOS/UEFI.</li>
              <li>Ative Pacote Wake on Magic no adaptador Realtek.</li>
              <li>Permita que o dispositivo acorde o computador.</li>
              <li>Desative a Inicialização Rápida do Windows.</li>
            </ul>
            <a className="text-link" href={`${repositoryUrl}/blob/main/docs/WAKE-ON-LAN.md`} target="_blank" rel="noreferrer">
              Abrir manual de Wake-on-LAN <ArrowIcon />
            </a>
          </div>
        </div>
      </section>

      <section className="security-teaser" id="seguranca">
        <div className="section-shell security-teaser-inner">
          <div>
            <span className="eyebrow">PRIVACIDADE DESDE O PRIMEIRO TOQUE</span>
            <h2>Seu painel. Sua rede.<br />Seus dados.</h2>
          </div>
          <div className="security-points">
            <span><i>✓</i> Sem servidor em nuvem</span>
            <span><i>✓</i> Pareamento com código temporário</span>
            <span><i>✓</i> Aprovação no PC para comandos sensíveis</span>
          </div>
        </div>
      </section>

      <section className="section section-shell security-detail">
        <div className="security-grid">
          {securityItems.map((item) => (
            <article key={item.title}>
              <i>✓</i>
              <h3>{item.title}</h3>
              <p>{item.text}</p>
            </article>
          ))}
        </div>
        <div className="security-note">
          <span>Importante</span>
          <p>O conteúdo protegido trafega por HTTP local porque o agente não depende de certificado externo, mas ações, caminhos e respostas autenticadas são cifrados antes do envio. O Wake-on-LAN é uma exceção do próprio padrão e deve permanecer somente na rede privada.</p>
        </div>
      </section>

      <section className="section section-shell troubleshooting" id="ajuda">
        <div className="section-heading split-heading">
          <div>
            <span className="eyebrow">RESOLVA SEM COMPLICAÇÃO</span>
            <h2>Dúvidas frequentes.</h2>
          </div>
          <p>Respostas diretas para configuração, conexão e uso diário.</p>
        </div>
        <div className="faq-list">
          {faqs.map((faq) => (
            <details key={faq.question}>
              <summary>{faq.question}<span>＋</span></summary>
              <p>{faq.answer}</p>
            </details>
          ))}
        </div>
        <div className="help-card">
          <div>
            <span className="panel-eyebrow">AINDA PRECISA DE AJUDA?</span>
            <h3>Consulte o guia de solução de problemas.</h3>
            <p>Há instruções específicas para conexão, firewall, Chrome, janelas, inicialização automática e Wake-on-LAN.</p>
          </div>
          <a className="button button-ghost button-large" href={`${repositoryUrl}/blob/main/docs/TROUBLESHOOTING.md`} target="_blank" rel="noreferrer">
            Abrir guia completo <ArrowIcon />
          </a>
        </div>
      </section>

      <section className="final-cta">
        <div className="section-shell final-cta-inner">
          <span className="version-badge"><i /> Versão 1.0.0</span>
          <h2>Seu fluxo, agora<br />a um toque.</h2>
          <p>Baixe o SyncDeck, conecte seu celular e transforme tarefas repetitivas em botões.</p>
          <div className="hero-actions">
            <a className="button button-primary button-large" href={downloadsUrl} target="_blank" rel="noreferrer">Baixar grátis <span>↓</span></a>
            <a className="button button-ghost button-large" href={repositoryUrl} target="_blank" rel="noreferrer">Ver código no GitHub <ArrowIcon /></a>
          </div>
        </div>
      </section>

      <footer className="site-footer">
        <div className="section-shell footer-inner">
          <a className="brand" href="#top"><SyncDeckMark /><span>SyncDeck</span></a>
          <p>Controle seu Windows de um jeito mais rápido, simples e seguro.</p>
          <div>
            <a href={repositoryUrl} target="_blank" rel="noreferrer">GitHub</a>
            <a href="#instalacao">Instalação</a>
            <a href="#ajuda">Ajuda</a>
          </div>
        </div>
      </footer>
    </main>
  );
}
