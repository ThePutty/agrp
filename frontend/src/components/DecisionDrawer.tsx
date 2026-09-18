"use client";

import { useEffect, useState } from "react";
import Highlight from "./Highlight";
import { fetchDecision } from "@/lib/graphql";
import type { Decision } from "@/lib/types";

export default function DecisionDrawer({
  decisionId,
  quotes,
  onClose,
}: {
  decisionId: string;
  /** Citované věty - zvýrazní se v textu odůvodnění. */
  quotes: string[];
  onClose: () => void;
}) {
  const [decision, setDecision] = useState<Decision | null>(null);
  const [error, setError] = useState<string | null>(null);

  // komponenta je v rodiči klíčovaná decisionId, takže se stav resetuje mountem
  useEffect(() => {
    let cancelled = false;
    fetchDecision(decisionId)
      .then((d) => {
        if (cancelled) return;
        if (!d) setError("Rozhodnutí nebylo nalezeno.");
        else setDecision(d);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      });
    return () => {
      cancelled = true;
    };
  }, [decisionId]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="drawer-wrap">
      <div className="drawer-scrim" onClick={onClose} aria-hidden />
      <aside className="drawer">
        <header className="box-head">
          <h2>{decision?.caseNumber ?? "Načítám…"}</h2>
          <div className="meta">
            <button type="button" onClick={onClose} className="btn">
              Zavřít ✕
            </button>
          </div>
        </header>

        {decision && (
          <table className="kv" style={{ borderTop: 0 }}>
            <tbody>
              <tr>
                <th scope="row">Soud</th>
                <td>{decision.court}</td>
              </tr>
              <tr>
                <th scope="row">Datum</th>
                <td>{decision.decidedOn ?? "-"}</td>
              </tr>
              <tr>
                <th scope="row">ECLI</th>
                <td>{decision.ecli ?? "-"}</td>
              </tr>
              <tr>
                <th scope="row">Ustanovení</th>
                <td>{decision.provisions.length ? decision.provisions.join(" · ") : "-"}</td>
              </tr>
              <tr>
                <th scope="row">Klíčová slova</th>
                <td>{decision.keywords.length ? decision.keywords.join(" · ") : "-"}</td>
              </tr>
              <tr>
                <th scope="row">Výsledek</th>
                <td>{decision.resultTypes.length ? decision.resultTypes.join(" · ") : "-"}</td>
              </tr>
            </tbody>
          </table>
        )}

        <div className="drawer-body">
          {error && <p className="red">{error}</p>}
          {!decision && !error && <p className="muted">Načítám rozhodnutí…</p>}

          {decision?.verdictText && (
            <section className="box">
              <header className="box-head">
                <h2>Výrok</h2>
              </header>
              <div className="box-body prose pre-wrap">
                <Highlight text={decision.verdictText} terms={quotes} />
              </div>
            </section>
          )}

          {decision?.justificationText && (
            <section className="box">
              <header className="box-head">
                <h2>Odůvodnění</h2>
                {quotes.length > 0 && (
                  <div className="meta">Zvýrazněny citované pasáže</div>
                )}
              </header>
              <div className="box-body prose pre-wrap">
                <Highlight text={decision.justificationText} terms={quotes} />
              </div>
            </section>
          )}

          {decision && (
            <a
              href={decision.sourceUrl}
              target="_blank"
              rel="noreferrer"
              className="btn"
              style={{ justifySelf: "start" }}
            >
              Zdroj · justice.cz ↗
            </a>
          )}
        </div>
      </aside>
    </div>
  );
}
