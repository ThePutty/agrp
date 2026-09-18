package cz.demo.caselaw.store;

import java.util.List;

/**
 * Deterministic hybrid retrieval: pgvector kNN + PostgreSQL fulltext, fused with Reciprocal Rank Fusion.
 * The LLM never decides the ranking.
 */
public interface HybridSearch {
    /**
     * @param queries         search strings (from QueryAnalysis.searchQueries, plus the raw question)
     * @param queryEmbeddings embedding per query (same order/size as queries)
     * @param provisions      paragraph references detected in the question, used as a fusion bonus
     * @param topK            how many hits to return (default 8)
     * @param similarK        how many similar decisions to return for outcome statistics (default 30)
     */
    SearchResult search(List<String> queries, List<float[]> queryEmbeddings, List<String> provisions, int topK, int similarK);
}
