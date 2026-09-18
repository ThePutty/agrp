import { Help } from "./ui";
import type { Answer, Side } from "@/lib/types";

const SIDE_LABEL: Record<Side, string> = {
  FOR: "FOR · KLIENT",
  AGAINST: "AGAINST · PROTISTRANA",
  BALANCED: "BALANCED · VYROVNANÉ",
};

export default function VerdictCard({
  answer,
  no = "08",
}: {
  answer: Answer;
  no?: string;
}) {
  const total = answer.verifiedCount + answer.unverifiedCount;
  const allOk = answer.unverifiedCount === 0;

  return (
    <section className="box">
      <header className="box-head">
        <h2>
          <span className="muted">{no} · </span>Verdikt · Soudce
          <Help tip="Co je verdikt? Třetí role AI, „soudce“: přečte argumenty obou stran a statistiku výsledků a shrne, která linie je silnější a proč. Statistiku jen komentuje, nepřepočítává. Není to rozhodnutí soudu ani právní rada, jen shrnutí nalezené judikatury." />
        </h2>
        <div className="meta">
          <span className={allOk ? undefined : "red"}>
            {allOk ? "■" : "✕"} {answer.verifiedCount}/{total} CITACÍ OVĚŘENO
          </span>
        </div>
      </header>

      <table className="kv">
        <tbody>
          <tr>
            <th scope="row">Silnější strana</th>
            <td style={{ fontWeight: 700 }}>{SIDE_LABEL[answer.strongerSide]}</td>
          </tr>
          <tr>
            <th scope="row">Závěr</th>
            <td className="prose">{answer.verdict}</td>
          </tr>
          <tr>
            <th scope="row">Model</th>
            <td>{answer.model ?? "neznámý"}</td>
          </tr>
          <tr>
            <th scope="row">Pokusů</th>
            <td>{answer.attempts}</td>
          </tr>
          <tr>
            <th scope="row">Režim</th>
            <td>{answer.mock ? "MOCK ODPOVĚĎ" : "LIVE"}</td>
          </tr>
        </tbody>
      </table>
    </section>
  );
}
