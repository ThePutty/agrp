"use client";

import { useMemo } from "react";
import Mermaid from "./Mermaid";
import { Help, Panel, StatusLine, ms } from "./ui";
import type { GraphNode, GraphView, StepStatus } from "@/lib/types";
import { PALETTE, useTheme, type Theme } from "@/lib/theme";

const CLASS_BY_STATUS: Record<StepStatus, string> = {
  RUNNING: "running",
  DONE: "done",
  FAILED: "failed",
  PENDING: "pending",
  SKIPPED: "skipped",
};

/** Monochromatické stavy: běží = inkoust, hotovo = 2px obrys, chyba = červený obrys. */
function classDefs(theme: Theme): string {
  const c = PALETTE[theme];
  return [
    `  classDef running fill:${c.ink},stroke:${c.ink},stroke-width:1px,color:${c.paper};`,
    `  classDef done fill:${c.box},stroke:${c.ink},stroke-width:2px,color:${c.ink};`,
    `  classDef failed fill:${c.box},stroke:${c.red},stroke-width:2px,color:${c.red};`,
    `  classDef pending fill:${c.box},stroke:${c.ink},stroke-width:1px,color:${c.muted};`,
    `  classDef skipped fill:${c.box},stroke:${c.muted},stroke-width:1px,stroke-dasharray:4 3,color:${c.muted};`,
  ].join("\n");
}

/** Co jednotlivé uzly grafu dělají, jazykem pro začátečníka. Klíč = id uzlu v Mermaid diagramu. */
export const NODE_HELP: Record<string, string> = {
  analyzeQuestion:
    "Analýza dotazu (AI). Z vašeho popisu případu vytáhne právní pojmy, dotčené paragrafy a 2-4 vyhledávací dotazy. Jediný krok hledání, který závisí na jazykovém modelu.",
  retrieve:
    "Hybridní hledání (kód). Vyhledávací dotazy převede na vektory (Ollama) a hledá v databázi podle významu i podle slov; pořadí sloučí RRF. Výstup: 8 nejlepších rozhodnutí + 30 podobných pro statistiku.",
  argueFor:
    "Argumenty pro klienta (AI). Model v roli zástupce klienta sestaví 2-4 argumenty a ke každému doslovné citace, výhradně z nalezených rozhodnutí. Běží souběžně s argueAgainst a outcomeStats.",
  argueAgainst:
    "Argumenty protistrany (AI). Stejný postup, opačná role: co proti vám vytáhne druhá strana.",
  outcomeStats:
    "Statistika výsledků (kód, bez AI). Z 30 podobných rozhodnutí spočítá podíl vyhověno / částečně / zamítnuto, rozpad podle soudu a roku.",
  judge:
    "Verdikt (AI, role soudce). Přečte obě argumentační linie a statistiku, shrne, která strana je silnější a proč. Statistiku nepřepočítává.",
  verifyCitations:
    "Ověření citací (kód). Každá citace musí odkazovat na nalezené rozhodnutí a úryvek musí v jeho textu doslova existovat. Neověřené citace jdou zpět k přepsání (max. 2 pokusy).",
  reargue:
    "Oprava argumentů (AI). Spustí se jen když ověření selhalo: přepíše se pouze strana s neověřenou citací, pak znovu verdikt a ověření.",
};

const ESCAPE_RE = /[.*+?^${}()|[\]\\]/g;

function mentions(diagram: string, name: string) {
  const escaped = name.replace(ESCAPE_RE, "\\$&");
  return new RegExp(`(^|[^A-Za-z0-9_])${escaped}([^A-Za-z0-9_]|$)`, "m").test(diagram);
}

/**
 * Vezme mermaid text z backendu a připíše na konec classDef definice
 * a `class <uzel> <stav>;` řádky podle aktuálních stavů uzlů.
 * Id uzlů v diagramu se musí shodovat s `graph.nodes[].name`.
 */
export function decorateMermaid(diagram: string, nodes: GraphNode[], theme: Theme = "light"): string {
  const groups = new Map<string, string[]>();
  for (const node of nodes) {
    if (!mentions(diagram, node.name)) continue;
    const cls = CLASS_BY_STATUS[node.status];
    const list = groups.get(cls) ?? [];
    list.push(node.name);
    groups.set(cls, list);
  }
  const classLines = [...groups.entries()].map(
    ([cls, names]) => `  class ${names.join(",")} ${cls};`,
  );
  return [diagram.trimEnd(), classDefs(theme), ...classLines].join("\n");
}

export default function LiveGraph({
  graph,
  no = "03",
}: {
  graph: GraphView;
  no?: string;
}) {
  const theme = useTheme();
  const chart = useMemo(
    () => decorateMermaid(graph.mermaid, graph.nodes, theme),
    [graph.mermaid, graph.nodes, theme],
  );
  const unknown = graph.nodes.filter((n) => !mentions(graph.mermaid, n.name));

  return (
    <Panel
      no={no}
      title="Schéma · LangGraph4j · generováno z kódu"
      help="Co vidím? Živý graf AI agenta: každý obdélník je jeden krok (uzel). Černý = právě běží, dvojitý obrys = hotovo, červený = selhal. Šipky jsou pořadí kroků; argueFor, argueAgainst a outcomeStats běží souběžně. Smyčka z verifyCitations zpět do reargue znamená, že některá citace neprošla ověřením a AI ji přepisuje. Diagram není nakreslený ručně, generuje ho kód z definice grafu."
      flush
      right={
        <>
          <span>▌ BĚŽÍ</span>
          <span>■ HOTOVO</span>
          <span className="red">✕ CHYBA</span>
          <span>○ ČEKÁ</span>
        </>
      }
    >
      <div className="box-body">
        <Mermaid chart={chart} />
      </div>

      {graph.nodes.length > 0 && (
        <div className="tbl-wrap" style={{ borderTop: "1px solid var(--ink)" }}>
          <table className="tbl" style={{ border: 0 }}>
            <thead>
              <tr>
                <th>Uzel</th>
                <th>Stav</th>
                <th className="r nowrap">Trvání</th>
                <th className="r nowrap">Pokus</th>
                <th>Detail</th>
              </tr>
            </thead>
            <tbody>
              {graph.nodes.map((n) => (
                <tr key={n.name}>
                  <td className="nowrap">
                    {n.name}
                    {NODE_HELP[n.name] && <Help tip={NODE_HELP[n.name]} />}
                  </td>
                  <td className="nowrap">
                    <StatusLine status={n.status} />
                  </td>
                  <td className="r nowrap">{ms(n.durationMs)}</td>
                  <td className="r nowrap">{n.attempt ?? "-"}</td>
                  <td className="prose">{n.detail ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {unknown.length > 0 && (
        <p className="box-sub red" style={{ borderTop: "1px solid var(--ink)", borderBottom: 0 }}>
          Pozor: uzly {unknown.map((n) => n.name).join(", ")} nebyly v diagramu nalezeny -
          id uzlů v mermaid textu se musí shodovat s názvy v graph.nodes.
        </p>
      )}
    </Panel>
  );
}
