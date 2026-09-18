import type { Metadata } from "next";
import Nav from "@/components/Nav";
import { THEME_BOOT_SCRIPT } from "@/lib/theme";
import "./globals.css";

export const metadata: Metadata = {
  title: "Judikatura AI · ověřené citace",
  description:
    "AI vyhledávání v české judikatuře s ověřenými citacemi - CODEXIS hackathon.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="cs" suppressHydrationWarning>
      <head>
        {/* nastaví data-theme před prvním vykreslením, aby tmavý režim nebliknul */}
        <script dangerouslySetInnerHTML={{ __html: THEME_BOOT_SCRIPT }} />
      </head>
      <body>
        <div className="frame">
          <Nav />

          <main className="page">{children}</main>

          <footer className="foot">
            <div className="foot-cell">
              Data: MSp ČR · CC BY 4.0 · rozhodnuti.justice.cz
            </div>
            <div className="foot-cell">
              Demo
            </div>
            <div className="foot-cell">
              REV 0.1
            </div>
          </footer>
        </div>
      </body>
    </html>
  );
}
