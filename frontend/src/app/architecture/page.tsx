import Mermaid from "@/components/Mermaid";
import { Panel } from "@/components/ui";

export const metadata = {
  title: "Architektura · Judikatura AI",
};

const DIAGRAM = `flowchart TD
  B[Browser] --> FE[Next.js :3000]
  FE -->|GraphQL| GQL[Spring GraphQL]
  subgraph BE[Spring Boot backend]
    GQL --> TW[Temporal Workflows]
    TW --> SR[HybridSearch · Ranker · CitationVerifier]
    TW --> AIG[LangGraph4j graf]
    AIG --> LC[LangChain4j]
  end
  TW <--> T[Temporal server]
  TW --> MSP[(rozhodnuti.justice.cz)]
  SR <--> PG[(PostgreSQL + pgvector)]
  LC --> LL[LiteLLM proxy]
  LL --> OR[(OpenRouter free LLM)]
  LL --> OL[Ollama bge-m3]`;

const TECH: { layer: string; tech: string; role: string }[] = [
  {
    layer: "Frontend",
    tech: "Next.js 16 (App Router), TypeScript, Tailwind CSS 4",
    role: "UI, dotazování GraphQL, živý graf běhu v Mermaidu",
  },
  {
    layer: "API",
    tech: "Spring Boot + Spring for GraphQL",
    role: "mutace ask / startIngest, dotazy research / ingest / corpusStats",
  },
  {
    layer: "Orchestrace",
    tech: "Temporal (workflow + activities)",
    role: "spolehlivý běh dlouhých úloh, retry, viditelnost běhu",
  },
  {
    layer: "AI graf",
    tech: "LangGraph4j",
    role: "uzly analyzeQuestion → retrieve → argueFor/argueAgainst/outcomeStats → judge → verifyCitations",
  },
  {
    layer: "LLM klient",
    tech: "LangChain4j",
    role: "promptování, strukturované výstupy, embeddingy",
  },
  {
    layer: "Model proxy",
    tech: "LiteLLM",
    role: "jednotné OpenAI-kompatibilní API pro OpenRouter i Ollamu",
  },
  {
    layer: "Modely",
    tech: "OpenRouter (free LLM), Ollama bge-m3",
    role: "generování argumentů a závěru, vektorové embeddingy",
  },
  {
    layer: "Úložiště",
    tech: "PostgreSQL + pgvector",
    role: "rozhodnutí, úseky textu, vektorový i fulltextový index",
  },
  {
    layer: "Ověřování",
    tech: "CitationVerifier",
    role: "doslovné porovnání citované věty s textem rozhodnutí v DB",
  },
  {
    layer: "Observabilita",
    tech: "Technical Trace (Java), Temporal UI",
    role: "trasování vrstev a délek kroků v aplikaci, běh workflow",
  },
  {
    layer: "Zdroj dat",
    tech: "rozhodnuti.justice.cz",
    role: "otevřená data Ministerstva spravedlnosti ČR (CC BY 4.0)",
  },
];

export default function ArchitecturePage() {
  return (
    <>
      <section className="box">
        <header className="box-head">
          <h1 className="hero-title">Architektura</h1>
          <div className="meta">
            <span>REV 0.1</span>
          </div>
        </header>
        <div className="box-body">
          <p className="prose">
            Celý tok od dotazu v prohlížeči až po ověřenou citaci ze zdrojového
            rozhodnutí.
          </p>
        </div>
      </section>

      <Panel no="01" title="Schéma systému · generováno z kódu">
        <Mermaid chart={DIAGRAM} />
      </Panel>

      <Panel no="02" title="Technologická mapa" flush right={<span>{TECH.length} VRSTEV</span>}>
        <div className="tbl-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Vrstva</th>
                <th>Technologie</th>
                <th>Role</th>
              </tr>
            </thead>
            <tbody>
              {TECH.map((t) => (
                <tr key={t.layer}>
                  <td className="nowrap" style={{ fontWeight: 700 }}>
                    {t.layer}
                  </td>
                  <td>{t.tech}</td>
                  <td className="prose">{t.role}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
    </>
  );
}
