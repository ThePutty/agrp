"use client";

import { useState } from "react";

/** Ukázkové dotazy podle data/demo-questions.md (snapshot 300 rozhodnutí, 1.-4. 3. 2024). */
export const EXAMPLE_QUESTIONS = [
  "Vzal jsem si spotřebitelský úvěr, splátky jsem přestal platit a pohledávku koupila inkasní společnost. Ta po mně teď chce jistinu, úroky i smluvní pokutu. Musím platit i tomu, kdo mi peníze nepůjčil, a může po mně chtít všechny poplatky?",
  "Soud řekl, že smlouva o úvěru je neplatná. Věřitel po mně přesto vymáhá zaplacení celé částky jako bezdůvodné obohacení, včetně úroků. Mám mu vracet jen to, co jsem skutečně dostal?",
  "Nájemce mi přestal platit nájemné za nebytové prostory a odmítá je vyklidit. Můžu vedle dlužného nájemného žádat i úrok z prodlení a jak rychle se dá dosáhnout vyklizení?",
  "Po dopravní nehodě mi pojišťovna viníka proplatila jen část opravy, zbytek odmítla s tím, že jde o zhodnocení vozidla. Můžu se domáhat doplatku a nákladů na znalecký posudek?",
];

export default function QuestionForm({
  onSubmit,
  busy,
}: {
  onSubmit: (question: string) => void;
  busy: boolean;
}) {
  const [value, setValue] = useState("");

  function submit(text: string) {
    const q = text.trim();
    if (!q || busy) return;
    onSubmit(q);
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        submit(value);
      }}
      className="box"
    >
      <header className="box-head">
        <h2>
          <span className="muted">01 · </span>Dotaz
        </h2>
        <div className="meta">
          {busy ? (
            <span>
              ANALYZUJI<span className="cursor"> ▌</span>
            </span>
          ) : (
            <span>READY</span>
          )}
        </div>
      </header>

      <div className="box-body">
        <label htmlFor="question" className="label" style={{ display: "block", marginBottom: 6 }}>
          Popis případu nebo právní otázka
        </label>
        <p className="hint">
          Popište situaci vlastními slovy. Hledá se pouze v lokální databázi rozhodnutí
          (justice.cz, naplněné ručně na stránce Korpus), ne na internetu. Odpověď trvá 1-3 minuty.
        </p>
        <textarea
          id="question"
          rows={5}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) submit(value);
          }}
          placeholder="Např.: Klient zaplatil zálohu na základě smlouvy, která byla později shledána neplatnou…"
          className="inp"
        />
        <div
          style={{
            marginTop: 12,
            display: "flex",
            flexWrap: "wrap",
            alignItems: "center",
            gap: 16,
          }}
        >
          <button
            type="submit"
            disabled={busy || value.trim().length === 0}
            className="btn btn-primary"
          >
            {busy ? "Analyzuji…" : "Analyzovat"}
          </button>
          <span className="label">Ctrl/⌘ + Enter</span>
        </div>
      </div>

      <div className="box-sub" style={{ borderTop: "1px solid var(--ink)", borderBottom: 0 }}>
        Ukázkové dotazy
      </div>
      <div className="box-body">
        <ul className="numlist">
          {EXAMPLE_QUESTIONS.map((q, i) => (
            <li key={q}>
              <span className="idx">[{i + 1}]</span>
              <button
                type="button"
                disabled={busy}
                onClick={() => {
                  setValue(q);
                  submit(q);
                }}
              >
                {q.length > 110 ? `${q.slice(0, 110)}…` : q}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </form>
  );
}
