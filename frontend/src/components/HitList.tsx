"use client";

import Highlight from "./Highlight";
import { Bar, Panel, Help } from "./ui";
import type { Hit } from "@/lib/types";

function ScoreCell({ value }: { value: number | null }) {
  if (value == null) return <td className="r muted">-</td>;
  return (
    <td className="r nowrap">
      <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
        <Bar value={value} />
        {value.toFixed(2)}
      </span>
    </td>
  );
}

export default function HitList({
  hits,
  terms,
  onSelect,
  no = "05",
}: {
  hits: Hit[];
  terms: string[];
  onSelect: (decisionId: string) => void;
  no?: string;
}) {
  return (
    <Panel
      no={no}
      title="Nalezená rozhodnutí"
      subtitle="Hybridní vyhledávání (vektory + fulltext), fúze RRF"
      help="Jak se hledá? Dvěma způsoby zároveň: podle významu (vektory z modelu bge-m3 najdou i jinak formulované texty) a podle slov (fulltext). Obě pořadí sloučí algoritmus RRF a přidá bonus za shodný §. Vybere se 8 nejlepších rozhodnutí; jen z těch smí AI citovat. Pořadí určuje kód, ne AI."
      flush
      right={<span>{hits.length} ZÁSAHŮ</span>}
    >
      <div className="tbl-wrap">
        <table className="tbl">
          <thead>
            <tr>
              <th className="r">#</th>
              <th>Sp. zn.</th>
              <th>Soud</th>
              <th className="nowrap">Datum</th>
              <th>Úryvek</th>
              <th className="r">
                Význam<Help tip="Významová podobnost (embedding, pgvector): najde i rozhodnutí, která používají jiná slova se stejným významem. 0-1, vyšší = podobnější." />
              </th>
              <th className="r">
                Slova<Help tip="Shoda slov (PostgreSQL fulltext): rozhodnutí obsahuje přesně hledaná slova. Vyšší = víc shod." />
              </th>
              <th className="r">
                Skóre<Help tip="Výsledné pořadí: obě metody sloučené algoritmem Reciprocal Rank Fusion + bonus za shodný §. Počítá kód, ne AI." />
              </th>
            </tr>
          </thead>
          <tbody>
            {hits.map((hit) => (
              <tr
                key={`${hit.rank}-${hit.decision.id}`}
                onClick={() => onSelect(hit.decision.id)}
                style={{ cursor: "pointer" }}
              >
                <td className="r">{hit.rank}</td>
                <td className="nowrap">
                  <u>{hit.decision.caseNumber}</u>
                </td>
                <td>
                  {hit.decision.court}
                  {hit.decision.subject && (
                    <span className="muted"> · {hit.decision.subject}</span>
                  )}
                </td>
                <td className="nowrap">{hit.decision.decidedOn ?? "-"}</td>
                <td className="prose" style={{ minWidth: 280 }}>
                  <Highlight text={hit.snippet} terms={terms} />
                  {hit.decision.provisions.length > 0 && (
                    <span className="label" style={{ display: "block", marginTop: 4 }}>
                      {hit.decision.provisions.join(" · ")}
                    </span>
                  )}
                </td>
                <ScoreCell value={hit.vectorScore} />
                <ScoreCell value={hit.textScore} />
                <ScoreCell value={hit.fusedScore} />
              </tr>
            ))}
            {hits.length === 0 && (
              <tr className="norow">
                <td colSpan={8} className="muted">
                  Žádná rozhodnutí.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </Panel>
  );
}
