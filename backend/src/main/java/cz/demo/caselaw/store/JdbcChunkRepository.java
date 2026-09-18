package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.Chunk;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Chunks + pgvector embeddings. The vector is bound as its text literal ("[0.1,0.2,...]")
 * and cast with {@code CAST(? AS vector)} - this keeps the code free of a pgvector-specific JDBC type.
 */
@Repository
public class JdbcChunkRepository implements ChunkRepository {

    private final JdbcClient jdbc;

    public JdbcChunkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void replaceChunks(UUID decisionId, List<Chunk> chunks, List<float[]> embeddings) {
        List<Chunk> toInsert = chunks == null ? List.of() : chunks;
        boolean withEmbeddings = embeddings != null && !embeddings.isEmpty();
        if (withEmbeddings && embeddings.size() != toInsert.size()) {
            throw new IllegalArgumentException("Počet embeddingů (%d) neodpovídá počtu chunků (%d)"
                    .formatted(embeddings.size(), toInsert.size()));
        }
        jdbc.sql("DELETE FROM chunk WHERE decision_id = ?").param(decisionId).update();

        for (int i = 0; i < toInsert.size(); i++) {
            Chunk chunk = toInsert.get(i);
            String vector = toVectorLiteral(withEmbeddings ? embeddings.get(i) : null);
            jdbc.sql("""
                            INSERT INTO chunk (decision_id, seq, section, content, embedding)
                            VALUES (?, ?, ?, ?, CAST(? AS vector))
                            """)
                    .param(decisionId)
                    .param(chunk.seq())
                    .param(chunk.section())
                    .param(chunk.content())
                    .param(vector)
                    .update();
        }
    }

    /** float[] -> pgvector literal "[0.1,0.2,...]"; null stays null (the embedding column is nullable). */
    static String toVectorLiteral(float[] embedding) {
        if (embedding == null) return null;
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : embedding) joiner.add(Float.toString(v));
        return joiner.toString();
    }
}
