import type {
  Argument,
  Citation,
  CorpusStats,
  DbInspect,
  Decision,
  GraphNode,
  Hit,
  Ingest,
  OutcomeStats,
  Research,
  Step,
  StepStatus,
  TraceEntry,
} from "./types";

/**
 * Lokální mock pro vývoj bez backendu (NEXT_PUBLIC_MOCK=1).
 * Research "postupuje" podle počtu dotazů - každé volání research(id) posune fázi.
 */

export const MOCK_STEP_NAMES = [
  "Analýza dotazu",
  "Vyhledání judikatury",
  "Argumenty pro",
  "Argumenty proti",
  "Statistika výsledků",
  "Ověření citací",
];

/** Uzly grafu v pořadí, v jakém doběhnou. */
const NODE_ORDER = [
  "analyzeQuestion",
  "retrieve",
  "argueFor",
  "argueAgainst",
  "outcomeStats",
  "judge",
  "verifyCitations",
];

export const MOCK_MERMAID = [
  "flowchart TD",
  "  __START__ --> analyzeQuestion",
  "  analyzeQuestion --> retrieve",
  "  retrieve --> argueFor",
  "  retrieve --> argueAgainst",
  "  retrieve --> outcomeStats",
  "  argueFor --> judge",
  "  argueAgainst --> judge",
  "  outcomeStats --> judge",
  "  judge --> verifyCitations",
  "  verifyCitations --> __END__",
].join("\n");

const MOCK_JUSTIFICATION = [
  "Soud předesílá, že bezdůvodným obohacením se podle § 2991 o. z. rozumí majetkový prospěch získaný bez spravedlivého důvodu. Plnil-li žalobce na základě smlouvy, která byla následně shledána neplatnou, jde o plnění z právního důvodu, který odpadl.",
  "Nejvyšší soud opakovaně vyslovil, že promlčecí lhůta u nároku z bezdůvodného obohacení počíná běžet okamžikem, kdy se oprávněný dozvěděl o okolnostech rozhodných pro uplatnění práva. Subjektivní promlčecí lhůta činí tři roky.",
  "Odvolací soud proto nepochybil, dovodil-li, že nárok žalobce nebyl promlčen, neboť žalobce se o neplatnosti smlouvy dozvěděl až z pravomocného rozhodnutí v jiném řízení. K závěru o odpovědnosti za škodu nepostačuje pouhá pravděpodobnost; příčinná souvislost musí být prokázána.",
].join("\n\n");

function decision(
  id: string,
  caseNumber: string,
  court: string,
  courtCode: string,
  decidedOn: string,
  subject: string,
  keywords: string[],
  provisions: string[],
): Decision {
  return {
    id,
    ecli: "ECLI:CZ:OS:2024:" + caseNumber.replace(/[^0-9]/g, ""),
    caseNumber,
    court,
    courtCode,
    decidedOn,
    publishedOn: decidedOn,
    subject,
    keywords,
    provisions,
    resultTypes: ["částečně vyhověno"],
    verdictText:
      "Žalovaný je povinen zaplatit žalobci částku 84 320 Kč s úrokem z prodlení; ve zbytku se žaloba zamítá. (" +
      caseNumber +
      ")",
    justificationText: MOCK_JUSTIFICATION,
    sourceUrl: "https://rozhodnuti.justice.cz/rozhodnuti/" + id,
  };
}

const MOCK_HITS: Hit[] = [
  {
    rank: 1,
    decision: decision(
      "d-1",
      "7 C 286/2023-31",
      "Okresní soud v Klatovech",
      "OSKT",
      "2024-03-01",
      "Bezdůvodné obohacení",
      ["bezdůvodné obohacení", "neplatnost smlouvy", "promlčení"],
      ["§ 2991 o. z.", "§ 629 o. z."],
    ),
    snippet:
      "Plnil-li žalobce na základě smlouvy, která byla následně shledána neplatnou, jde o bezdůvodné obohacení z právního důvodu, který odpadl.",
    vectorScore: 0.91,
    textScore: 0.74,
    fusedScore: 0.88,
  },
  {
    rank: 2,
    decision: decision(
      "d-2",
      "8 C 300/2023-58",
      "Okresní soud Brno-venkov",
      "OSBV",
      "2024-03-04",
      "Smlouva o úvěru",
      ["smlouva o úvěru", "úrok z prodlení", "spotřebitel"],
      ["§ 2395 o. z.", "§ 1970 o. z."],
    ),
    snippet:
      "Ujednání o úroku z prodlení ve výši přesahující obvyklou míru je ve spotřebitelských smlouvách posuzováno jako nepřiměřené.",
    vectorScore: 0.83,
    textScore: 0.62,
    fusedScore: 0.79,
  },
  {
    rank: 3,
    decision: decision(
      "d-3",
      "18 C 183/2023-44",
      "Okresní soud v Havlíčkově Brodě",
      "OSHB",
      "2024-03-04",
      "Náhrada škody",
      ["náhrada škody", "příčinná souvislost"],
      ["§ 2910 o. z."],
    ),
    snippet:
      "K závěru o odpovědnosti za škodu nepostačuje pouhá pravděpodobnost; příčinná souvislost musí být prokázána.",
    vectorScore: 0.71,
    textScore: 0.55,
    fusedScore: 0.68,
  },
];

const FOR_ARGS: Argument[] = [
  {
    claim: "Plnění z neplatné smlouvy zakládá nárok na vydání bezdůvodného obohacení.",
    reasoning:
      "Nejvyšší soud setrvale dovozuje, že odpadne-li právní důvod plnění, vzniká na straně příjemce bezdůvodné obohacení podle § 2991 o. z. a druhá strana má nárok na jeho vydání v penězích.",
    citationIndexes: [1],
  },
  {
    claim: "Promlčecí lhůta nezačala běžet dříve než rozhodnutím o neplatnosti.",
    reasoning:
      "Subjektivní tříletá lhůta počíná okamžikem, kdy se oprávněný dozvěděl o rozhodných okolnostech - zde až z pravomocného rozhodnutí v jiném řízení.",
    citationIndexes: [2],
  },
];

const AGAINST_ARGS: Argument[] = [
  {
    claim: "Nárok je promlčen, neboť žalobce o plnění bez důvodu věděl od počátku.",
    reasoning:
      "Judikatura připouští, že vědomost o rozhodných okolnostech může nastat již při samotném plnění, byl-li rozpor se zákonem zjevný.",
    citationIndexes: [3],
  },
  {
    claim: "Výše obohacení nebyla prokázána v tvrzeném rozsahu.",
    reasoning:
      "Obohacený vydává to, oč se skutečně obohatil; není-li prokázána obvyklá cena plnění, nelze nárok přiznat v plné výši.",
    citationIndexes: [4],
  },
];

const CITATIONS: Citation[] = [
  {
    index: 1,
    decisionId: "d-1",
    caseNumber: "7 C 286/2023-31",
    quote:
      "Plnil-li žalobce na základě smlouvy, která byla následně shledána neplatnou, jde o plnění z právního důvodu, který odpadl.",
    verified: true,
    reason: null,
    side: "FOR",
  },
  {
    index: 2,
    decisionId: "d-1",
    caseNumber: "7 C 286/2023-31",
    quote: "Subjektivní promlčecí lhůta činí tři roky.",
    verified: true,
    reason: null,
    side: "FOR",
  },
  {
    index: 3,
    decisionId: "d-2",
    caseNumber: "8 C 300/2023-58",
    quote:
      "Vědomost o bezdůvodnosti plnění nastává již okamžikem samotného plnění, je-li rozpor se zákonem zjevný.",
    verified: false,
    reason: "Citovaná věta nebyla nalezena v textu rozhodnutí (nejlepší shoda 0,42).",
    side: "AGAINST",
  },
  {
    index: 4,
    decisionId: "d-3",
    caseNumber: "18 C 183/2023-44",
    quote:
      "K závěru o odpovědnosti za škodu nepostačuje pouhá pravděpodobnost; příčinná souvislost musí být prokázána.",
    verified: true,
    reason: null,
    side: "AGAINST",
  },
];

const OUTCOME: OutcomeStats = {
  sampleSize: 128,
  buckets: [
    { category: "GRANTED", count: 41, share: 0.32 },
    { category: "PARTIALLY_GRANTED", count: 27, share: 0.211 },
    { category: "DISMISSED", count: 52, share: 0.406 },
    { category: "OTHER", count: 8, share: 0.063 },
  ],
  byCourtLevel: [
    { label: "Okresní soudy", sampleSize: 96, grantedShare: 0.365 },
    { label: "Krajské soudy", sampleSize: 23, grantedShare: 0.29 },
    { label: "Vrchní a Nejvyšší soud", sampleSize: 9, grantedShare: 0.217 },
  ],
  byYear: [
    { label: "2022", sampleSize: 35, grantedShare: 0.257 },
    { label: "2023", sampleSize: 48, grantedShare: 0.312 },
    { label: "2024", sampleSize: 45, grantedShare: 0.378 },
  ],
  note: "Orientační statistika ze vzorku rozhodnutí odpovídajících vyhledávacím dotazům. Nejde o reprezentativní výběr ani o právní predikci.",
};

const TRACE: TraceEntry[] = [
  {
    layer: "Temporal",
    name: "ResearchWorkflow",
    status: "DONE",
    durationMs: 8420,
    detail: "workflowId=research-mock-1",
  },
  {
    layer: "PostgreSQL/pgvector",
    name: "hybridSearch",
    status: "DONE",
    durationMs: 214,
    detail: "3 zásahy, RRF fúze",
  },
  {
    layer: "LangGraph4j",
    name: "researchGraph",
    status: "DONE",
    durationMs: 7810,
    detail: "7 uzlů, reargue přeskočeno",
  },
  {
    layer: "LangChain4j",
    name: "ChatModel.generate",
    status: "DONE",
    durationMs: 5320,
    detail: "4 volání",
  },
  {
    layer: "LiteLLM",
    name: "proxy /chat/completions",
    status: "DONE",
    durationMs: 5180,
    detail: "model=openrouter/free",
  },
  {
    layer: "OpenRouter/Ollama",
    name: "bge-m3 embed",
    status: "DONE",
    durationMs: 132,
    detail: "1024 dim",
  },
  {
    layer: "CitationVerifier",
    name: "verify",
    status: "DONE",
    durationMs: 46,
    detail: "3 ze 4 ověřeno",
  },
];

const STEP_DETAILS = [
  "4 koncepty, 3 dotazy",
  "3 rozhodnutí",
  "2 argumenty",
  "2 argumenty",
  "vzorek 128",
  "3 ze 4 ověřeno",
];

function stepAt(index: number, phase: number): Step {
  const status: StepStatus = phase > index ? "DONE" : phase === index ? "RUNNING" : "PENDING";
  return {
    name: MOCK_STEP_NAMES[index],
    status,
    durationMs: status === "DONE" ? 600 + index * 430 : null,
    attempts: status === "PENDING" ? null : 1,
    detail:
      status === "DONE" ? STEP_DETAILS[index] : status === "RUNNING" ? "probíhá…" : null,
  };
}

/** Mapování uzel -> fáze, ve které uzel běží. */
const NODE_PHASE = [0, 1, 2, 3, 4, 5, 5];

function nodeAt(index: number, phase: number): GraphNode {
  const p = NODE_PHASE[index];
  const status: StepStatus = phase > p ? "DONE" : phase === p ? "RUNNING" : "PENDING";
  return {
    name: NODE_ORDER[index],
    status,
    startedAt: status === "PENDING" ? null : "2024-03-05T10:00:00Z",
    durationMs: status === "DONE" ? 400 + index * 260 : null,
    attempt: status === "PENDING" ? null : 1,
    detail: status === "DONE" ? "ok" : status === "RUNNING" ? "probíhá…" : null,
  };
}

/** Sestaví mock Research ve fázi `phase` (0..6). */
export function mockResearch(id: string, question: string, phase: number): Research {
  const done = phase >= MOCK_STEP_NAMES.length;
  return {
    id,
    question,
    status: done ? "COMPLETED" : "RUNNING",
    steps: MOCK_STEP_NAMES.map((_, i) => stepAt(i, phase)),
    analysis:
      phase >= 1
        ? {
            legalConcepts: [
              "bezdůvodné obohacení",
              "neplatnost smlouvy",
              "promlčení",
              "vydání plnění",
            ],
            provisions: ["§ 2991 o. z.", "§ 629 o. z.", "§ 2993 o. z."],
            searchQueries: [
              "bezdůvodné obohacení plnění z neplatné smlouvy",
              "promlčení nároku na vydání bezdůvodného obohacení",
              "odpadnutí právního důvodu plnění",
            ],
          }
        : null,
    hits: phase >= 2 ? MOCK_HITS : [],
    answer: done
      ? {
          forArguments: FOR_ARGS,
          againstArguments: AGAINST_ARGS,
          verdict:
            "Nárok na vydání bezdůvodného obohacení je s vysokou pravděpodobností důvodný; klíčovou otázkou zůstává běh subjektivní promlčecí lhůty. Judikatura Nejvyššího soudu váže její počátek na vědomost o rozhodných okolnostech, což zde svědčí spíše klientovi.",
          strongerSide: "FOR",
          citations: CITATIONS,
          verifiedCount: 3,
          unverifiedCount: 1,
          attempts: 2,
          model: "openrouter/deepseek-chat-v3:free",
          mock: true,
        }
      : null,
    outcome: phase >= 5 ? OUTCOME : null,
    graph: {
      mermaid: MOCK_MERMAID,
      nodes: NODE_ORDER.map((_, i) => nodeAt(i, phase)),
    },
    trace: phase >= 2 ? TRACE.slice(0, Math.min(TRACE.length, phase + 2)) : [],
  };
}

export const MOCK_CORPUS_STATS: CorpusStats = {
  decisions: 1284,
  chunks: 18342,
  courts: [
    "Okresní soud v Klatovech",
    "Okresní soud Brno-venkov",
    "Okresní soud v Havlíčkově Brodě",
    "Okresní soud v Novém Jičíně",
  ],
  from: "2024-03-01",
  to: "2024-03-05",
};

const INGEST_STEP_NAMES = ["Stažení seznamu", "Stažení textů", "Chunking", "Embedding"];

export function mockIngest(id: string, requested: number, phase: number): Ingest {
  const done = phase >= INGEST_STEP_NAMES.length;
  const quarter = Math.ceil(requested / 4);
  const fetched = Math.min(requested, phase * quarter);
  const embedded = Math.max(0, Math.min(fetched, (phase - 1) * quarter));
  return {
    id,
    status: done ? "COMPLETED" : "RUNNING",
    requested,
    fetched,
    embedded,
    failed: done ? 2 : 0,
    steps: INGEST_STEP_NAMES.map((name, i) => ({
      name,
      status: (phase > i ? "DONE" : phase === i ? "RUNNING" : "PENDING") as StepStatus,
      durationMs: phase > i ? 1200 + i * 800 : null,
      attempts: phase >= i ? 1 : null,
      detail: phase > i ? "ok" : null,
    })),
    trace: [
      {
        layer: "Temporal",
        name: "IngestWorkflow",
        status: done ? "DONE" : "RUNNING",
        durationMs: done ? 24100 : null,
        detail: "workflowId=" + id,
      },
      {
        layer: "PostgreSQL/pgvector",
        name: "upsert chunks",
        status: done ? "DONE" : "RUNNING",
        durationMs: done ? 3120 : null,
        detail: embedded + " chunků",
      },
      {
        layer: "Ollama",
        name: "bge-m3 embed",
        status: done ? "DONE" : "RUNNING",
        durationMs: done ? 18200 : null,
        detail: "batch 32",
      },
    ],
  };
}

export const MOCK_GRAPH_DIAGRAM = MOCK_MERMAID;

export const MOCK_DB_INSPECT: DbInspect = {
  embeddingDims: 1024,
  tables: [
    {
      name: "decision",
      rows: 1284,
      totalSize: "9.8 MB",
      indexes: [{ name: "decision_pkey", kind: "btree", size: "88 kB" }],
    },
    {
      name: "chunk",
      rows: 18342,
      totalSize: "312 MB",
      indexes: [
        { name: "chunk_embedding_idx", kind: "hnsw", size: "140 MB" },
        { name: "chunk_tsv_idx", kind: "gin", size: "22 MB" },
        { name: "chunk_decision_idx", kind: "btree", size: "560 kB" },
        { name: "chunk_pkey", kind: "btree", size: "420 kB" },
      ],
    },
  ],
  sections: [
    { section: "justification", chunks: 17058 },
    { section: "verdict", chunks: 1284 },
  ],
  chunkRows: [
    {
      id: "1",
      decisionId: "mock-1",
      caseNumber: "12 C 345/2023",
      seq: 0,
      section: "verdict",
      content:
        "I. Žalovaný je povinen zaplatit žalobci částku ve výši [částka] s úrokem z prodlení ve výši 8,25 % ročně…",
      embeddingPrefix: [-0.012, 0.0299, -0.0054, -0.0041, -0.0176, -0.0071, 0.0162, -0.0087],
      embeddingNorm: 1.0,
      tsvPrefix: "'castka':9,15 'povinen':4 'prodleni':13 'urokem':11 'zalobci':7 'zalovany':2 'zaplatit':6",
    },
    {
      id: "2",
      decisionId: "mock-2",
      caseNumber: "8 C 112/2023",
      seq: 1,
      section: "justification",
      content:
        "1. Žalobce podal dne [datum] u Okresního soudu v Klatovech žalobu o zaplacení částky [částka] s příslušenstvím…",
      embeddingPrefix: [0.0034, 0.0296, 0.0054, -0.004, -0.0313, -0.0132, 0.0153, 0.0021],
      embeddingNorm: 1.0,
      tsvPrefix: "'castka':14 'datum':5 'klatovech':10 'okresniho':8 'podal':3 'soudu':9 'zalobce':2 'zalobu':11",
    },
  ],
};
