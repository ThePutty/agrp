import { Panel } from "./ui";
import type { QueryAnalysis } from "@/lib/types";

export default function AnalysisPanel({
  analysis,
  no = "04",
}: {
  analysis: QueryAnalysis;
  no?: string;
}) {
  const rows: { label: string; items: string[] }[] = [
    { label: "Právní koncepty", items: analysis.legalConcepts },
    { label: "Ustanovení", items: analysis.provisions },
    { label: "Vyhledávací dotazy", items: analysis.searchQueries },
  ].filter((g) => g.items.length > 0);

  return (
    <Panel
      no={no}
      title="Analýza dotazu"
      subtitle="Co z otázky vyčetl model"
      help="K čemu to je? Váš popis případu je běžná řeč; AI z něj vytáhne právní pojmy, dotčené paragrafy a 2-4 vyhledávací dotazy, kterými se pak prohledává databáze. Jen tato část hledání závisí na AI; samotné hledání a řazení dělá kód."
      flush
      right={<span>{rows.length} POLOŽEK</span>}
    >
      <table className="kv">
        <tbody>
          {rows.map((g) => (
            <tr key={g.label}>
              <th scope="row">{g.label}</th>
              <td>{g.items.join(" · ")}</td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <th scope="row">Výsledek</th>
              <td className="muted">Model nic nevyčetl.</td>
            </tr>
          )}
        </tbody>
      </table>
    </Panel>
  );
}
