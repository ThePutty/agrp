"use client";

import { useState } from "react";
import { Panel, StatusLine, ms } from "./ui";
import DbPeek from "./DbPeek";
import type { TraceEntry } from "@/lib/types";

/** Pořadí vrstev v demu - ostatní vrstvy se připojí na konec. */
const LAYER_ORDER = [
  "Temporal",
  "PostgreSQL/pgvector",
  "LangGraph4j",
  "LangChain4j",
  "LiteLLM",
  "OpenRouter/Ollama",
  "CitationVerifier",
];

function layerRank(layer: string) {
  const i = LAYER_ORDER.findIndex(
    (l) =>
      l.toLowerCase() === layer.toLowerCase() ||
      layer.toLowerCase().includes(l.toLowerCase()),
  );
  return i === -1 ? LAYER_ORDER.length : i;
}

export default function TechnicalTrace({
  trace,
  decisionIds = [],
  defaultOpen = false,
  no = "10",
}: {
  trace: TraceEntry[];
  /** Rozhodnutí nalezená dotazem - vzorek řádků z DB se bere z nich. */
  decisionIds?: string[];
  defaultOpen?: boolean;
  no?: string;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const [dbOpen, setDbOpen] = useState(false);

  const groups = new Map<string, TraceEntry[]>();
  for (const entry of trace) {
    const list = groups.get(entry.layer) ?? [];
    list.push(entry);
    groups.set(entry.layer, list);
  }
  const ordered = [...groups.entries()].sort((a, b) => layerRank(a[0]) - layerRank(b[0]));

  return (
    <Panel
      no={no}
      title="Technická stopa"
      help="Technický záznam běhu po vrstvách: Temporal (orchestrace), PostgreSQL/pgvector (hledání), LangGraph4j (uzly AI grafu), LangChain4j (jednotlivá volání modelu s počtem tokenů), LiteLLM → OpenRouter/Ollama (který model skutečně odpověděl), CitationVerifier (ověření). Trvání v milisekundách ukazuje, kde se čeká. Tlačítko Databáze ukáže, co fyzicky leží v PostgreSQL: velikosti tabulek a indexů a řádky tabulky chunk včetně začátku embeddingu."
      subtitle="Co se stalo pod kapotou, vrstva po vrstvě"
      flush
      right={
        <>
          <button type="button" onClick={() => setDbOpen((v) => !v)} className="btn">
            {dbOpen ? "Skrýt databázi" : "Databáze"}
          </button>
          <button type="button" onClick={() => setOpen((v) => !v)} className="btn">
            {open ? "Skrýt" : `Zobrazit (${trace.length})`}
          </button>
        </>
      }
    >
      {dbOpen && <DbPeek decisionIds={decisionIds} />}
      {open && (
        <div className="tbl-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Vrstva</th>
                <th>Operace</th>
                <th>Stav</th>
                <th className="r nowrap">Trvání</th>
                <th>Detail</th>
              </tr>
            </thead>
            <tbody>
              {ordered.map(([layer, entries]) =>
                entries.map((e, i) => (
                  <tr key={`${layer}-${e.name}-${i}`}>
                    <td className="nowrap label">{i === 0 ? layer : ""}</td>
                    <td className="nowrap">{e.name}</td>
                    <td className="nowrap">
                      <StatusLine status={e.status} />
                    </td>
                    <td className="r nowrap">{ms(e.durationMs)}</td>
                    <td className="prose">{e.detail ?? "-"}</td>
                  </tr>
                )),
              )}
              {trace.length === 0 && (
                <tr className="norow">
                  <td colSpan={5} className="muted">
                    Zatím žádné záznamy.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}
