package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.Citation;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.GraphNode;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.OutcomeBucket;
import cz.demo.caselaw.domain.OutcomeCategory;
import cz.demo.caselaw.domain.OutcomeStats;
import cz.demo.caselaw.domain.Side;
import cz.demo.caselaw.domain.StepStatus;
import cz.demo.caselaw.search.CitationVerifier;
import cz.demo.caselaw.search.OutcomeStatsCalculator;
import cz.demo.caselaw.store.HybridSearch;
import cz.demo.caselaw.store.SearchResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the whole graph in mock mode against in-test fakes: no database, no LiteLLM, no network.
 * This is the regression net for the graph wiring (node order, parallel branches, events).
 */
class ResearchGraphMockTest {

    private static final UUID ID_A = UUID.randomUUID();
    private static final UUID ID_B = UUID.randomUUID();
    private static final UUID ID_C = UUID.randomUUID();
    private static final UUID ID_D = UUID.randomUUID();

    @Test
    void runsAllNodesAndProducesVerifiedCitations() {
        List<GraphEvent> events = new ArrayList<>();
        ResearchOutcome outcome = runner(new SubstringVerifier())
                .run("r-1", "Má klient nárok na náhradu škody podle § 2913 obč. zák.?", events::add);

        List<String> doneNodes = outcome.nodes().stream().map(GraphNode::name).toList();
        assertThat(doneNodes).containsExactlyInAnyOrder(
                "analyzeQuestion", "retrieve", "argueFor", "argueAgainst", "outcomeStats", "judge", "verifyCitations");
        // analyze runs first, judge only after all three parallel branches, verify last
        assertThat(doneNodes.get(0)).isEqualTo("analyzeQuestion");
        assertThat(doneNodes.get(1)).isEqualTo("retrieve");
        assertThat(doneNodes.get(doneNodes.size() - 1)).isEqualTo("verifyCitations");
        assertThat(doneNodes.indexOf("judge")).isGreaterThan(doneNodes.indexOf("outcomeStats"));

        // every node reported RUNNING before its DONE
        for (String node : doneNodes) {
            assertThat(events.stream().anyMatch(e -> e.node().name().equals(node)
                    && e.node().status() == StepStatus.RUNNING)).as("RUNNING for %s", node).isTrue();
            assertThat(events.stream().anyMatch(e -> e.node().name().equals(node)
                    && e.node().status() == StepStatus.DONE)).as("DONE for %s", node).isTrue();
        }
        assertThat(events.stream().anyMatch(e -> e.analysis() != null)).isTrue();
        assertThat(events.stream().anyMatch(e -> e.hits() != null)).isTrue();
        assertThat(events.stream().anyMatch(e -> e.outcome() != null)).isTrue();
        assertThat(events.stream().anyMatch(e -> e.answer() != null)).isTrue();

        assertThat(outcome.answer().mock()).isTrue();
        assertThat(outcome.answer().model()).isEqualTo("mock");
        assertThat(outcome.answer().forArguments()).isNotEmpty();
        assertThat(outcome.answer().againstArguments()).isNotEmpty();
        assertThat(outcome.answer().citations()).allMatch(Citation::verified);
        assertThat(outcome.answer().unverifiedCount()).isZero();
        assertThat(outcome.answer().attempts()).isEqualTo(1);
        // citations are globally numbered, FOR first
        assertThat(outcome.answer().citations().stream().map(Citation::index).toList())
                .isEqualTo(indexes(outcome.answer().citations().size()));
        assertThat(outcome.answer().citations().get(0).side()).isEqualTo(Side.FOR);
        assertThat(outcome.hits()).hasSize(4);
        assertThat(outcome.outcome().sampleSize()).isEqualTo(4);
        assertThat(outcome.trace()).extracting("layer")
                .contains("LangGraph4j", "LiteLLM → Ollama", "PostgreSQL/pgvector", "CitationVerifier");
    }

    @Test
    void retriesThroughReargueWhenCitationsFail() {
        List<GraphEvent> events = new ArrayList<>();
        ResearchOutcome outcome = runner(new FailFirstAttemptVerifier()).run("r-2", "Nárok na náhradu škody", events::add);

        List<String> doneNodes = outcome.nodes().stream().map(GraphNode::name).toList();
        assertThat(doneNodes).contains("reargue");
        // judge and verifyCitations ran twice, outcomeStats only once
        assertThat(doneNodes.stream().filter("judge"::equals)).hasSize(2);
        assertThat(doneNodes.stream().filter("verifyCitations"::equals)).hasSize(2);
        assertThat(doneNodes.stream().filter("outcomeStats"::equals)).hasSize(1);
        assertThat(outcome.answer().attempts()).isEqualTo(2);
        assertThat(outcome.answer().citations()).allMatch(Citation::verified);
    }

    @Test
    void mermaidContainsEveryNodeId() {
        String mermaid = runner(new SubstringVerifier()).mermaid();
        assertThat(mermaid).contains("flowchart TD");
        for (String node : LangGraphResearchRunner.NODE_NAMES) {
            assertThat(mermaid).as("node %s in diagram", node).contains(node);
            assertThat(mermaid).as("classDef for %s", node).contains("classDef " + node);
        }
    }

    // ------------------------------------------------------------------ fixtures

    private static List<Integer> indexes(int size) {
        List<Integer> expected = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            expected.add(i);
        }
        return expected;
    }

    private LangGraphResearchRunner runner(CitationVerifier verifier) {
        LlmProperties props = new LlmProperties("http://localhost:4000/v1", "k", "chat-model", "embed-model",
                1024, true, false);
        return new LangGraphResearchRunner(new FakeSearch(), verifier, new FakeStats(), new FakeEmbeddings(),
                null, new MockResearch(), props, new LlmCallListener(), 8, 30);
    }

    private static Decision decision(UUID id, String caseNumber, String result) {
        return new Decision(id, "ECLI:CZ:NS:2020:" + caseNumber, caseNumber, "Nejvyšší soud", "NS",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), "Náhrada škody",
                List.of("náhrada škody"), List.of("§ 2913"), List.of(result),
                "Dovolání se " + result + ".",
                "Odůvodnění: Soud dospěl k závěru, že porušení smluvní povinnosti zakládá povinnost k náhradě škody, "
                        + "pokud je dána příčinná souvislost mezi porušením a vznikem škody.",
                "https://rozhodnuti.justice.cz/" + caseNumber);
    }

    private static Hit hit(int rank, Decision d) {
        return new Hit(rank, d, "porušení smluvní povinnosti zakládá povinnost k náhradě škody, pokud je dána příčinná souvislost",
                0.9 - rank * 0.1, 0.8 - rank * 0.1, 1.0 / (60 + rank));
    }

    private static final class FakeSearch implements HybridSearch {
        @Override
        public SearchResult search(List<String> queries, List<float[]> queryEmbeddings, List<String> provisions,
                                   int topK, int similarK) {
            List<Decision> all = List.of(
                    decision(ID_A, "25 Cdo 1/2020", "vyhověno"),
                    decision(ID_B, "25 Cdo 2/2020", "vyhověno"),
                    decision(ID_C, "25 Cdo 3/2020", "zamítnuto"),
                    decision(ID_D, "25 Cdo 4/2020", "zamítnuto"));
            List<Hit> hits = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                hits.add(hit(i + 1, all.get(i)));
            }
            return new SearchResult(hits, all);
        }
    }

    private static final class FakeStats implements OutcomeStatsCalculator {
        @Override
        public OutcomeStats compute(List<Decision> similarDecisions) {
            int n = similarDecisions.size();
            return new OutcomeStats(n,
                    List.of(new OutcomeBucket(OutcomeCategory.GRANTED, n / 2, 0.5),
                            new OutcomeBucket(OutcomeCategory.DISMISSED, n - n / 2, 0.5)),
                    List.of(), List.of(), "test");
        }
    }

    private static final class FakeEmbeddings implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            List<Embedding> result = new ArrayList<>();
            for (TextSegment segment : segments) {
                float[] vector = new float[4];
                vector[0] = segment.text().length();
                result.add(Embedding.from(vector));
            }
            return Response.from(result);
        }
    }

    /** Mirrors the real deterministic rule: quote must appear in the cited decision's full text. */
    private static final class SubstringVerifier implements CitationVerifier {
        @Override
        public List<Citation> verify(List<Citation> citations, List<Hit> hits) {
            List<Citation> result = new ArrayList<>();
            for (Citation c : citations) {
                boolean ok = hits.stream().anyMatch(h -> h.decision().id().equals(c.decisionId())
                        && h.decision().fullText().contains(c.quote()));
                result.add(c.withVerification(ok, ok ? null : "úryvek nenalezen"));
            }
            return result;
        }
    }

    /** Fails the first FOR citation on the first pass to exercise the reargue loop. */
    private static final class FailFirstAttemptVerifier implements CitationVerifier {
        private int calls;

        @Override
        public List<Citation> verify(List<Citation> citations, List<Hit> hits) {
            calls++;
            List<Citation> result = new ArrayList<>();
            for (Citation c : citations) {
                boolean ok = calls > 1 || c.side() != Side.FOR || c.index() != 1;
                result.add(c.withVerification(ok, ok ? null : "úryvek nenalezen"));
            }
            return result;
        }
    }
}
