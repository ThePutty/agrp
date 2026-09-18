package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.Answer;
import cz.demo.caselaw.domain.Argument;
import cz.demo.caselaw.domain.Citation;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.GraphNode;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.OutcomeStats;
import cz.demo.caselaw.domain.QueryAnalysis;
import cz.demo.caselaw.domain.Side;
import cz.demo.caselaw.domain.StepStatus;
import cz.demo.caselaw.domain.TraceEntry;
import cz.demo.caselaw.search.CitationVerifier;
import cz.demo.caselaw.search.OutcomeStatsCalculator;
import cz.demo.caselaw.store.HybridSearch;
import cz.demo.caselaw.store.SearchResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * The research agent as a LangGraph4j StateGraph.
 *
 * Why a graph and not plain Java calls: the three "Advocatus Diaboli" branches really do run in
 * parallel (native fan-out from `retrieve`), and the citation check feeds back into the arguing
 * nodes. That control flow - parallel branches plus a conditional retry loop - is what the graph
 * expresses declaratively, and it is also what the UI renders live from the same Mermaid diagram.
 *
 * What is deliberately NOT in the LLM: retrieval ranking, outcome statistics and citation
 * verification are deterministic Java/SQL. The model may only argue over material it was given.
 */
@Service
public class LangGraphResearchRunner implements ResearchGraphRunner {

    private static final Logger log = LoggerFactory.getLogger(LangGraphResearchRunner.class);

    static final String ANALYZE = "analyzeQuestion";
    static final String RETRIEVE = "retrieve";
    static final String ARGUE_FOR = "argueFor";
    static final String ARGUE_AGAINST = "argueAgainst";
    static final String OUTCOME_STATS = "outcomeStats";
    static final String JUDGE = "judge";
    static final String VERIFY = "verifyCitations";
    static final String REARGUE = "reargue";

    /** Nodes in diagram order - also the ids the UI colours via classDef. */
    public static final List<String> NODE_NAMES =
            List.of(ANALYZE, RETRIEVE, ARGUE_FOR, ARGUE_AGAINST, OUTCOME_STATS, JUDGE, VERIFY, REARGUE);

    private static final int MAX_ATTEMPTS = 2;

    private final HybridSearch hybridSearch;
    private final CitationVerifier citationVerifier;
    private final OutcomeStatsCalculator outcomeStatsCalculator;
    private final EmbeddingModel embeddingModel;
    private final LlmServices llm;
    private final MockResearch mock;
    private final LlmProperties llmProps;
    private final LlmCallListener callListener;
    private final int topK;
    private final int similarK;

    private final ExecutorService nodeExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "research-node");
                t.setDaemon(true);
                return t;
            });

    private final ExecutorService reargueExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "reargue");
                t.setDaemon(true);
                return t;
            });

    private volatile String mermaidCache;

    public LangGraphResearchRunner(HybridSearch hybridSearch,
                                   CitationVerifier citationVerifier,
                                   OutcomeStatsCalculator outcomeStatsCalculator,
                                   EmbeddingModel embeddingModel,
                                   LlmServices llm,
                                   MockResearch mock,
                                   LlmProperties llmProps,
                                   LlmCallListener callListener,
                                   @Value("${app.search.top-k:8}") int topK,
                                   @Value("${app.search.similar-k:30}") int similarK) {
        this.hybridSearch = hybridSearch;
        this.citationVerifier = citationVerifier;
        this.outcomeStatsCalculator = outcomeStatsCalculator;
        this.embeddingModel = embeddingModel;
        this.llm = llm;
        this.mock = mock;
        this.llmProps = llmProps;
        this.callListener = callListener;
        this.topK = topK;
        this.similarK = similarK;
    }

    // ---------------------------------------------------------------- public API

    @Override
    public ResearchOutcome run(String researchId, String question, Consumer<GraphEvent> listener) {
        Run run = new Run(researchId, question, listener, llmProps.mock());
        try {
            CompiledGraph<ResearchState> graph = buildGraph(run).compile();
            ResearchState last = null;
            for (NodeOutput<ResearchState> output : graph.stream(Map.of(
                    ResearchState.QUESTION, question,
                    ResearchState.ATTEMPTS, 1))) {
                if (output.state() != null) {
                    last = output.state();
                }
            }
            ResearchOutcome outcome = toOutcome(run, last);
            return outcome;
        } catch (GraphStateException e) {
            throw new IllegalStateException("Nepodařilo se sestavit graf", e);
        }
    }

    @Override
    public String mermaid() {
        String cached = mermaidCache;
        if (cached != null) {
            return cached;
        }
        try {
            // The diagram is built from the same definition the run uses, so node ids in the
            // Mermaid source are exactly the node names the UI gets in GraphEvents.
            Run dummy = new Run("diagram", "", null, true);
            String content = buildGraph(dummy)
                    .getGraph(GraphRepresentation.Type.MERMAID, "Research graph", true)
                    .content();
            cached = withNodeClasses(content);
            mermaidCache = cached;
            return cached;
        } catch (GraphStateException e) {
            throw new IllegalStateException("Nepodařilo se vykreslit graf", e);
        }
    }

    /**
     * Mermaid needs every referenced class to exist. The generator emits `:::nodeName` on edges but
     * declares no classDef for them, so we add a neutral default; the UI overrides these lines to
     * colour nodes by live status.
     */
    static String withNodeClasses(String mermaid) {
        StringBuilder sb = new StringBuilder(mermaid.stripTrailing()).append('\n');
        for (String node : NODE_NAMES) {
            sb.append("\tclassDef ").append(node).append(" fill:#eef2ff,stroke:#94a3b8,stroke-width:1px;\n");
        }
        // The conditional edge renders as conditionN diamonds, which reference a class too.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(":::(condition\\d+)").matcher(mermaid);
        java.util.Set<String> conditions = new LinkedHashSet<>();
        while (m.find()) {
            conditions.add(m.group(1));
        }
        for (String condition : conditions) {
            sb.append("\tclassDef ").append(condition).append(" fill:#fff7ed,stroke:#fdba74,stroke-width:1px;\n");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- graph definition

    private StateGraph<ResearchState> buildGraph(Run run) throws GraphStateException {
        return new StateGraph<>(ResearchState.SCHEMA, new InMemoryStateSerializer())
                .addNode(ANALYZE, node(run, ANALYZE, s -> analyze(run, s)))
                .addNode(RETRIEVE, node(run, RETRIEVE, s -> retrieve(run, s)))
                .addNode(ARGUE_FOR, node(run, ARGUE_FOR, s -> argue(run, s, Side.FOR)))
                .addNode(ARGUE_AGAINST, node(run, ARGUE_AGAINST, s -> argue(run, s, Side.AGAINST)))
                .addNode(OUTCOME_STATS, node(run, OUTCOME_STATS, s -> outcomeStats(run, s)))
                .addNode(JUDGE, node(run, JUDGE, s -> judge(run, s)))
                .addNode(VERIFY, node(run, VERIFY, s -> verify(run, s)))
                .addNode(REARGUE, node(run, REARGUE, s -> reargue(run, s)))
                .addEdge(START, ANALYZE)
                .addEdge(ANALYZE, RETRIEVE)
                // Native fan-out: three addEdge calls from the same source compile into a
                // LangGraph4j ParallelNode; all three branches must fan back into one node.
                .addEdge(RETRIEVE, ARGUE_FOR)
                .addEdge(RETRIEVE, ARGUE_AGAINST)
                .addEdge(RETRIEVE, OUTCOME_STATS)
                .addEdge(ARGUE_FOR, JUDGE)
                .addEdge(ARGUE_AGAINST, JUDGE)
                .addEdge(OUTCOME_STATS, JUDGE)
                .addEdge(JUDGE, VERIFY)
                // A conditional edge picks exactly one target, so the retry goes to a single node
                // that re-runs the failing side(s) itself. Statistics are never recomputed.
                .addConditionalEdges(VERIFY,
                        edge_async(s -> allVerified(s) || s.attempts() >= MAX_ATTEMPTS ? "end" : "retry"),
                        Map.of("end", END, "retry", REARGUE))
                .addEdge(REARGUE, JUDGE);
    }

    private static boolean allVerified(ResearchState state) {
        return state.verification().stream().allMatch(Citation::verified);
    }

    // ---------------------------------------------------------------- nodes

    private NodeResult analyze(Run run, ResearchState state) {
        String question = state.question();
        QueryAnalysis analysis;
        if (run.mock) {
            analysis = toDomain(mock.analyze(question));
        } else {
            try (LlmCallScope ignored = LlmCallScope.open(ANALYZE, run.trace::add)) {
                analysis = toDomain(llm.analyze(question));
            } catch (RuntimeException e) {
                // A failure on the very first LLM call means LiteLLM is down or the key is wrong:
                // switch the whole run to mock so the demo still produces a full answer.
                run.mock = true;
                run.trace.add(new TraceEntry("LangChain4j", ANALYZE, StepStatus.FAILED, null,
                        "přepnuto do mock režimu: " + e));
                log.warn("LLM unavailable, switching to mock mode: {}", e.toString());
                analysis = toDomain(mock.analyze(question));
            }
        }
        String detail = "%d pojmů, %d ustanovení, %d dotazů"
                .formatted(analysis.legalConcepts().size(), analysis.provisions().size(), analysis.searchQueries().size());
        return new NodeResult(Map.of(ResearchState.ANALYSIS, analysis), detail, analysis, null, null, null);
    }

    private NodeResult retrieve(Run run, ResearchState state) {
        QueryAnalysis analysis = state.analysis();
        List<String> queries = new ArrayList<>(new LinkedHashSet<>(prepend(state.question(),
                analysis == null ? List.of() : analysis.searchQueries())));

        // Embeddings are the semantic half of the hybrid search. If the embedding service is down
        // (Ollama/LiteLLM restart, network blip) we degrade to fulltext-only instead of failing the
        // whole research; the Technical Trace shows the degradation.
        long t0 = System.nanoTime();
        List<float[]> vectors = List.of();
        try {
            List<TextSegment> segments = queries.stream().map(TextSegment::from).toList();
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            vectors = embeddings.stream().map(Embedding::vector).toList();
            run.trace.add(new TraceEntry("LiteLLM → Ollama", "embed-model", StepStatus.DONE, msSince(t0),
                    queries.size() + " textů, " + llmProps.embeddingDimension() + " dimenzí"));
        } catch (RuntimeException e) {
            log.warn("Embedding selhal, hledám jen fulltextem: {}", e.toString());
            run.trace.add(new TraceEntry("LiteLLM → Ollama", "embed-model", StepStatus.FAILED, msSince(t0),
                    "embeddingy nedostupné, pouze fulltext: " + Prompts.cut(e.toString(), 160)));
        }

        long t1 = System.nanoTime();
        SearchResult result = hybridSearch.search(queries,
                vectors,
                analysis == null ? List.of() : analysis.provisions(),
                topK, similarK);
        int searchMs = msSince(t1);
        List<Hit> hits = result.hits() == null ? List.of() : result.hits();
        List<Decision> similar = result.similar() == null ? List.of() : result.similar();
        run.trace.add(new TraceEntry("PostgreSQL/pgvector", "hybridní hledání (RRF)", StepStatus.DONE, searchMs,
                hits.size() + " hitů, " + similar.size() + " podobných"));

        String detail = hits.size() + " hitů, " + similar.size() + " podobných"
                + (vectors.isEmpty() ? " (jen fulltext, embeddingy nedostupné)" : "");
        return new NodeResult(Map.of(ResearchState.HITS, hits, ResearchState.SIMILAR, similar),
                detail, null, hits, null, null);
    }

    private NodeResult argue(Run run, ResearchState state, Side side) {
        Sided sided = generateArguments(run, state, side, feedbackFor(state, side), nodeName(side));
        Map<String, Object> updates = side == Side.FOR
                ? Map.of(ResearchState.FOR_ARGS, sided.arguments(), ResearchState.FOR_CITATIONS, sided.citations())
                : Map.of(ResearchState.AGAINST_ARGS, sided.arguments(), ResearchState.AGAINST_CITATIONS, sided.citations());
        return NodeResult.of(updates, sided.detail());
    }

    private NodeResult outcomeStats(Run run, ResearchState state) {
        // Deterministic on purpose: the numbers the judge comments on must be reproducible and
        // auditable, so they are computed from the retrieved corpus, never by the model.
        OutcomeStats stats = outcomeStatsCalculator.compute(state.similar());
        int sample = stats == null ? 0 : stats.sampleSize();
        run.trace.add(new TraceEntry("CitationVerifier", "OutcomeStatsCalculator", StepStatus.DONE, null,
                "vzorek " + sample + " rozhodnutí"));
        return new NodeResult(Map.of(ResearchState.OUTCOME, stats == null ? emptyStats() : stats),
                "vzorek " + sample + " rozhodnutí", null, null, stats, null);
    }

    private NodeResult judge(Run run, ResearchState state) {
        // Both sides numbered their citations from 1; merge them into one global numbering first.
        Citations.Merged merged = Citations.merge(state.forArgs(), state.forCitations(),
                state.againstArgs(), state.againstCitations());

        String verdict;
        Side stronger;
        if (run.mock) {
            Dtos.JudgmentDto dto = mock.judge(merged.forArguments().size(), merged.againstArguments().size(), state.outcome());
            verdict = dto.verdict();
            stronger = parseSide(dto.strongerSide());
        } else {
            try (LlmCallScope ignored = LlmCallScope.open(JUDGE, run.trace::add)) {
                Dtos.JudgmentDto dto = llm.judge(state.question(),
                        Prompts.arguments("Argumenty PRO klienta", merged.forArguments()),
                        Prompts.arguments("Argumenty PROTI klientovi", merged.againstArguments()),
                        state.outcome());
                verdict = dto.verdict() == null || dto.verdict().isBlank()
                        ? "Model nevrátil validní odpověď" : dto.verdict();
                stronger = parseSide(dto.strongerSide());
            } catch (RuntimeException e) {
                log.warn("Judge call failed: {}", e.toString());
                verdict = "Model nevrátil validní odpověď";
                stronger = Side.BALANCED;
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(ResearchState.FOR_ARGS, merged.forArguments());
        updates.put(ResearchState.AGAINST_ARGS, merged.againstArguments());
        updates.put(ResearchState.VERIFICATION, merged.citations());
        updates.put(ResearchState.VERDICT, verdict);
        updates.put(ResearchState.STRONGER_SIDE, stronger);

        String detail = "verdikt %d znaků, silnější strana %s".formatted(verdict.length(), stronger);
        Answer answer = answer(run, merged.forArguments(), merged.againstArguments(),
                merged.citations(), verdict, stronger, state.attempts());
        return new NodeResult(updates, detail, null, null, null, answer);
    }

    private NodeResult verify(Run run, ResearchState state) {
        long t0 = System.nanoTime();
        List<Citation> checked = citationVerifier.verify(state.verification(), state.hits());
        int ms = msSince(t0);
        checked = checked == null ? List.of() : checked;

        long unverified = checked.stream().filter(c -> !c.verified()).count();
        String detail = unverified == 0
                ? "%d z %d citací ověřeno".formatted(checked.size(), checked.size())
                : "%d z %d citací neověřeno".formatted(unverified, checked.size());
        run.trace.add(new TraceEntry("CitationVerifier", "ověření citací", StepStatus.DONE, ms,
                (checked.size() - unverified) + " ověřeno, " + unverified + " neověřeno"));

        Map<String, Object> updates = new HashMap<>();
        updates.put(ResearchState.VERIFICATION, checked);
        updates.put(ResearchState.FEEDBACK_FOR, feedbackText(checked, Side.FOR));
        updates.put(ResearchState.FEEDBACK_AGAINST, feedbackText(checked, Side.AGAINST));

        Answer answer = answer(run, state.forArgs(), state.againstArgs(), checked,
                state.verdict(), state.strongerSide(), state.attempts());
        return new NodeResult(updates, detail, null, null, null, answer);
    }

    /**
     * Retry path. A conditional edge can only pick one target, so the node itself re-runs the side
     * (or both sides) whose citations failed - in parallel, and only that side gets the feedback.
     * outcomeStats is never repeated: its input did not change.
     */
    private NodeResult reargue(Run run, ResearchState state) {
        String forFeedback = state.feedbackFor();
        String againstFeedback = state.feedbackAgainst();
        boolean redoFor = forFeedback != null && !forFeedback.isBlank();
        boolean redoAgainst = againstFeedback != null && !againstFeedback.isBlank();

        CompletableFuture<Sided> forTask = redoFor
                ? CompletableFuture.supplyAsync(
                        () -> generateArguments(run, state, Side.FOR, forFeedback, REARGUE), reargueExecutor)
                : CompletableFuture.completedFuture(null);
        CompletableFuture<Sided> againstTask = redoAgainst
                ? CompletableFuture.supplyAsync(
                        () -> generateArguments(run, state, Side.AGAINST, againstFeedback, REARGUE), reargueExecutor)
                : CompletableFuture.completedFuture(null);

        Sided forResult = forTask.join();
        Sided againstResult = againstTask.join();

        Map<String, Object> updates = new HashMap<>();
        updates.put(ResearchState.ATTEMPTS, state.attempts() + 1);
        updates.put(ResearchState.FEEDBACK_FOR, "");
        updates.put(ResearchState.FEEDBACK_AGAINST, "");
        if (forResult != null) {
            updates.put(ResearchState.FOR_ARGS, forResult.arguments());
            updates.put(ResearchState.FOR_CITATIONS, forResult.citations());
        } else {
            updates.put(ResearchState.FOR_CITATIONS, sideCitations(state, Side.FOR));
        }
        if (againstResult != null) {
            updates.put(ResearchState.AGAINST_ARGS, againstResult.arguments());
            updates.put(ResearchState.AGAINST_CITATIONS, againstResult.citations());
        } else {
            updates.put(ResearchState.AGAINST_CITATIONS, sideCitations(state, Side.AGAINST));
        }

        List<String> redone = new ArrayList<>();
        if (redoFor) {
            redone.add("PRO");
        }
        if (redoAgainst) {
            redone.add("PROTI");
        }
        return NodeResult.of(updates, "znovu argumentuje: " + (redone.isEmpty() ? "-" : String.join(", ", redone)));
    }

    // ---------------------------------------------------------------- helpers

    /** One side's arguments + citations, already mapped to domain records. */
    private record Sided(List<Argument> arguments, List<Citation> citations, String detail) {
    }

    private Sided generateArguments(Run run, ResearchState state, Side side, String feedback, String callName) {
        List<Hit> hits = state.hits();
        Dtos.ArgumentsDto dto;
        if (run.mock) {
            dto = mock.argue(side, hits);
        } else {
            try (LlmCallScope ignored = LlmCallScope.open(callName + ":" + side, run.trace::add)) {
                dto = llm.argue(side, state.question(), hits, feedback);
            } catch (RuntimeException e) {
                log.warn("Argument generation failed for {}: {}", side, e.toString());
                return new Sided(List.of(), List.of(), "model nevrátil validní odpověď");
            }
        }
        List<Argument> arguments = Citations.argumentsFromDto(dto.arguments());
        List<Citation> citations = Citations.fromDto(dto.citations(), side, hits);
        return new Sided(arguments, citations, "%d argumentů, %d citací".formatted(arguments.size(), citations.size()));
    }

    /**
     * After the merge in `judge` the global citation list is the single source of truth; when a
     * side is not re-argued we simply carry its (already merged) citations over.
     */
    private static List<Citation> sideCitations(ResearchState state, Side side) {
        return state.verification().stream().filter(c -> c.side() == side).toList();
    }

    private static String feedbackFor(ResearchState state, Side side) {
        return side == Side.FOR ? state.feedbackFor() : state.feedbackAgainst();
    }

    private static String nodeName(Side side) {
        return side == Side.FOR ? ARGUE_FOR : ARGUE_AGAINST;
    }

    /** Feedback goes only to the side that produced the bad citation. */
    private static String feedbackText(List<Citation> citations, Side side) {
        List<Integer> bad = citations.stream()
                .filter(c -> c.side() == side && !c.verified())
                .map(Citation::index)
                .toList();
        if (bad.isEmpty()) {
            return "";
        }
        String list = bad.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return Prompts.CITATION_FEEDBACK.formatted(list, list);
    }

    private Answer answer(Run run, List<Argument> forArgs, List<Argument> againstArgs,
                          List<Citation> citations, String verdict, Side stronger, int attempts) {
        int verified = (int) citations.stream().filter(Citation::verified).count();
        return new Answer(forArgs, againstArgs, verdict, stronger, citations,
                verified, citations.size() - verified, attempts,
                run.mock ? "mock" : callListener.lastModelName().orElse(llmProps.chatModel()),
                run.mock);
    }

    private ResearchOutcome toOutcome(Run run, ResearchState state) {
        List<Hit> hits = state == null ? List.of() : state.hits();
        QueryAnalysis analysis = state == null ? null : state.analysis();
        OutcomeStats outcome = state == null ? emptyStats() : state.outcome();
        List<Citation> citations = state == null ? List.of() : state.verification();
        Answer answer = state == null
                ? answer(run, List.of(), List.of(), List.of(), "Graf neproběhl", Side.BALANCED, 1)
                : answer(run, state.forArgs(), state.againstArgs(), citations,
                        state.verdict() == null ? "Model nevrátil validní odpověď" : state.verdict(),
                        state.strongerSide(), state.attempts());

        List<TraceEntry> trace = new ArrayList<>(run.trace);
        if (!run.mock) {
            trace.add(new TraceEntry("LiteLLM → OpenRouter", llmProps.chatModel(), StepStatus.DONE, null,
                    "alias → " + callListener.lastModelName().orElse("neznámý model")));
        } else {
            trace.add(new TraceEntry("LiteLLM → OpenRouter", llmProps.chatModel(), StepStatus.SKIPPED, null,
                    "mock režim, model nevolán"));
        }

        List<GraphNode> nodes = state == null ? List.of() : state.nodeTrace();
        return new ResearchOutcome(analysis, hits, outcome == null ? emptyStats() : outcome, answer, nodes, trace);
    }

    private static OutcomeStats emptyStats() {
        return new OutcomeStats(0, List.of(), List.of(), List.of(), "Bez dat");
    }

    private static QueryAnalysis toDomain(Dtos.QueryAnalysisDto dto) {
        return new QueryAnalysis(safe(dto == null ? null : dto.legalConcepts()),
                safe(dto == null ? null : dto.provisions()),
                safe(dto == null ? null : dto.searchQueries()));
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).toList();
    }

    private static List<String> prepend(String first, List<String> rest) {
        List<String> all = new ArrayList<>();
        if (first != null && !first.isBlank()) {
            all.add(first);
        }
        if (rest != null) {
            rest.stream().filter(v -> v != null && !v.isBlank()).forEach(all::add);
        }
        return all;
    }

    private static Side parseSide(String value) {
        if (value == null) {
            return Side.BALANCED;
        }
        try {
            return Side.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Side.BALANCED;
        }
    }

    private static int msSince(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000);
    }

    // ---------------------------------------------------------------- node instrumentation

    /** What a node body returns: state updates, a human detail, and the partial data for the UI. */
    private record NodeResult(Map<String, Object> updates, String detail, QueryAnalysis analysis,
                              List<Hit> hits, OutcomeStats outcome, Answer answer) {
        static NodeResult of(Map<String, Object> updates, String detail) {
            return new NodeResult(updates, detail, null, null, null, null);
        }
    }

    /**
     * Wraps a node body with everything the demo needs around it: RUNNING/DONE GraphEvents, the
     * GraphNode written into the appender channel and a trace row.
     */
    private AsyncNodeAction<ResearchState> node(Run run, String name,
                                                Function<ResearchState, NodeResult> body) {
        // node_async would run the body on the caller thread, which serialises LangGraph4j's parallel
        // branches. Running each node on its own thread makes argueFor / argueAgainst / outcomeStats
        // truly concurrent (the ThreadLocal LlmCallScope is opened inside the body, so nesting still works).
        return state -> CompletableFuture.supplyAsync(() -> {
            int attempt = state.attempts();
            String startedAt = Instant.now().toString();
            long t0 = System.nanoTime();
            run.emit(GraphEvent.nodeOnly(new GraphNode(name, StepStatus.RUNNING, startedAt, null, attempt, null)));
            try {
                NodeResult result = body.apply(state);
                GraphNode done = new GraphNode(name, StepStatus.DONE, startedAt, msSince(t0), attempt, result.detail());
                run.trace.add(new TraceEntry("LangGraph4j", name, StepStatus.DONE, done.durationMs(), result.detail()));
                run.emit(new GraphEvent(done, result.analysis(), result.hits(), result.outcome(), result.answer()));

                Map<String, Object> updates = new HashMap<>(result.updates());
                updates.put(ResearchState.NODE_TRACE, done);
                return updates;
            } catch (Exception e) {
                GraphNode failed = new GraphNode(name, StepStatus.FAILED, startedAt, msSince(t0), attempt, String.valueOf(e));
                run.trace.add(new TraceEntry("LangGraph4j", name, StepStatus.FAILED, failed.durationMs(), String.valueOf(e)));
                run.emit(GraphEvent.nodeOnly(failed));
                throw e;
            }
        }, nodeExecutor);
    }

    /** Everything that belongs to one question; the graph is rebuilt per run so nodes can capture it. */
    private final class Run {
        final String researchId;
        final String question;
        final Consumer<GraphEvent> listener;
        final List<TraceEntry> trace = Collections.synchronizedList(new ArrayList<>());
        volatile boolean mock;

        Run(String researchId, String question, Consumer<GraphEvent> listener, boolean mock) {
            this.researchId = researchId;
            this.question = question;
            this.listener = listener;
            this.mock = mock;
        }

        void emit(GraphEvent event) {
            if (listener == null) {
                return;
            }
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                log.debug("Graph event listener failed for {}: {}", researchId, e.toString());
            }
        }

    }
}
