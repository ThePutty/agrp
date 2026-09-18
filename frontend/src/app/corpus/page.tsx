"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import ProgressSteps from "@/components/ProgressSteps";
import TechnicalTrace from "@/components/TechnicalTrace";
import { Panel, num } from "@/components/ui";
import { MOCK_MODE, fetchCorpusStats, fetchIngest, startIngest } from "@/lib/graphql";
import type { CorpusStats, Ingest } from "@/lib/types";

const POLL_MS = 1000;

const LINKS = [
  { href: "http://localhost:8233", label: "Temporal UI", hint: "běh workflow" },
  { href: "http://localhost:8081/api/llm/models", label: "LiteLLM /models", hint: "dostupné modely" },
  { href: "http://localhost:8081/graphiql", label: "GraphiQL", hint: "API playground" },
];

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="cell">
      <p className="label">{label}</p>
      <p style={{ fontSize: 16, fontWeight: 700, marginTop: 4 }}>{value}</p>
    </div>
  );
}

export default function CorpusPage() {
  const [stats, setStats] = useState<CorpusStats | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);
  const [from, setFrom] = useState("2024-03-01");
  const [to, setTo] = useState("2024-03-04");
  const [limit, setLimit] = useState(300);
  const [ingest, setIngest] = useState<Ingest | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const loadStats = useCallback(() => {
    fetchCorpusStats()
      .then((s) => {
        setStats(s);
        setStatsError(null);
      })
      .catch((e: unknown) =>
        setStatsError(e instanceof Error ? e.message : String(e)),
      );
  }, []);

  useEffect(() => {
    loadStats();
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [loadStats]);

  /** Dotazuje se na stav ingestu každou sekundu, dokud běží. */
  async function poll(id: string) {
    try {
      const next = await fetchIngest(id);
      if (!next) throw new Error("Ingest nebyl nalezen.");
      setIngest(next);
      if (next.status === "RUNNING") {
        timer.current = setTimeout(() => void poll(id), POLL_MS);
      } else {
        setBusy(false);
        loadStats();
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  }

  async function run() {
    if (timer.current) clearTimeout(timer.current);
    setError(null);
    setBusy(true);
    try {
      const started = await startIngest(from, to, limit);
      setIngest(started);
      if (started.status === "RUNNING") {
        timer.current = setTimeout(() => void poll(started.id), POLL_MS);
      } else {
        setBusy(false);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  }

  return (
    <>
      <section className="box">
        <header className="box-head">
          <h1 className="hero-title">Korpus judikatury</h1>
          <div className="meta">
            <span>{MOCK_MODE ? "MOCK REŽIM" : "LIVE"}</span>
          </div>
        </header>
        <div className="box-body">
          <p className="prose">
            Rozhodnutí stažená z rozhodnuti.justice.cz, rozsekaná na úseky a
            zaindexovaná jako vektory (bge-m3) i fulltext v PostgreSQL s rozšířením
            pgvector.
          </p>
          {MOCK_MODE && (
            <p className="label" style={{ marginTop: 6 }}>
              Mock režim - data jsou ukázková
            </p>
          )}
        </div>
      </section>

      {statsError && (
        <section className="box">
          <header className="box-head">
            <h2 className="red">Statistiky korpusu se nepodařilo načíst</h2>
          </header>
          <div className="box-body prose red">{statsError}</div>
        </section>
      )}

      <Panel no="01" title="Stav korpusu" flush>
        <div className="grid-rules cols-4" style={{ border: 0 }}>
          <Stat
            label="Rozhodnutí"
            value={stats ? num(stats.decisions) : "-"}
          />
          <Stat
            label="Úseky (chunks)"
            value={stats ? num(stats.chunks) : "-"}
          />
          <Stat label="Soudy" value={stats ? String(stats.courts.length) : "-"} />
          <Stat
            label="Rozsah dat"
            value={stats?.from && stats?.to ? `${stats.from} - ${stats.to}` : "-"}
          />
        </div>
      </Panel>

      {stats && stats.courts.length > 0 && (
        <Panel no="02" title="Zastoupené soudy" flush>
          <table className="kv">
            <tbody>
              {stats.courts.map((c, i) => (
                <tr key={c}>
                  <th scope="row">{String(i + 1).padStart(2, "0")}</th>
                  <td>{c}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      )}

      <Panel
        no="03"
        title="Ingest"
        subtitle="Běží jako Temporal workflow - sledujte v Temporal UI: http://localhost:8233"
        right={
          <span className={busy ? undefined : "muted"}>
            {busy ? (
              <>
                BĚŽÍ<span className="cursor"> ▌</span>
              </>
            ) : (
              "IDLE"
            )}
          </span>
        }
      >
        <div
          style={{
            display: "flex",
            flexWrap: "wrap",
            alignItems: "flex-end",
            gap: 16,
          }}
        >
          <label className="field">
            <span className="label">Od</span>
            <input
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              className="inp"
              style={{ width: 170 }}
            />
          </label>
          <label className="field">
            <span className="label">Do</span>
            <input
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              className="inp"
              style={{ width: 170 }}
            />
          </label>
          <label className="field">
            <span className="label">Limit</span>
            <input
              type="number"
              min={1}
              max={5000}
              value={limit}
              onChange={(e) => setLimit(Number(e.target.value))}
              className="inp"
              style={{ width: 110 }}
            />
          </label>
          <button
            type="button"
            onClick={() => void run()}
            disabled={busy}
            className="btn btn-primary"
          >
            {busy ? "Ingest běží…" : "Spustit ingest"}
          </button>
          <a
            href="http://localhost:8233"
            target="_blank"
            rel="noreferrer"
            className="btn"
          >
            Temporal UI ↗
          </a>
        </div>

        {error && (
          <p className="red" style={{ marginTop: 12 }}>
            {error}
          </p>
        )}

        {ingest && (
          <div style={{ display: "grid", gap: 16, marginTop: 16 }}>
            <table className="tbl">
              <thead>
                <tr>
                  <th>Metrika</th>
                  <th className="r">Požadováno</th>
                  <th className="r">Staženo</th>
                  <th className="r">Embedováno</th>
                  <th className="r">Chyb</th>
                </tr>
              </thead>
              <tbody>
                <tr className="norow">
                  <td className="label">Ingest {ingest.id}</td>
                  <td className="r">{ingest.requested}</td>
                  <td className="r">{ingest.fetched}</td>
                  <td className="r">{ingest.embedded}</td>
                  <td className={`r${ingest.failed > 0 ? " red" : ""}`}>
                    {ingest.failed}
                  </td>
                </tr>
              </tbody>
            </table>
            <ProgressSteps steps={ingest.steps} status={ingest.status} no="04" />
            <TechnicalTrace trace={ingest.trace} no="05" />
          </div>
        )}
      </Panel>

      <Panel no="06" title="Nástroje" flush>
        <table className="tbl">
          <thead>
            <tr>
              <th>Nástroj</th>
              <th>Účel</th>
              <th>Adresa</th>
            </tr>
          </thead>
          <tbody>
            {LINKS.map((l) => (
              <tr key={l.href}>
                <td className="nowrap">
                  <a href={l.href} target="_blank" rel="noreferrer">
                    {l.label} ↗
                  </a>
                </td>
                <td>{l.hint}</td>
                <td className="muted">{l.href}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>
    </>
  );
}
