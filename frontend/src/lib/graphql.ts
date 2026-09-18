import {
  MOCK_CORPUS_STATS,
  MOCK_DB_INSPECT,
  MOCK_GRAPH_DIAGRAM,
  mockIngest,
  mockResearch,
} from "./mockData";
import type { CorpusStats, DbInspect, Decision, Ingest, Research } from "./types";

export const GRAPHQL_URL =
  process.env.NEXT_PUBLIC_GRAPHQL_URL ?? "http://localhost:8081/graphql";

/** NEXT_PUBLIC_MOCK=1 → žádné síťové volání, vrací se lokální demo data. */
export const MOCK_MODE = process.env.NEXT_PUBLIC_MOCK === "1";

interface GraphQLResponse<T> {
  data?: T;
  errors?: { message: string }[];
}

/** Minimalistický GraphQL klient - jeden fetch POST, bez Apolla. */
export async function gql<T>(
  query: string,
  variables: Record<string, unknown> = {},
): Promise<T> {
  if (MOCK_MODE) return mockGql<T>(query, variables);

  let res: Response;
  try {
    res = await fetch(GRAPHQL_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query, variables }),
      cache: "no-store",
    });
  } catch {
    throw new Error(
      `Backend není dostupný na ${GRAPHQL_URL}. Spusťte backend nebo použijte NEXT_PUBLIC_MOCK=1.`,
    );
  }
  if (!res.ok) {
    throw new Error(`GraphQL HTTP ${res.status} ${res.statusText}`);
  }
  const json = (await res.json()) as GraphQLResponse<T>;
  if (json.errors?.length) {
    throw new Error(json.errors.map((e) => e.message).join("; "));
  }
  if (!json.data) throw new Error("GraphQL odpověď neobsahuje data.");
  return json.data;
}

// ---------------------------------------------------------------- fragmenty

const DECISION_FIELDS = `
  id ecli caseNumber court courtCode decidedOn publishedOn subject
  keywords provisions resultTypes sourceUrl
`;

const STEP_FIELDS = `name status durationMs attempts detail`;
const TRACE_FIELDS = `layer name status durationMs detail`;

const RESEARCH_FIELDS = `
  id
  question
  status
  steps { ${STEP_FIELDS} }
  analysis { legalConcepts provisions searchQueries }
  hits {
    rank snippet vectorScore textScore fusedScore
    decision { ${DECISION_FIELDS} }
  }
  answer {
    forArguments { claim reasoning citationIndexes }
    againstArguments { claim reasoning citationIndexes }
    verdict strongerSide
    citations { index decisionId caseNumber quote verified reason side }
    verifiedCount unverifiedCount attempts model mock
  }
  outcome {
    sampleSize note
    buckets { category count share }
    byCourtLevel { label sampleSize grantedShare }
    byYear { label sampleSize grantedShare }
  }
  graph {
    mermaid
    nodes { name status startedAt durationMs attempt detail }
  }
  trace { ${TRACE_FIELDS} }
`;

const INGEST_FIELDS = `
  id status requested fetched embedded failed
  steps { ${STEP_FIELDS} }
  trace { ${TRACE_FIELDS} }
`;

// ----------------------------------------------------------------- operace

const ASK = `mutation Ask($question: String!) { ask(question: $question) { ${RESEARCH_FIELDS} } }`;
const RESEARCH = `query ResearchById($id: ID!) { research(id: $id) { ${RESEARCH_FIELDS} } }`;
const START_INGEST = `mutation StartIngest($from: String!, $to: String!, $limit: Int) {
  startIngest(from: $from, to: $to, limit: $limit) { ${INGEST_FIELDS} }
}`;
const INGEST = `query IngestById($id: ID!) { ingest(id: $id) { ${INGEST_FIELDS} } }`;
const CORPUS_STATS = `query CorpusStats { corpusStats { decisions chunks courts from to } }`;
const DECISION = `query DecisionById($id: ID!) {
  decision(id: $id) { ${DECISION_FIELDS} verdictText justificationText }
}`;
const GRAPH_DIAGRAM = `query GraphDiagram { graphDiagram }`;
const DB_INSPECT = `query DbInspect($decisionIds: [ID!], $limit: Int) {
  dbInspect(decisionIds: $decisionIds, limit: $limit) {
    embeddingDims
    tables { name rows totalSize indexes { name kind size } }
    sections { section chunks }
    chunkRows {
      id decisionId caseNumber seq section content embeddingPrefix embeddingNorm tsvPrefix
    }
  }
}`;

export async function ask(question: string): Promise<Research> {
  const d = await gql<{ ask: Research }>(ASK, { question });
  return d.ask;
}

export async function fetchResearch(id: string): Promise<Research | null> {
  const d = await gql<{ research: Research | null }>(RESEARCH, { id });
  return d.research;
}

export async function startIngest(
  from: string,
  to: string,
  limit: number,
): Promise<Ingest> {
  const d = await gql<{ startIngest: Ingest }>(START_INGEST, { from, to, limit });
  return d.startIngest;
}

export async function fetchIngest(id: string): Promise<Ingest | null> {
  const d = await gql<{ ingest: Ingest | null }>(INGEST, { id });
  return d.ingest;
}

export async function fetchCorpusStats(): Promise<CorpusStats> {
  const d = await gql<{ corpusStats: CorpusStats }>(CORPUS_STATS);
  return d.corpusStats;
}

export async function fetchDecision(id: string): Promise<Decision | null> {
  const d = await gql<{ decision: Decision | null }>(DECISION, { id });
  return d.decision;
}

export async function fetchGraphDiagram(): Promise<string> {
  const d = await gql<{ graphDiagram: string }>(GRAPH_DIAGRAM);
  return d.graphDiagram;
}

export async function fetchDbInspect(
  decisionIds: string[] = [],
  limit = 8,
): Promise<DbInspect> {
  const d = await gql<{ dbInspect: DbInspect }>(DB_INSPECT, { decisionIds, limit });
  return d.dbInspect;
}

// -------------------------------------------------------------- mock režim

interface MockJob {
  phase: number;
  question: string;
  requested: number;
}

const mockJobs = new Map<string, MockJob>();
let mockSeq = 0;

function delay<T>(value: T, ms = 220): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

async function mockGql<T>(query: string, variables: Record<string, unknown>): Promise<T> {
  if (query.includes("mutation Ask")) {
    const id = `mock-research-${++mockSeq}`;
    const question = String(variables.question ?? "");
    mockJobs.set(id, { phase: 0, question, requested: 0 });
    return delay({ ask: mockResearch(id, question, 0) } as T);
  }
  if (query.includes("query ResearchById")) {
    const id = String(variables.id);
    const job = mockJobs.get(id) ?? { phase: 0, question: "", requested: 0 };
    job.phase = Math.min(job.phase + 1, 6);
    mockJobs.set(id, job);
    return delay({ research: mockResearch(id, job.question, job.phase) } as T);
  }
  if (query.includes("mutation StartIngest")) {
    const id = `mock-ingest-${++mockSeq}`;
    const requested = Number(variables.limit ?? 300);
    mockJobs.set(id, { phase: 0, question: "", requested });
    return delay({ startIngest: mockIngest(id, requested, 0) } as T);
  }
  if (query.includes("query IngestById")) {
    const id = String(variables.id);
    const job = mockJobs.get(id) ?? { phase: 0, question: "", requested: 300 };
    job.phase = Math.min(job.phase + 1, 4);
    mockJobs.set(id, job);
    return delay({ ingest: mockIngest(id, job.requested, job.phase) } as T);
  }
  if (query.includes("query CorpusStats")) {
    return delay({ corpusStats: MOCK_CORPUS_STATS } as T);
  }
  if (query.includes("query DecisionById")) {
    const id = String(variables.id);
    const hit = mockResearch("mock", "", 6).hits.find((h) => h.decision.id === id);
    return delay({ decision: hit ? hit.decision : null } as T);
  }
  if (query.includes("query GraphDiagram")) {
    return delay({ graphDiagram: MOCK_GRAPH_DIAGRAM } as T);
  }
  if (query.includes("query DbInspect")) {
    return delay({ dbInspect: MOCK_DB_INSPECT } as T);
  }
  throw new Error("Mock režim: neznámá operace.");
}
