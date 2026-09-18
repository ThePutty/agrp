// Typy zrcadlí backend/src/main/resources/graphql/schema.graphqls

export type StepStatus = "PENDING" | "RUNNING" | "DONE" | "FAILED" | "SKIPPED";
export type JobStatus = "RUNNING" | "COMPLETED" | "FAILED";
export type Side = "FOR" | "AGAINST" | "BALANCED";
export type OutcomeCategory =
  | "GRANTED"
  | "PARTIALLY_GRANTED"
  | "DISMISSED"
  | "OTHER";

export interface Step {
  name: string;
  status: StepStatus;
  durationMs: number | null;
  attempts: number | null;
  detail: string | null;
}

export interface TraceEntry {
  layer: string;
  name: string;
  status: StepStatus;
  durationMs: number | null;
  detail: string | null;
}

export interface QueryAnalysis {
  legalConcepts: string[];
  provisions: string[];
  searchQueries: string[];
}

export interface Decision {
  id: string;
  ecli: string | null;
  caseNumber: string;
  court: string;
  courtCode: string | null;
  decidedOn: string | null;
  publishedOn: string | null;
  subject: string | null;
  keywords: string[];
  provisions: string[];
  resultTypes: string[];
  verdictText: string | null;
  justificationText: string | null;
  sourceUrl: string;
}

export interface Hit {
  rank: number;
  decision: Decision;
  snippet: string;
  vectorScore: number | null;
  textScore: number | null;
  fusedScore: number;
}

export interface Argument {
  claim: string;
  reasoning: string;
  citationIndexes: number[];
}

export interface Citation {
  index: number;
  decisionId: string;
  caseNumber: string;
  quote: string;
  verified: boolean;
  reason: string | null;
  side: Side;
}

export interface Answer {
  forArguments: Argument[];
  againstArguments: Argument[];
  verdict: string;
  strongerSide: Side;
  citations: Citation[];
  verifiedCount: number;
  unverifiedCount: number;
  attempts: number;
  model: string | null;
  mock: boolean;
}

export interface OutcomeBucket {
  category: OutcomeCategory;
  count: number;
  share: number;
}

export interface OutcomeBreakdown {
  label: string;
  sampleSize: number;
  grantedShare: number;
}

export interface OutcomeStats {
  sampleSize: number;
  buckets: OutcomeBucket[];
  byCourtLevel: OutcomeBreakdown[];
  byYear: OutcomeBreakdown[];
  note: string;
}

export interface GraphNode {
  name: string;
  status: StepStatus;
  startedAt: string | null;
  durationMs: number | null;
  attempt: number | null;
  detail: string | null;
}

export interface GraphView {
  mermaid: string;
  nodes: GraphNode[];
}

export interface Research {
  id: string;
  question: string;
  status: JobStatus;
  steps: Step[];
  analysis: QueryAnalysis | null;
  hits: Hit[];
  answer: Answer | null;
  outcome: OutcomeStats | null;
  graph: GraphView;
  trace: TraceEntry[];
}

export interface Ingest {
  id: string;
  status: JobStatus;
  requested: number;
  fetched: number;
  embedded: number;
  failed: number;
  steps: Step[];
  trace: TraceEntry[];
}

export interface CorpusStats {
  decisions: number;
  chunks: number;
  courts: string[];
  from: string | null;
  to: string | null;
}

// ---- Databáze (Technická stopa) --------------------------------------------

export interface DbIndex {
  name: string;
  kind: string;
  size: string;
}

export interface DbTable {
  name: string;
  rows: number;
  totalSize: string;
  indexes: DbIndex[];
}

export interface DbSection {
  section: string;
  chunks: number;
}

export interface DbChunkRow {
  id: string;
  decisionId: string;
  caseNumber: string;
  seq: number;
  section: string;
  content: string;
  embeddingPrefix: number[];
  embeddingNorm: number | null;
  tsvPrefix: string;
}

export interface DbInspect {
  tables: DbTable[];
  sections: DbSection[];
  embeddingDims: number;
  chunkRows: DbChunkRow[];
}
