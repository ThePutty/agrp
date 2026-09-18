import { Bar, Panel, pct } from "./ui";
import type { OutcomeBreakdown, OutcomeCategory, OutcomeStats } from "@/lib/types";

const CATEGORY_LABEL: Record<OutcomeCategory, string> = {
  GRANTED: "Vyhověno",
  PARTIALLY_GRANTED: "Částečně",
  DISMISSED: "Zamítnuto",
  OTHER: "Jiné",
};

function BreakdownTable({
  title,
  rows,
}: {
  title: string;
  rows: OutcomeBreakdown[];
}) {
  if (rows.length === 0) return null;
  return (
    <div>
      <p className="label" style={{ marginBottom: 6 }}>
        {title}
      </p>
      <table className="tbl">
        <thead>
          <tr>
            <th>Skupina</th>
            <th className="r">n</th>
            <th>Podíl</th>
            <th className="r">%</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.label}>
              <td>{r.label}</td>
              <td className="r">{r.sampleSize}</td>
              <td style={{ width: "40%" }}>
                <Bar value={r.grantedShare} />
              </td>
              <td className="r nowrap">{pct(r.grantedShare)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function OutcomeChart({
  outcome,
  no = "06",
}: {
  outcome: OutcomeStats;
  no?: string;
}) {
  const max = Math.max(1, ...outcome.buckets.map((b) => b.count));

  return (
    <Panel
      no={no}
      title="Statistika výsledků"
      subtitle="Jak podobné případy dopadly ve vzorku judikatury"
      help="Co to je? Kód vezme 30 rozhodnutí nejpodobnějších vaší otázce a spočítá, jak dopadla (pole caseResultType z justice.cz): vyhověno = žalobce uspěl, zamítnuto = neuspěl, částečně = soud přiznal jen část nároku. Rozpad podle úrovně soudu a roku. Proč tomu věřit? Čísla nepočítá AI, ale SQL nad reálnými rozhodnutími. Proč opatrně? Vzorek je malý a podobnost je významová, ne právní; není to předpověď vašeho sporu."
      right={<span>VZOREK {outcome.sampleSize}</span>}
    >
      <div style={{ display: "grid", gap: 16 }}>
        <div>
          <p className="label" style={{ marginBottom: 6 }}>
            Výsledek řízení
          </p>
          <table className="tbl">
            <thead>
              <tr>
                <th>Kategorie</th>
                <th className="r">Počet</th>
                <th>Podíl</th>
                <th className="r">%</th>
              </tr>
            </thead>
            <tbody>
              {outcome.buckets.map((b) => (
                <tr key={b.category}>
                  <td>{CATEGORY_LABEL[b.category]}</td>
                  <td className="r">{b.count}</td>
                  <td style={{ width: "50%" }}>
                    <Bar value={b.count} max={max} />
                  </td>
                  <td className="r nowrap">{pct(b.share)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="grid-rules cols-2" style={{ background: "transparent", border: 0, gap: 16 }}>
          <BreakdownTable
            title="Podle stupně soudu (podíl vyhověno)"
            rows={outcome.byCourtLevel}
          />
          <BreakdownTable title="Podle roku (podíl vyhověno)" rows={outcome.byYear} />
        </div>

        {outcome.note && (
          <p className="label" style={{ textTransform: "none", letterSpacing: 0 }}>
            {outcome.note}
          </p>
        )}
      </div>
    </Panel>
  );
}
