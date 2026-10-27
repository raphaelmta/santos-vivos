import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Santos Vivos",
  description: "Converse com os santos a partir dos seus próprios escritos.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
