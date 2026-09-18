"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { setTheme, useTheme } from "@/lib/theme";

const NAV = [
  { href: "/", label: "Hledání" },
  { href: "/corpus", label: "Korpus" },
  { href: "/architecture", label: "Architektura" },
];

/** Observabilita: kam se dívat, když chci vidět, co systém pod kapotou dělá. */
const EXTERNAL = [
  {
    href: "http://localhost:8233/namespaces/default/workflows",
    label: "Temporal",
    tip: "Běhy workflow (ingest, research): stav, historie událostí, aktivity, retry, signály nodeUpdate, heartbeaty.",
  },
  {
    href: "http://localhost:4000/ui/logs",
    label: "LiteLLM logy",
    tip: "Každé volání modelu: alias → skutečný model, fallbacky, latence, tokeny, cena. Login admin/admin.",
  },
  {
    href: "http://localhost:8081/graphiql",
    label: "GraphiQL",
    tip: "Přímé dotazy na API backendu (research, ingest, corpusStats).",
  },
  {
    href: "http://localhost:8081/api/llm/models",
    label: "Modely",
    tip: "Aliasy modelů, které aplikace zná (chat-model, embed-model, fallbacky).",
  },
];

export default function Nav() {
  const pathname = usePathname();
  const theme = useTheme();

  return (
    <header className="nav">
      <nav className="nav-row">
        <div className="nav-cell">
          <Link href="/" className="nav-mark">
            <b>Judikatura AI</b>
            <span className="nav-rev">REV 0.1 · 2026-09</span>
          </Link>
        </div>
        <div className="nav-cell grow">
          {NAV.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className="nav-link"
              aria-current={pathname === l.href ? "page" : undefined}
            >
              {l.label}
            </Link>
          ))}
        </div>
        <div className="nav-cell">
          <span className="nav-group">Observabilita:</span>
          {EXTERNAL.map((l) => (
            <a
              key={l.href}
              href={l.href}
              target="_blank"
              rel="noreferrer"
              className="nav-ext"
              title={l.tip}
            >
              {l.label} ↗
            </a>
          ))}
          <button
            type="button"
            className="btn nav-theme"
            onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
            aria-label="Přepnout světlý / tmavý režim"
            title="Přepnout světlý / tmavý režim"
          >
            {theme === "dark" ? "■ DARK" : "□ LIGHT"}
          </button>
        </div>
      </nav>
    </header>
  );
}
