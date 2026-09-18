"use client";

import { Help } from "./ui";

import type { Answer, Argument, Citation } from "@/lib/types";

function CitationRef({
  index,
  citation,
  onSelect,
}: {
  index: number;
  citation?: Citation;
  onSelect: (c: Citation) => void;
}) {
  const verified = citation?.verified ?? false;
  return (
    <button
      type="button"
      title={
        citation
          ? `${citation.caseNumber} - ${verified ? "ověřeno" : "neověřeno"}`
          : "citace nenalezena"
      }
      onClick={() => citation && onSelect(citation)}
      className="btn"
      style={{
        padding: "0 4px",
        fontSize: 11,
        letterSpacing: 0,
        color: citation && !verified ? "var(--red)" : undefined,
        borderColor: citation && !verified ? "var(--red)" : undefined,
      }}
    >
      [{index}]
    </button>
  );
}

function ArgumentEntry({
  code,
  argument,
  citations,
  onSelect,
}: {
  code: string;
  argument: Argument;
  citations: Citation[];
  onSelect: (c: Citation) => void;
}) {
  return (
    <li style={{ display: "grid", gap: 4, paddingBottom: 10, marginBottom: 10, borderBottom: "1px solid var(--ink)" }}>
      <p style={{ fontWeight: 700, textTransform: "none", letterSpacing: 0 }}>
        <span className="muted">{code} · </span>
        {argument.claim}
      </p>
      <p className="prose">{argument.reasoning}</p>
      {argument.citationIndexes.length > 0 && (
        <p style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 6 }}>
          <span className="label">Opora:</span>
          {argument.citationIndexes.map((i) => (
            <CitationRef
              key={i}
              index={i}
              citation={citations.find((c) => c.index === i)}
              onSelect={onSelect}
            />
          ))}
        </p>
      )}
    </li>
  );
}

export default function ArgumentColumns({
  answer,
  onSelectCitation,
  no = "07",
}: {
  answer: Answer;
  onSelectCitation: (c: Citation) => void;
  no?: string;
}) {
  const columns = [
    { title: "Pro klienta", prefix: "A", args: answer.forArguments },
    { title: "Protistrana", prefix: "B", args: answer.againstArguments },
  ];

  return (
    <section className="box">
      <header className="box-head">
        <h2>
          <span className="muted">{no} · </span>Advocatus Diaboli
          <Help tip="Proč dvě strany? AI dostane stejných 8 rozhodnutí dvakrát: jednou v roli zástupce klienta, jednou protistrany. Vidíte tak dopředu, čím bude druhá strana argumentovat. Každý argument má citace [n]; červené [n] znamená citaci, kterou kód neověřil. Obě strany argumentují pouze z nalezených rozhodnutí." />
        </h2>
        <div className="meta">
          <span>
            {answer.forArguments.length} PRO / {answer.againstArguments.length} PROTI
          </span>
        </div>
      </header>
      <div className="grid-rules cols-2" style={{ border: 0, borderTop: "1px solid var(--ink)" }}>
        {columns.map((col) => (
          <div key={col.prefix}>
            <p className="box-sub" style={{ fontWeight: 700, color: "var(--ink)" }}>
              {col.title}
            </p>
            <div className="box-body">
              <ol style={{ listStyle: "none", margin: 0, padding: 0 }}>
                {col.args.map((a, i) => (
                  <ArgumentEntry
                    key={`${col.prefix}${i}`}
                    code={`${col.prefix}${i + 1}`}
                    argument={a}
                    citations={answer.citations}
                    onSelect={onSelectCitation}
                  />
                ))}
                {col.args.length === 0 && <li className="muted">Žádné argumenty.</li>}
              </ol>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
