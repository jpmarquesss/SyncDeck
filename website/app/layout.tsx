import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "SyncDeck — Controle seu Windows pelo celular",
  description:
    "Transforme seu celular em um painel inteligente para abrir programas, sites, pastas e comandos no Windows pela rede local.",
  applicationName: "SyncDeck",
  keywords: ["SyncDeck", "Stream Deck", "Windows", "Android", "automação", "rede local"],
  authors: [{ name: "Erick Carmo" }],
  openGraph: {
    title: "SyncDeck — Seu PC, a um toque de distância",
    description: "Controle programas, janelas e automações do Windows diretamente pelo celular.",
    type: "website",
    locale: "pt_BR",
    images: [
      {
        url: "/syncdeck-og.png",
        width: 1200,
        height: 630,
        alt: "SyncDeck — Seu PC, a um toque de distância",
      },
    ],
  },
  other: {
    "codex-preview": "development",
  },
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
    apple: "/apple-touch-icon.png",
  },
  manifest: "/manifest.webmanifest",
};

export const viewport: Viewport = {
  colorScheme: "dark",
  themeColor: "#050706",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR" className="dark">
      <body className={`${geistSans.variable} ${geistMono.variable}`}>
        {children}
      </body>
    </html>
  );
}
