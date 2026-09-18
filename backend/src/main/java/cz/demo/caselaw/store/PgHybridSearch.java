package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.search.Ranker;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hybrid retrieval over the `chunk` table: pgvector kNN (semantic) + PostgreSQL fulltext
 * (exact legal wording), fused with Reciprocal Rank Fusion in {@link Ranker}.
 *
 * <p>Both halves are pure SQL, so the ranking is reproducible and the LLM never decides which
 * decisions are relevant - it only argues about the ones retrieved here.
 */
@Component
public class PgHybridSearch implements HybridSearch {

    /** Candidates taken from each individual SQL query before fusion. */
    private static final int PER_QUERY_LIMIT = 40;
    private static final int SNIPPET_LENGTH = 400;

    private static final String KNN_SQL = """
            SELECT decision_id, content, 1 - (embedding <=> CAST(? AS vector)) AS score
            FROM chunk
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT %d
            """.formatted(PER_QUERY_LIMIT);

    private static final String FULLTEXT_SQL = """
            SELECT decision_id, content, ts_rank_cd(tsv, plainto_tsquery('simple', immutable_unaccent(?))) AS score
            FROM chunk
            WHERE tsv @@ plainto_tsquery('simple', immutable_unaccent(?))
            ORDER BY score DESC
            LIMIT %d
            """.formatted(PER_QUERY_LIMIT);

    private final JdbcClient jdbc;
    private final DecisionRepository decisions;

    public PgHybridSearch(JdbcClient jdbc, DecisionRepository decisions) {
        this.jdbc = jdbc;
        this.decisions = decisions;
    }

    @Override
    public SearchResult search(List<String> queries, List<float[]> queryEmbeddings,
                               List<String> provisions, int topK, int similarK) {
        List<Ranker.RankedList> lists = new ArrayList<>();

        if (queryEmbeddings != null) {
            for (float[] embedding : queryEmbeddings) {
                if (embedding == null || embedding.length == 0) continue;
                String vector = JdbcChunkRepository.toVectorLiteral(embedding);
                lists.add(new Ranker.RankedList(Ranker.Kind.VECTOR,
                        jdbc.sql(KNN_SQL).param(vector).param(vector).query(PgHybridSearch::mapCandidate).list()));
            }
        }
        if (queries != null) {
            for (String query : queries) {
                if (query == null || query.isBlank()) continue;
                lists.add(new Ranker.RankedList(Ranker.Kind.TEXT,
                        jdbc.sql(FULLTEXT_SQL).param(query).param(query).query(PgHybridSearch::mapCandidate).list()));
            }
        }
        if (lists.isEmpty()) return new SearchResult(List.of(), List.of());

        int wide = Math.max(topK, similarK);
        // First pass without the provision bonus, only to learn which decisions to load.
        List<Ranker.Fused> preliminary = Ranker.fuse(lists, List.of(), Map.of(), wide);
        Map<UUID, Decision> loaded = new LinkedHashMap<>();
        decisions.findAllById(preliminary.stream().map(Ranker.Fused::decisionId).toList())
                .forEach(d -> loaded.put(d.id(), d));

        Map<UUID, List<String>> decisionProvisions = new LinkedHashMap<>();
        loaded.forEach((id, d) -> decisionProvisions.put(id, d.provisions()));
        List<Ranker.Fused> ranked = Ranker.fuse(lists, provisions, decisionProvisions, wide);

        List<Hit> hits = new ArrayList<>();
        List<Decision> similar = new ArrayList<>();
        for (Ranker.Fused f : ranked) {
            Decision decision = loaded.get(f.decisionId());
            if (decision == null) continue;                     // chunk without its decision - skip defensively
            if (similar.size() < similarK) similar.add(decision);
            if (hits.size() < topK) {
                hits.add(new Hit(hits.size() + 1, decision, snippet(f.snippet()),
                        f.vectorScore(), f.textScore(), f.fusedScore()));
            }
        }
        return new SearchResult(List.copyOf(hits), List.copyOf(similar));
    }

    private static Ranker.Candidate mapCandidate(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Ranker.Candidate(rs.getObject("decision_id", UUID.class), rs.getString("content"), rs.getDouble("score"));
    }

    private static String snippet(String content) {
        if (content == null) return null;
        String text = content.strip().replaceAll("\\s+", " ");
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH).trim() + "…";
    }
}
