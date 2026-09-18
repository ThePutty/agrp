"use client";

import { useRef, useState } from "react";
import { fetchCorpusStats } from "@/lib/graphql";
import type { CorpusStats } from "@/lib/types";
import { num } from "./ui";

/**
 * Vysvětlení pro uživatele: odkud jsou data, jak často se obnovují, co se s dotazem děje
 * a co systém neumí. Stav korpusu se čte živě z backendu.
 */
export default function AboutSystem() {
  const [stats, setStats] = useState<CorpusStats | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loaded = useRef(false);

  // Sekce je sbalená; stav korpusu se načte až při prvním rozbalení.
  function onToggle(e: React.SyntheticEvent<HTMLDetailsElement>) {
    if (!e.currentTarget.open || loaded.current) return;
    loaded.current = true;
    fetchCorpusStats()
      .then(setStats)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)));
  }

  const corpus = error
    ? `nepodařilo se načíst (${error})`
    : stats
      ? `${num(stats.decisions)} rozhodnutí · ${num(stats.chunks)} textových úseků · zveřejněno ${stats.from ?? "?"} až ${stats.to ?? "?"} · ${stats.courts.length} soudů`
      : "načítám…";

  return (
    <details className="box about" onToggle={onToggle}>
      <summary className="box-head about-head">
        <h2>00 · Co systém umí a co dělá</h2>
        <span className="meta about-toggle" />
      </summary>
      <div className="box-body">
        <table className="kv">
          <tbody>
            <tr>
              <th>Odkud jsou data</th>
              <td>
                Otevřená data Ministerstva spravedlnosti ČR (rozhodnuti.justice.cz): anonymizovaná
                rozhodnutí okresních, krajských a vrchních soudů, licence CC BY 4.0. Systém
                neprohledává internet ani jiné weby, jen vlastní databázi.
              </td>
            </tr>
            <tr>
              <th>Jak často se obnovují</th>
              <td>
                Neobnovují se automaticky. Databáze se plní ručně na stránce{" "}
                <a href="/corpus">Korpus</a> tlačítkem <i>Spustit ingest</i> za zvolené období
                zveřejnění. Každé rozhodnutí se stáhne, rozdělí na úseky a k nim se lokálně
                (Ollama) spočítají významové vektory. Během ingestu se nic nedotazuje do AI.
              </td>
            </tr>
            <tr>
              <th>Co je v databázi teď</th>
              <td>{corpus}</td>
            </tr>
            <tr>
              <th>Co se stane s dotazem</th>
              <td>
                1. AI přeloží popis případu na právní pojmy, paragrafy a vyhledávací dotazy.
                2. Kód hledá v databázi dvěma způsoby: podle významu (vektory) a podle slov
                (fulltext); výsledky sloučí a vybere 8 nejrelevantnějších rozhodnutí + 30 podobných
                pro statistiku. 3. AI z těchto 8 rozhodnutí sestaví argumenty pro klienta i
                protistranu a soudcovský verdikt; kód spočítá, jak podobné spory dopadly.
                4. Kód ověří každou citaci: rozhodnutí musí být mezi nalezenými a úryvek musí
                v jeho textu doslova existovat. Neověřená citace se označí červeně a AI dostane
                jednu šanci na opravu.
              </td>
            </tr>
            <tr>
              <th>Kde probíhá embedování</th>
              <td>
                Embedding = převod textu na vektor 1024 čísel zachycující význam (model bge-m3,
                běží lokálně v Ollamě přes LiteLLM, data neopouštějí stroj). Děje se na dvou místech:
                (1) při ingestu se každý úsek rozhodnutí převede na vektor a uloží do PostgreSQL
                (pgvector, index HNSW); (2) při dotazu se stejným modelem převedou vyhledávací dotazy
                a databáze najde nejbližší úseky (kosinová vzdálenost). Oba kroky musí používat týž
                model, jinak vektory nejsou srovnatelné. Chat model embeddingy nedělá.
              </td>
            </tr>
            <tr>
              <th>Co rozhoduje AI a co kód</th>
              <td>
                AI jen formuluje (analýza dotazu, argumenty, verdikt). Pořadí výsledků, statistiku
                výsledků a existenci citací rozhoduje deterministický kód. Bez klíče k modelu běží
                systém v mock režimu se šablonovými odpověďmi.
              </td>
            </tr>
            <tr>
              <th>Co systém neumí</th>
              <td>
                Témata mimo nahraný korpus najde jen přibližně (vždy vrátí nejbližší dostupná
                rozhodnutí). Nezná rozhodnutí Nejvyššího, Nejvyššího správního ani Ústavního soudu
                (nemají otevřené API). Nesleduje, zda bylo rozhodnutí později změněno. Výstup není
                právní rada.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </details>
  );
}
