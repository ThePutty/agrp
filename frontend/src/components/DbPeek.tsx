"use client";

import { useEffect, useState } from "react";
import { fetchDbInspect } from "@/lib/graphql";
import type { DbInspect } from "@/lib/types";

/**
 * Pohled do PostgreSQL/pgvector: co fyzicky leží v tabulkách, jak vypadá řádek `chunk`
 * (text, prvních 8 čísel z 1024-dimenzionálního vektoru, tsvector pro fulltext).
 * Vzorek řádků pochází přednostně z rozhodnutí, která aktuální dotaz našel.
 */
export default function DbPeek({ decisionIds = [] }: { decisionIds?: string[] }) {
  const key = decisionIds.join(",");
  // Stav nese klíč dotazu, pro který platí; načítání = klíč se liší. Žádný setState přímo v efektu.
  const [result, setResult] = useState<{ key: string; data: DbInspect | null; error: string | null }>({
    key: "",
    data: null,
    error: null,
  });

  useEffect(() => {
    let alive = true;
    fetchDbInspect(decisionIds, 8)
      .then((data) => alive && setResult({ key, data, error: null }))
      .catch((e: unknown) =>
        alive && setResult({ key, data: null, error: e instanceof Error ? e.message : String(e) }),
      );
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  const loaded = result.key === key ? result : null;
  if (loaded?.error) return <p className="muted db-note">Databáze nedostupná: {loaded.error}</p>;
  const data = loaded?.data;
  if (!data) return <p className="muted db-note">Načítám z PostgreSQL…</p>;

  const fromHits = decisionIds.length > 0;

  return (
    <div className="db-peek">
      <div className="tbl-wrap">
        <table className="tbl">
          <thead>
            <tr>
              <th>Tabulka</th>
              <th className="r nowrap">Řádků</th>
              <th className="r nowrap">Velikost</th>
              <th>Indexy</th>
            </tr>
          </thead>
          <tbody>
            {data.tables.map((t) => (
              <tr key={t.name}>
                <td className="nowrap label">
                  <code>{t.name}</code>
                </td>
                <td className="r nowrap">{t.rows.toLocaleString("cs-CZ")}</td>
                <td className="r nowrap">{t.totalSize}</td>
                <td className="prose">
                  {t.indexes.map((i) => (
                    <span key={i.name} className="db-index">
                      <code>{i.name}</code> <span className="muted">{i.kind} · {i.size}</span>
                    </span>
                  ))}
                </td>
              </tr>
            ))}
            <tr className="norow">
              <td colSpan={4} className="muted">
                Sloupec <code>chunk.embedding</code> je <code>vector({data.embeddingDims})</code>{" "}
                (bge-m3, kosinová vzdálenost přes HNSW). Sloupec <code>chunk.tsv</code> je generovaný{" "}
                <code>tsvector</code> bez diakritiky pro fulltext (GIN). Sekce:{" "}
                {data.sections.map((s) => `${s.section} ${s.chunks.toLocaleString("cs-CZ")}`).join(", ")}.
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p className="box-sub db-note">
        {fromHits
          ? `Řádky tabulky chunk pro rozhodnutí nalezená tímto dotazem (jeden úsek na rozhodnutí):`
          : `Vzorek řádků tabulky chunk (jeden úsek na rozhodnutí):`}
      </p>

      <div className="tbl-wrap">
        <table className="tbl">
          <thead>
            <tr>
              <th className="nowrap">id</th>
              <th className="nowrap">Rozhodnutí</th>
              <th className="nowrap">Sekce · seq</th>
              <th>content</th>
              <th className="nowrap">embedding (prvních 8 z {data.embeddingDims})</th>
              <th>tsv</th>
            </tr>
          </thead>
          <tbody>
            {data.chunkRows.map((r) => (
              <tr key={r.id}>
                <td className="nowrap muted">{r.id}</td>
                <td className="nowrap">{r.caseNumber}</td>
                <td className="nowrap muted">
                  {r.section} · {r.seq}
                </td>
                <td className="prose db-content">{r.content}…</td>
                <td className="db-vec">
                  <code>[{r.embeddingPrefix.map((v) => v.toFixed(4)).join(", ")}, …]</code>
                  {r.embeddingNorm != null && (
                    <div className="muted">‖v‖ = {r.embeddingNorm.toFixed(3)}</div>
                  )}
                </td>
                <td className="db-tsv">
                  <code>{r.tsvPrefix}…</code>
                </td>
              </tr>
            ))}
            {data.chunkRows.length === 0 && (
              <tr className="norow">
                <td colSpan={6} className="muted">
                  Tabulka chunk je prázdná - spusťte ingest na stránce Korpus.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
